package cn.huohuas001.huhobotPenguin.nukkit

import cn.huohuas001.bot.HuHoBot
import cn.huohuas001.bot.QClient
import cn.huohuas001.bot.addon.Addon
import cn.huohuas001.bot.addon.AddonManager
import cn.huohuas001.bot.agent.AgentCommandMode
import cn.huohuas001.bot.events.commands.BaseCommand
import cn.huohuas001.bot.events.commands.CustomCommandRegistry
import cn.huohuas001.bot.events.commands.RegisteredCommand
import cn.huohuas001.bot.provider.AdminMode
import cn.huohuas001.bot.provider.ChatFormat
import cn.huohuas001.bot.provider.CustomCommandDetail
import cn.huohuas001.bot.provider.HExecution
import cn.huohuas001.bot.provider.Motd
import cn.huohuas001.bot.provider.PlayerEventFormat
import cn.huohuas001.bot.provider.WhiteList
import cn.huohuas001.bot.tools.Cancelable
import cn.huohuas001.bot.web.QrAuthState
import cn.huohuas001.bot.web.WebUiServer
import cn.huohuas001.huhobotPenguin.adapter.api.MsgPack
import cn.huohuas001.huhobotPenguin.adapter.api.toMsgPack
import cn.huohuas001.huhobotPenguin.adapter.api.withCommand
import cn.huohuas001.huhobotPenguin.adapter.config.YamlConfig
import cn.huohuas001.huhobotPenguin.nukkit.commands.AtCommand
import cn.huohuas001.huhobotPenguin.nukkit.commands.CommandOutputAppender
import cn.huohuas001.huhobotPenguin.nukkit.commands.HuHoBotCommand
import cn.huohuas001.huhobotPenguin.nukkit.commands.NukkitCommandExecutor
import cn.huohuas001.huhobotPenguin.nukkit.commands.QqBindCommand
import cn.huohuas001.huhobotPenguin.nukkit.commands.SendCommand
import cn.huohuas001.huhobotPenguin.nukkit.events.ForceBindGuard
import cn.huohuas001.huhobotPenguin.nukkit.events.OnBotCommand
import cn.huohuas001.huhobotPenguin.nukkit.events.OnBotRecvMsg
import cn.huohuas001.huhobotPenguin.nukkit.events.PlayerEvents
import cn.huohuas001.huhobotPenguin.nukkit.inventory.OfflineInventorySnapshots
import cn.huohuas001.huhobotPenguin.nukkit.inventory.InventoryRenderer
import cn.huohuas001.huhobotPenguin.nukkit.integration.PlaceholderApiSupport
import cn.huohuas001.huhobotPenguin.nukkit.manager.ConfigMigrator
import cn.huohuas001.huhobotPenguin.nukkit.manager.QrLoginManager
import cn.huohuas001.huhobotPenguin.nukkit.scripting.NukkitScriptLoader
import cn.nukkit.command.Command
import cn.nukkit.command.CommandSender
import cn.nukkit.command.PluginIdentifiableCommand
import cn.nukkit.command.data.CommandData
import cn.nukkit.event.Event
import cn.nukkit.level.Sound
import cn.nukkit.plugin.PluginBase
import cn.nukkit.plugin.PluginLogger
import com.alibaba.fastjson.JSONArray
import com.alibaba.fastjson.JSONObject
import io.github.kloping.qqbot.api.v2.GroupMessageEvent
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * Nukkit（Nukkit-MOT）平台适配器。
 *
 * 与 Spigot 适配器共享 `common-Bot` 的全部运行时（QQ 客户端、指令系统、AI Agent、WebUI、
 * 绑定/白名单/认证状态），本类只负责平台差异部分：配置读写、原生命令执行、广播、调度与
 * 服务器信息查询。
 */
class HuHoBotNukkit : PluginBase(), HuHoBot {

    private lateinit var config: YamlConfig
    private lateinit var pluginLogger: PluginLogger
    private lateinit var offlineInventorySnapshots: OfflineInventorySnapshots

    private var scriptLoader: NukkitScriptLoader? = null

    /** 脚本加载器，供 `/huhobot scripts reload` 与脚本自身取用。 */
    fun getScriptLoader(): NukkitScriptLoader? = scriptLoader

    private val huHoBotCommand by lazy { HuHoBotCommand(this) }
    private val atCommand by lazy { AtCommand(this) }
    private val qqBindCommand by lazy { QqBindCommand(this) }
    private val sendCommand by lazy { SendCommand(this) }

