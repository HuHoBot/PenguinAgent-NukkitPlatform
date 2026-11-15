package cn.huohuas001.huhobotPenguin.nukkit.onlinelist

import cn.huohuas001.bot.onlinelist.OnlineListHandler
import cn.huohuas001.bot.onlinelist.OnlineListImageReply
import cn.huohuas001.huhobotPenguin.adapter.config.YamlConfig
import cn.huohuas001.huhobotPenguin.nukkit.HuHoBotNukkit
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * 在线列表渲染与发送管线（Nukkit 侧）。
 *
 * 把 `查在线` 命令的图片逻辑从 MOTD 外部 API 改为本地渲染：
 * 捕获玩家快照（含皮肤像素）→ 渲染 PNG → 通过 [reply] 回调发回 QQ。
 *
 * 在 [cn.huohuas001.bot.onlinelist.OnlineListBridge] 中注册后，
 * `查在线` 命令即可走本地渲染；`online-list.enabled=false` 或渲染器初始化失败时，
 * 命令自动回退到 MOTD 外部 API 图片。
 */
class OnlineListManager(
    private val plugin: HuHoBotNukkit,
    private val config: YamlConfig
) : OnlineListHandler {

    override fun handle(groupOpenId: String, requestedPage: Int, reply: OnlineListImageReply): Boolean =
        handleQuery(groupOpenId, requestedPage, reply)

    private val inFlightGroups = ConcurrentHashMap.newKeySet<String>()
    private val lastRequestAt = ConcurrentHashMap<String, Long>()

    private lateinit var renderExecutor: ExecutorService
    private var avatarCache: AvatarCache? = null
    private var renderer: OnlineListRenderer? = null

    fun start() {
        renderExecutor = Executors.newSingleThreadExecutor(namedThreads("HuHoBotOnlineList-Render"))
        val cache = AvatarCache(
            cacheCapacity = config.onlineListSkinCacheSize(),
            enabled = config.onlineListSkinEnabled()
        )
        avatarCache = cache
        try {
            renderer = OnlineListRenderer(
                avatarCache = cache,
                columns = config.onlineListRenderColumns(),
                configuredFont = config.onlineListRenderFontFamily(),
                footerText = config.onlineListRenderFooterText()
            )
        } catch (error: Throwable) {
            // 底图资源缺失/损坏时不中断插件启动，仅关闭本地渲染能力
            plugin.log_warning("在线列表渲染器初始化失败，查在线将回退到 MOTD 图片: ${error.message}")
            return
        }
        plugin.log_info("在线列表渲染器已就绪（雾蓝玻璃主题，玩家头像直接取自 Bedrock 皮肤）")
    }

    fun stop() {
        if (::renderExecutor.isInitialized) renderExecutor.shutdownNow()
        inFlightGroups.clear()
        lastRequestAt.clear()
    }

    /**
     * 查在线命令的图片回复入口。
     *
     * @return true 表示请求已被平台受理（含冷却/页码拒绝等情况，结果通过 [reply] 回调）；
     *         false 表示本地渲染未启用，调用方应回退到 MOTD 图片
     */
    fun handleQuery(
        groupOpenId: String,
        requestedPage: Int,
        reply: OnlineListImageReply
    ): Boolean {
        if (!config.onlineListEnabled()) return false
        val activeRenderer = renderer ?: return false

        val complete = captureSnapshotAsync() ?: run {
            reply.failed("无法获取在线玩家快照")
            return true
        }
        val totalPlayers = complete.totalOnlinePlayers
        val pageSize = config.onlineListPageSize()
        val pages = OnlineListPages.totalPages(totalPlayers, pageSize)
        if (requestedPage < 1 || requestedPage > pages) {
            reply.outOfRange(requestedPage, pages)
            return true
        }

        val now = System.currentTimeMillis()
        val cooldownMs = config.onlineListCooldownSeconds().coerceAtLeast(0) * 1000L
        val previous = lastRequestAt[groupOpenId]
        if (previous != null && now - previous < cooldownMs) {
            reply.busy()
            return true
        }
        if (!inFlightGroups.add(groupOpenId)) {
            reply.busy()
            return true
        }
        lastRequestAt[groupOpenId] = now

        val snapshot = OnlineListPages.page(complete, requestedPage, pageSize)
        renderExecutor.execute {
            try {
                val png = activeRenderer.render(snapshot)
                if (png.size > config.onlineListImageMaxBytes()) {
                    reply.failed("图片超出大小限制: ${png.size} bytes")
                    return@execute
                }
                reply.image(png)
            } catch (error: Throwable) {
                plugin.log_warning("在线列表渲染失败: ${error.message}")
                reply.failed("在线列表渲染失败: ${error.message}")
            } finally {
                inFlightGroups.remove(groupOpenId)
            }
        }
        return true
    }

    /**
     * 在主线程捕获在线玩家快照；不在主线程时切到主线程执行（最多等 5 秒）。
     * 获取失败时返回 null。
     */
    fun captureSnapshotAsync(): ServerSnapshot? {
        if (plugin.server.isPrimaryThread) return captureSnapshot()
        val future = CompletableFuture<ServerSnapshot>()
        plugin.submit {
            try {
                future.complete(captureSnapshot())
            } catch (error: Throwable) {
                future.completeExceptionally(error)
            }
        }
        return try {
            future.get(5, TimeUnit.SECONDS)
        } catch (error: Throwable) {
            plugin.log_warning("在线列表快照获取失败: ${error.message}")
            null
        }
    }

    /** 在主线程捕获在线玩家快照。读取皮肤与背包状态不是线程安全的。 */
    fun captureSnapshot(): ServerSnapshot {
        val adminFirst = config.onlineListAdminFirst()
        val adminPermission = config.onlineListAdminPermission()
        val players = plugin.server.onlinePlayers.values.map { player ->
            val skinImage = try {
                player.skin?.skinData
            } catch (_: Throwable) {
                null
            }
            OnlineListPlayer(
                name = player.name,
                uuid = player.uniqueId.toString(),
                skinData = skinImage?.data,
                skinWidth = skinImage?.width ?: 0,
                skinHeight = skinImage?.height ?: 0,
                administrator = player.isOp ||
                    (adminPermission.isNotEmpty() && player.hasPermission(adminPermission))
            )
        }
        val sorted = OnlineListPages.sorted(players, adminFirst)
        return ServerSnapshot(
            serverName = config.serverName(),
            maxPlayers = plugin.server.maxPlayers,
            totalOnlinePlayers = sorted.size,
            players = sorted
        )
    }

    private fun namedThreads(prefix: String): ThreadFactory {
        val index = AtomicInteger()
        return ThreadFactory { runnable ->
            Thread(runnable, "$prefix-${index.incrementAndGet()}").apply { isDaemon = true }
        }
    }
}