    override fun onEnable() {
        pluginLogger = logger
        config = YamlConfig(File(dataFolder, "config.yml"), DEFAULT_BEDROCK_PORT) { log_warning(it) }
        config.initialize { javaClass.classLoader.getResourceAsStream("config.yml") }
        ConfigMigrator.upgrade(config) { log_info(it) }

        InventoryRenderer.init(dataFolder, config.inventoryRender(), this::log_warning)
        offlineInventorySnapshots = OfflineInventorySnapshots(this).also { it.start() }

        server.pluginManager.registerEvents(PlayerEvents(this), this)
        server.pluginManager.registerEvents(ForceBindGuard(this), this)
        PlaceholderApiSupport.setup(this)
        preloadAddonApiClasses()
        loadScriptAddons()
        initializeRuntime()
        // QQ 客户端是异步启动的，延迟检查一次强制绑定是否可用（不可用会自动关闭开关）
        scheduleForceBindCheck()
        log_info("HuHoBotPenguin-NukkitPlatform 已加载（平台：Nukkit-MOT，服务端版本：${server.version}）")
    }

    override fun onDisable() {
        try {
            scriptLoader?.unloadAll()
            scriptLoader = null
            if (::offlineInventorySnapshots.isInitialized) offlineInventorySnapshots.close()
        } finally {
            shutdownRuntime()
            CommandOutputAppender.removeInstance()
        }
    }

    /** plugin.yml 中声明的指令统一在这里分发。 */
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        return when (command.name.lowercase()) {
            "huhobot" -> huHoBotCommand.execute(sender, args)
            "at" -> atCommand.execute(sender, args)
            "qqbind" -> qqBindCommand.execute(sender, args)
            "send" -> sendCommand.execute(sender, args)
            else -> false
        }
    }

    override fun reloadPluginConfig() {
        config.reload()
        ConfigMigrator.upgrade(config) { log_info(it) }
        InventoryRenderer.init(dataFolder, config.inventoryRender(), this::log_warning)
        PlaceholderApiSupport.setup(this)
        reloadRuntimeConfig()
        // 配置里开着强制绑定但 QQ 不可用时，自动改回关闭，避免把玩家挡在门外
        ensureForceBindAvailable()
    }

    /**
     * `/huhobot reload` 的完整重启。
     *
     * Nukkit 的指令由 plugin.yml 在插件加载时注册，重启不需要（也不能）重复注册，
     * 因此这里只做「重载配置 → 重建 WebUI → 重启 QQ 客户端」。
     */
    fun fullRestart() {
        reloadPluginConfig()
        WebUiServer.stop()
        WebUiServer.start()
        launchQqClient()
    }

    // ---------------------------------------------------------------- 平台原语

    override fun createCommandExecutor(): HExecution =
        NukkitCommandExecutor(this, config.commandSenderMode().equals("Hybrid", ignoreCase = true))

    override fun broadcastMessage(msg: String) {
        submit { server.broadcastMessage(msg) }
    }

    override fun broadcastMessage(msg: String, highlightedPlayers: List<String>) {
        submit {
            server.broadcastMessage(msg)
            if (highlightedPlayers.isEmpty()) return@submit
            for (playerName in highlightedPlayers) {
                val player = server.onlinePlayers.values.firstOrNull { it.name.equals(playerName, true) } ?: continue
                player.level.addSound(player, Sound.NOTE_BELL, 1.0f, 1.0f, player)
            }
        }
    }

    override fun submit(task: Runnable): Cancelable =
        NukkitTaskCancelable(server.scheduler.scheduleTask(NukkitTask(this, task)))

    override fun submitLater(delay: Long, task: Runnable): Cancelable =
        NukkitTaskCancelable(server.scheduler.scheduleDelayedTask(NukkitTask(this, task), delay.toInt()))

    override fun submitTimer(delay: Long, period: Long, task: Runnable): Cancelable =
        NukkitTaskCancelable(
            server.scheduler.scheduleDelayedRepeatingTask(
                NukkitTask(this, task),
                delay.toInt(),
                period.toInt()
            )
        )

    override fun getOnlineList(): List<String> = server.onlinePlayers.values.map { it.name }.sorted()

    // ---------------------------------------------------------------- 配置

    override fun getConfigFile(): File = config.file
    override fun getBotAppId(): String = config.botAppId()
    override fun getBotSecret(): String = config.botSecret()
    override fun getChatFormat(): ChatFormat = config.chatFormat()
    override fun getPlayerEventFormat(): PlayerEventFormat = config.playerEventFormat()
    override fun getMarkdownFiles(): Map<String, String> = config.markdownFiles()
    override fun getMotd(): Motd = config.motd()
    override fun getWhiteList(): WhiteList = config.whiteList()
    override fun getFilterRegexList(): List<String> = config.filterRegexList()
    override fun getAdminMode(): AdminMode = config.adminMode()
    override fun getAdminList(): List<String> = config.adminOpenIds()
    override fun getGroupOpenIdList(): List<String> = config.groupOpenIds()
    override fun isUpdateCheckEnabled(): Boolean = config.updateCheckEnabled()
    override fun getUpdateCheckUrls(): String = config.updateCheckUrls()
    override fun isAgentFetchResultHidden(): Boolean = config.agentFetchResultHidden()
    override fun isPlaceholderApiEnabled(): Boolean = config.placeholderApiEnabled()
    override fun applyPlaceholders(playerName: String?, text: String): String =
        PlaceholderApiSupport.apply(playerName, text)

    /**
     * 每条群消息都会先走这里，把消息交给第三方扩展（Nukkit 事件 `OnBotRecvMsg`）。
     * 返回 true 表示扩展已接管（事件被取消），消息不再进入内置命令分发与聊天转发。
     */
    override fun onBotReceivedGroupMessage(event: GroupMessageEvent, messageSequence: Int): Boolean {
        val msgPack = event.toMsgPack(messageSequence)
        val botEvent = OnBotRecvMsg(
            msgPack = msgPack,
            replyTextAction = { text ->
                QClient.replyText(msgPack.groupOpenId, msgPack.messageId, msgPack.messageSequence, text)
            },
            replyMarkdownAction = { markdown, keyboard ->
                QClient.replyMarkdown(
                    msgPack.groupOpenId, msgPack.messageId, msgPack.messageSequence, markdown, keyboard
                )
            },
            replyImageAction = { text, imageUrl -> QClient.replyWithImg(event, text, imageUrl) }
        )
        callSyncEvent(botEvent)
        return botEvent.isCancelled
    }

    /**
     * 命中自定义命令时触发（Nukkit 事件 `OnBotCommand`）。
     * 返回 true 表示扩展已自行处理，命令模板里那条服务器命令会被跳过。
     */
    override fun onBotCommand(event: GroupMessageEvent, messageSequence: Int): Boolean {
        val msgPack = event.toMsgPack(messageSequence).withCommand(event.rawMessage.content.orEmpty())
        val botEvent = OnBotCommand(
            msgPack = msgPack,
            replyTextAction = { text ->
                QClient.replyText(msgPack.groupOpenId, msgPack.messageId, msgPack.messageSequence, text)
            },
            replyMarkdownAction = { markdown, keyboard ->
                QClient.replyMarkdown(
                    msgPack.groupOpenId, msgPack.messageId, msgPack.messageSequence, markdown, keyboard
                )
            },
            replyImageAction = { text, imageUrl -> QClient.replyWithImg(event, text, imageUrl) }
        )
        callSyncEvent(botEvent)
        return botEvent.isCancelled
    }

    /**
     * 在主线程触发事件。
     *
     * QQ 消息回调跑在 SDK 的线程池上，而扩展多半要碰服务端状态（发命令、读玩家数据），
     * 必须在主线程执行。已经在主线程时直接调用，避免自己等自己造成死锁。
     */
    private fun <T : Event> callSyncEvent(event: T): T {
        if (server.isPrimaryThread) {
            server.pluginManager.callEvent(event)
            return event
        }
        return try {
            val future = CompletableFuture<T>()
            server.scheduler.scheduleTask(this) {
                try {
                    server.pluginManager.callEvent(event)
                    future.complete(event)
                } catch (error: Throwable) {
                    future.completeExceptionally(error)
                }
            }
            future.get(EVENT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } catch (error: Exception) {
            log_error("同步触发 Nukkit 事件失败: ${error.message}")
            event
        }
    }

    override fun isAuthenticationEnabled(): Boolean = config.authenticationEnabled()
    override fun shouldSuppressQqBotConsoleOutput(): Boolean = config.suppressQqBotConsoleOutput()
    override fun getFullAmount(): Boolean = config.fullForwardingByDefault()
    override fun getCommandList(): Map<String, Boolean> = config.commandSwitches()
    override fun getCommandMenuList(): Map<String, Boolean> = config.commandMenuSwitches()
    override fun getCommandMenuPriorities(): Map<String, Int> = config.commandMenuPriorities()
    override fun showAdminCommandsInMenu(): Boolean = config.showAdminCommandsInMenu()
    override fun getAuditBaseUrl(): String? = config.auditBaseUrl()
    override fun getAuditApiKey(): String? = config.auditApiKey()
    override fun getAuditModel(): String? = config.auditModel()
    override fun getCustomCommands(): List<CustomCommandDetail> = config.customCommands()
    override fun getBotName(): String = config.botName()
    override fun getServerName(): String = config.serverName()
    override fun getPlatform(): String = "Nukkit"
    override fun getPluginVersion(): String = description.version
    override fun getServerVersion(): String = server.version

    override fun getAgentEnabled(): Boolean = config.agentEnabled()
    override fun getAgentBaseUrl(): String? = config.agentBaseUrl()
    override fun getAgentApiKey(): String? = config.agentApiKey()
    override fun getAgentModel(): String? = config.agentModel()
    override fun getAgentCommandMode(): AgentCommandMode = config.agentCommandMode()
    override fun getBindingRequireGameVerification(): Boolean = config.bindingRequireGameVerification()

    override fun isForceBindEnabled(): Boolean = config.forceBindEnabled()

    override fun getForceBindGroups(): List<String> = config.forceBindGroups()

    override fun getVerifyExemptPlayers(): List<String> = config.verifyExemptPlayers()

    /**
     * 写回免验证名单。
     *
     * 走 [applyWebUiConfigChanges] 这条通用写入通道，保证 config.yml 落盘后
     * 配置缓存同步刷新，不必重启即可生效。
     */
    private fun saveVerifyExemptPlayers(players: List<String>): Boolean {
        val changes = JSONObject()
        changes["binding.verify-exempt"] = players
        return applyWebUiConfigChanges(changes)
    }

    override fun addVerifyExemptPlayer(playerName: String): Boolean {
        if (playerName.isBlank()) return false
        if (isVerifyExempt(playerName)) return true
        return saveVerifyExemptPlayers(config.verifyExemptPlayers() + playerName)
    }

    override fun removeVerifyExemptPlayer(playerName: String): Boolean {
        if (playerName.isBlank()) return false
        val remaining = config.verifyExemptPlayers()
            .filterNot { it.equals(playerName, ignoreCase = true) }
        return saveVerifyExemptPlayers(remaining)
    }

    /**
     * 解绑后立即踢出在线玩家（仅强制绑定开启时由调用方触发）。
     *
     * 不这样做的话，玩家可以先绑定进服、再在 QQ 里解绑，之后继续留在服务器里，
     * 直到下次进服才被守卫拦下。
     */
    override fun kickUnboundPlayer(playerName: String): Boolean {
        // 免验证玩家解绑后照样能进服，踢出没有意义
        if (isVerifyExempt(playerName)) return false
        val target = server.getPlayer(playerName) ?: return false
        val separator = "${cn.nukkit.utils.TextFormat.DARK_GRAY}${cn.nukkit.utils.TextFormat.STRIKETHROUGH}${"─".repeat(30)}"
        val reason = buildString {
            appendLine(separator)
            appendLine("${cn.nukkit.utils.TextFormat.RED}${cn.nukkit.utils.TextFormat.BOLD}绑定已解除")
            appendLine(separator)
            appendLine("${cn.nukkit.utils.TextFormat.GRAY}你的 QQ 绑定已被解除，本次游戏会话已结束。")
            appendLine("${cn.nukkit.utils.TextFormat.GRAY}重新进入服务器时需要重新绑定。")
        }
        // 延后到下一 tick，避免在消息回调里直接改变玩家状态（Nukkit submitLater 单位是 tick）
        submitLater(1L) {
            if (target.isOnline) target.kick(reason)
        }
        log_info("强制绑定: $playerName 已解除绑定，踢出在线会话")
        return true
    }

    /**
     * 强制绑定的前置条件是 QQ 机器人可用：玩家被踢出后要能在 QQ 群完成绑定。
     *
     * QQ 连接失败时若仍开着强制绑定，所有未绑定玩家都会被挡在门外且无法完成绑定，
     * 因此这里直接把开关改回 false 并落盘。
     *
     * @return 强制绑定最终是否处于开启状态
     */
    fun ensureForceBindAvailable(): Boolean {
        if (!config.forceBindEnabled()) return false
        if (QClient.getStarter() != null) return true

        log_error("QQ 机器人未连接，无法完成强制绑定的验证码流程，已自动关闭 binding.force-bind")
        config.set("binding.force-bind", false)
        config.save()
        return false
    }

    /**
     * 启动后确认强制绑定是否可用。
     *
     * QQ 客户端是异步启动的，刚开启时通常还没连上，所以这里延迟一段时间再检查；
     * 连上则保持开启，未连上则由 [ensureForceBindAvailable] 自动关闭。
     */
    private fun scheduleForceBindCheck() {
        if (!config.forceBindEnabled()) return
        submitLater(FORCE_BIND_CHECK_DELAY_TICKS) {
            ensureForceBindAvailable()
        }
    }

    override fun getCommandBlacklist(): List<String> = config.commandBlacklist()

    override fun getWebUiPort(): Int = config.webUiPort()

    override fun getWebUiConfigValues(): Map<String, Any?> =
        config.flatten().toMutableMap().apply {
            put("command-panel.show-admin-commands", config.showAdminCommandsInMenu())
            put("bot.auto-add-groups", config.autoAddGroups())
            put("update-check.enabled", config.updateCheckEnabled())
            put("update-check.url", config.updateCheckUrls())
            put("placeholder-api.enabled", config.placeholderApiEnabled())
            put("agent.hide-fetch-results", config.agentFetchResultHidden())
            BaseCommand.allCommands().distinctBy { it.command }.forEach { command ->
                val name = command.command
                if (!containsKey("commands.$name") && !containsKey("commands.$name.enable")) {
                    put("commands.$name.enable", true)
                }
                if (!containsKey("commands.$name.pushMenu")) {
                    put("commands.$name.pushMenu", true)
                }
                if (!containsKey("commands.$name.priority")) {
                    put("commands.$name.priority", if (name == "agent") 0 else 100)
                }
            }
        }

    override fun applyWebUiConfigChanges(changes: JSONObject): Boolean {
        return try {
            changes.forEach { (path, value) ->
                config.set(path, convertJsonValue(value))
            }
            config.save()
            reloadPluginConfig()
            true
        } catch (error: Exception) {
            log_error("WebUI 保存配置失败: ${error.message}")
            false
        }
    }

    /** 将 fastjson 值转换为 YAML 可接受的 Java 类型。 */
    private fun convertJsonValue(value: Any?): Any? = when (value) {
        is JSONObject -> value.entries.associate { (k, v) -> k to convertJsonValue(v) }
        is JSONArray -> value.map { convertJsonValue(it) }
        else -> value
    }

    /** 群 OpenID 不在配置中时自动收录并写回 config.yml。 */
    override fun addGroupOpenId(groupOpenId: String): Boolean {
        val openId = groupOpenId.trim()
        if (openId.isEmpty() || !config.autoAddGroups()) return false
        val current = config.groupOpenIds()
        if (current.contains(openId)) return false
        return try {
            config.set("bot.groups", current + openId)
            config.save()
            log_info("已自动把群 $openId 添加到 bot.groups")
            reloadRuntimeConfig()
            true
        } catch (error: Exception) {
            log_error("自动添加群 $openId 失败: ${error.message}")
            false
        }
    }

    // ---------------------------------------------------------------- 附属插件（addon center）

    /**
     * 把附属插件中心下载到的 jar 写进服务端 `plugins/` 目录。
     *
     * 只落盘、不做热加载 —— Nukkit 的插件在启动时一次性扫描，必须重启才生效。
     * 与 Spigot 侧同一套实现：文件名先做安全校验，避免 `../` 之类的路径穿越。
     */
    override fun installAddon(fileName: String, bytes: ByteArray): Boolean =
        writeAddonFile(fileName) { it.writeBytes(bytes) }

    /** 删除已安装的附属插件文件。文件本就不存在时同样返回 true，交由调用方清理安装记录。 */
    override fun removeAddon(fileName: String): Boolean {
        val safeName = fileName.trim()
        if (!isSafeAddonFileName(safeName)) {
            log_warning("拒绝删除非法的附属插件文件名: $fileName")
            return false
        }
        return try {
            val target = pluginsFolder().resolve(safeName)
            !target.exists() || target.delete()
        } catch (error: Exception) {
            log_error("删除附属插件 $safeName 失败: ${error.message}")
            false
        }
    }

    private fun writeAddonFile(fileName: String, write: (File) -> Unit): Boolean {
        val safeName = fileName.trim()
        if (!isSafeAddonFileName(safeName)) {
            log_warning("拒绝写入非法的附属插件文件名: $fileName")
            return false
        }
        return try {
            val folder = pluginsFolder()
            if (!folder.isDirectory && !folder.mkdirs()) return false
            write(folder.resolve(safeName))
            true
        } catch (error: Exception) {
            log_error("写入附属插件 $safeName 失败: ${error.message}")
            false
        }
    }

    /** 服务端插件目录。本插件的数据目录是 `plugins/<插件名>/`，所以父目录就是 `plugins/`。 */
    private fun pluginsFolder(): File = dataFolder.parentFile?.takeIf { it.isDirectory } ?: File("plugins")

    private fun isSafeAddonFileName(name: String): Boolean =
        name.isNotEmpty() && !name.contains("..") && !name.contains('/') && !name.contains('\\')

    /** 供扫码登录写回凭据；走定点写入，保留 config.yml 里的注释。 */
    fun applyCredentialChanges(appId: String, secret: String) {
        config.set("bot.app-id", appId)
        config.set("bot.secret", secret)
        config.save()
    }

    // ---------------------------------------------------------------- 扫码登录

    /**
     * 凭据为空时不再在异步线程里跑阻塞式扫码循环，改为在后台开启扫码授权会话
     * （控制台与 WebUI 共用同一个会话），WebUI 照常启动。
     */
    override fun launchQqClient() {
        val appId = getBotAppId()
        val secret = getBotSecret()
        if (appId.isBlank() || secret.isBlank()) {
            log_info("未配置 bot.app-id / bot.secret，已开启扫码授权：扫描控制台二维码，或打开 WebUI「QQ 机器人」页面扫码")
            QrLoginManager.start(this)
            return
        }
        submitAsync { startClient(appId, secret) }
    }

    // ---------------------------------------------------------------- WebUI 扫码授权桥接

    override fun getQrAuthState(): QrAuthState = QrLoginManager.state()

    override fun startQrAuth(): Boolean = QrLoginManager.start(this)

    override fun cancelQrAuth(): Boolean = QrLoginManager.cancel()

    private fun startClient(appId: String, secret: String) {
        try {
            log_info("正在启动 QQ 客户端（AppID: $appId）…")
            QClient.launchClient(appId, secret, getQqBotLogFilePattern())
            log_info("QQ 客户端启动完成，已连接 QQ 开放平台")
        } catch (error: Throwable) {
            // 必须捕获 Throwable：NoClassDefFoundError / NoSuchMethodError 这类链接错误不是 Exception，
            // 只 catch Exception 会把它们静默丢进 CompletableFuture，外部表现为「什么都没发生」。
            log_error("QQ 机器人启动失败: ${error.javaClass.name}: ${error.message}")
            log_error(error.stackTraceToString())
        }
    }

    /** config.yml 中是否已经填好机器人凭据（供扫码流程判断是否还需要继续）。 */
    fun hasCredentialsConfigured(): Boolean =
        getBotAppId().isNotBlank() && getBotSecret().isNotBlank()

    // ---------------------------------------------------------------- 服务器信息（供 AI Agent）

    override fun getServerPluginList(): List<String> =
        server.pluginManager.plugins.values.map { it.name }.sorted()

    // 形参名与 ConfigProvider 保持一致，避免 Kotlin 的具名实参告警。
    override fun getServerCommandHelp(plugin: String?, command: String?): String = when {
        command != null -> formatSingleCommand(command)
        plugin != null -> formatPluginCommands(plugin)
        else -> formatAllCommands()
    }

    private fun formatSingleCommand(commandName: String): String {
        val known = server.commandMap.commands
        val command = server.getPluginCommand(commandName) as? Command
            ?: known[commandName.lowercase()]
            ?: known.values.firstOrNull { it.name.equals(commandName, true) }
            ?: return "未找到命令: /$commandName"
        return formatCommandDetail(command)
    }

    private fun formatCommandDetail(command: Command): String = buildString {
        appendLine("命令：/${command.name}")
        command.aliases.takeIf { it.isNotEmpty() }?.let { appendLine("别名：${it.joinToString(", ")}") }
        val description = command.description.orEmpty()
        if (description.isNotBlank()) appendLine("描述：$description")
        val usage = command.usage.orEmpty().trim()
        if (usage.isNotBlank() && usage != "/<command>") appendLine("用法：$usage")
        command.permission.orEmpty().takeIf { it.isNotBlank() }?.let { appendLine("权限：$it") }
        val owner = (command as? PluginIdentifiableCommand)?.plugin
        if (owner != null) {
            appendLine("所属插件：${owner.name}")
        } else {
            appendLine("类型：服务端内置命令（服务端版本：${server.version}）")
            appendLine("提示：内置命令的具体参数与语法请按服务端版本使用，可在游戏内执行 /help <命令> 查看。")
        }
    }.trimEnd()

    private fun formatPluginCommands(pluginName: String): String {
        val plugin = server.pluginManager.getPlugin(pluginName)
            ?: return "未找到插件：$pluginName（可用插件：${getServerPluginList().joinToString(", ")}）"

        val commands = plugin.description.commands
        if (commands.isEmpty()) return "插件 ${plugin.name} 没有注册命令。"
        return buildString {
            appendLine("插件 ${plugin.name} 注册的命令：")
            commands.toSortedMap().forEach { (label, meta) ->
                val description = (meta as? CommandData)?.description.orEmpty()
                val suffix = description.takeIf { it.isNotBlank() }?.let { " - $it" }.orEmpty()
                appendLine("/$label$suffix")
            }
        }.trimEnd()
    }

    private fun formatAllCommands(): String {
        val perPlugin = sortedMapOf<String, MutableList<String>>()
        val seen = mutableSetOf<String>()
        server.commandMap.commands.values.forEach { command ->
            val label = command.name
            if (label.isNullOrBlank() || !seen.add(label)) return@forEach
            val owner = (command as? PluginIdentifiableCommand)?.plugin?.name ?: "服务端内置命令"
            perPlugin.getOrPut(owner) { mutableListOf() }.add(label)
        }
        if (perPlugin.isEmpty()) return "服务器没有可查询的命令。"
        return buildString {
            appendLine("服务器命令概览：")
            perPlugin.forEach { (owner, labels) ->
                appendLine("- $owner：${labels.sorted().joinToString(", ")}")
            }
        }.trimEnd()
    }

    override fun getServerLogs(lines: Int?, keyword: String?): String {
        val logFile = resolveLogFile() ?: return "无法定位服务端日志文件（logs/server.log）"
        val allLines = try {
            logFile.readLines(Charsets.UTF_8)
        } catch (error: Throwable) {
            return "读取服务端日志失败：${error.message}"
        }

        val lineCount = (lines ?: 50).coerceIn(1, 500)
        val kw = keyword?.trim()?.takeIf(String::isNotEmpty)
        val context = 5

        val selected: List<String>
        val matchCount: Int?
        if (kw != null) {
            val indices = allLines.indices.filter { allLines[it].contains(kw, ignoreCase = true) }
            if (indices.isEmpty()) {
                return "未在服务端日志中找到包含「$kw」的内容。"
            }
            matchCount = indices.size
            if (matchCount > MAX_LOG_MATCHES) {
                return "关键词「$kw」匹配过多（$matchCount 处），请换用更精确的关键词。"
            }
            val from = (indices.first() - context).coerceAtLeast(0)
            val to = (indices.last() + context + 1).coerceAtMost(allLines.size)
            // 命中分散时只截取命中行附近，避免把整份日志塞进 QQ 消息。
            selected = if (to - from <= MAX_LOG_WINDOW_LINES) {
                allLines.subList(from, to)
            } else {
                indices.take(MAX_LOG_MATCHES).flatMap { index ->
                    allLines.subList((index - context).coerceAtLeast(0), (index + context + 1).coerceAtMost(allLines.size))
                }.distinct()
            }
        } else {
            matchCount = null
            selected = allLines.takeLast(lineCount)
        }

        val content = selected.joinToString("\n").trimEnd()
        val tip = matchCount?.let { "\n\n(按关键词「$kw」过滤，共匹配 $it 处)" } ?: ""
        return content + tip
    }

    /** 定位服务端日志文件。Nukkit-MOT 由 log4j2 写入 `logs/server.log`。 */
    private fun resolveLogFile(): File? {
        val candidates = listOfNotNull(
            File(server.dataPath, "logs/server.log"),
            File(server.dataPath, "logs/latest.log"),
            File("logs/server.log")
        )
        return candidates.firstOrNull { it.isFile }
    }

    // ---------------------------------------------------------------- 背包 / 末影箱

    override fun getPlayerInventory(playerName: String): String? =
        InventoryRenderer.renderTextInventory(findSnapshot(playerName))

    override fun getPlayerInventoryImage(playerName: String): ByteArray? =
        InventoryRenderer.renderInventory(findSnapshot(playerName))

    override fun getPlayerEnderChestImage(playerName: String): ByteArray? =
        InventoryRenderer.renderEnderChest(findSnapshot(playerName))

    private fun findSnapshot(playerName: String) =
        if (::offlineInventorySnapshots.isInitialized) offlineInventorySnapshots.find(playerName) else null

    // ---------------------------------------------------------------- 扩展（addon）API

    /**
     * 预热扩展 API 涉及的类。
     *
     * Nukkit 给每个插件一个独立的 PluginClassLoader，其 `findClass` 的查找顺序是
     * 「自己的 jar → JavaPluginLoader 的全局**已加载**类注册表」（父加载器在最前面，
     * 但只含服务端自带的库）。而这些事件类是懒加载的 —— 不预热的话，addon 在自己的
     * onEnable 里注册 `OnBotCommand` 监听器时会因找不到类而失败，且要等到第一条
     * QQ 指令才暴露出来。这里主动触发一次类加载，把它们登记进全局注册表。
     */
    private fun preloadAddonApiClasses() {
        for (clazz in listOf(OnBotRecvMsg::class.java, OnBotCommand::class.java, MsgPack::class.java)) {
            try {
                Class.forName(clazz.name, true, clazz.classLoader)
            } catch (error: Throwable) {
                log_warning("预热扩展 API 类 ${clazz.name} 失败: ${error.message}")
            }
        }
    }

    /**
     * 注册一个扩展。
     *
     * 第三方插件先 `server.pluginManager.getPlugin("HuHoBotPenguin-NukkitPlatform")`
     * 拿到本插件实例，再调用本方法；随后用 [registerBotCommand] 注册命令，
     * 或监听 `OnBotRecvMsg` / `OnBotCommand` 自己处理。
     *
     * @param name 扩展名称，全局唯一
     */
    fun registerAddon(
        name: String,
        version: String = "1.0.0",
        description: String = "",
        author: String = ""
    ): Boolean {
        if (name.isBlank()) {
            log_error("registerAddon 失败：扩展名称不能为空")
            return false
        }
        AddonManager.register(Addon(name, version, description, author))
        log_info("已注册扩展：$name v$version")
        return true
    }

    /** 注册一条运行时自定义命令（不归属任何扩展）。 */
    fun registerBotCommand(
        key: String,
        command: String,
        permission: Int = 0,
        pushMenu: Boolean = true
    ): Boolean {
        val registered = CustomCommandRegistry.register(
            CustomCommandDetail(key, command, permission, pushMenu)
        )
        if (!registered) return false
        submitAsync { QClient.syncGroupPanels() }
        return true
    }

    /**
     * 注册一条运行时自定义命令并归属到扩展。
     *
     * `command` 是**服务器命令模板**。若扩展想自己干活（调外部 API 等），
     * 监听 `OnBotCommand` 并取消事件即可跳过这条模板命令。
     *
     * @param addonName 已通过 [registerAddon] 注册的扩展名称
     * @param key       QQ 群里的命令 key（`/<key>` 或 `/执行 <key>` 触发）
     */
    fun registerBotCommand(
        addonName: String,
        key: String,
        command: String,
        permission: Int = 0,
        pushMenu: Boolean = true
    ): Boolean {
        if (addonName !in AddonManager) {
            log_error("registerBotCommand 失败：扩展 '$addonName' 未注册，请先调用 registerAddon")
            return false
        }
        val registered = CustomCommandRegistry.register(
            CustomCommandDetail(key, command, permission, pushMenu)
        )
        if (!registered) return false
        AddonManager.addCommand(
            addonName,
            RegisteredCommand(
                command = key,
                describe = command,
                onlyAdmin = permission > 0,
                source = addonName
            )
        )
        submitAsync { QClient.syncGroupPanels() }
        return true
    }

    /** 注销运行时自定义命令。配置文件里的命令不会被删除。 */
    fun unregisterBotCommand(key: String): Boolean {
        val removed = CustomCommandRegistry.unregister(key)
        if (removed) submitAsync { QClient.syncGroupPanels() }
        return removed
    }

    // ---------------------------------------------------------------- 脚本扩展

    /**
     * 加载 `plugins/HuHoBotPenguin-NukkitPlatform/addons` 下的目录插件。
     * `.lua` 用 Lua 5.4（与 NuclearScripting 同一约定：`on<事件名>` 即监听），打在主 jar 里。
     * `.js` 用 GraalJS、`.py` 用 GraalPy（Python 3），这两个引擎都在独立 jar 里，
     * 需要放到 `engines/` 目录。只扫本插件自己的目录，不会碰服务器 `plugins/` 里别人的脚本。
     */
    private fun loadScriptAddons() {
        val loader = NukkitScriptLoader(this)
        scriptLoader = loader
        try {
            val loaded = loader.loadAll()
            log_info("脚本扩展已加载 $loaded 个，目录 ${loader.folder().path}")
        } catch (error: Throwable) {
            // 脚本引擎的异常经常把真正原因藏在 cause 链里（PolyglotException 尤其如此），
            // 只打 message 会得到一句没有信息量的「null」。
            val chain = generateSequence(error) { it.cause }.take(6).joinToString("  <-  ") {
                it.javaClass.name + (it.message?.let { m -> ": $m" } ?: "")
            }
            log_error("加载脚本扩展时出现异常，插件继续运行: $chain")
        }
    }

    /** 向配置中的所有 QQ 群发送普通文本。供脚本扩展调用。 */
    fun sendBotText(text: String) = sendText(text)

    /** 卸载扩展登记。QQ 命令要另外调用 [unregisterBotCommand]。 */
    fun unregisterAddon(name: String) {
        if (name.isBlank() || name !in AddonManager) return
        AddonManager.unregister(name)
        log_info("已卸载扩展：$name")
    }

    // ---------------------------------------------------------------- 日志

    override fun log_info(msg: String) = pluginLogger.info(msg)
    override fun log_warning(msg: String) = pluginLogger.warning(msg)
    override fun log_error(msg: String) = pluginLogger.error(msg)

    private companion object {
        /** Bedrock 默认端口，用作 motd.server-port 的兜底值。 */
        const val DEFAULT_BEDROCK_PORT = 19132

        /** 关键词过滤时最多容忍的命中次数，避免把整份日志发给 AI。 */
        const val MAX_LOG_MATCHES = 200

        /** 单次返回的最大日志窗口行数。 */
        const val MAX_LOG_WINDOW_LINES = 1200

        /** 等待主线程派发扩展事件的超时。超时后按「扩展未处理」继续，不卡住 QQ 回调线程。 */
        const val EVENT_TIMEOUT_SECONDS = 30L

        /**
         * 启动后延迟多久检查 QQ 是否连上（QQ 客户端为异步启动，留出 15 秒连接时间）。
         *
         * ⚠️ 单位是 **tick**（20 tick = 1 秒），不是毫秒。上游 Spigot 侧写的是 15_000L
         * 传给 runTaskLater，实际会等 750 秒——这里按 15 秒的本意写 300。
         */
        const val FORCE_BIND_CHECK_DELAY_TICKS = 300L
    }
}
