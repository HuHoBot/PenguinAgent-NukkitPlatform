package cn.huohuas001.huhobotPenguin.nukkit

import cn.huohuas001.bot.HuHoBot
import cn.huohuas001.bot.QClient
import cn.huohuas001.bot.agent.AgentCommandMode
import cn.huohuas001.bot.events.commands.BaseCommand
import cn.huohuas001.bot.provider.AdminMode
import cn.huohuas001.bot.provider.ChatFormat
import cn.huohuas001.bot.provider.CustomCommandDetail
import cn.huohuas001.bot.provider.HExecution
import cn.huohuas001.bot.provider.Motd
import cn.huohuas001.bot.provider.PlayerEventFormat
import cn.huohuas001.bot.provider.WhiteList
import cn.huohuas001.bot.tools.Cancelable
import cn.huohuas001.bot.web.WebUiServer
import cn.huohuas001.huhobotPenguin.adapter.config.YamlConfig
import cn.huohuas001.huhobotPenguin.nukkit.commands.AtCommand
import cn.huohuas001.huhobotPenguin.nukkit.commands.CommandOutputAppender
import cn.huohuas001.huhobotPenguin.nukkit.commands.HuHoBotCommand
import cn.huohuas001.huhobotPenguin.nukkit.commands.NukkitCommandExecutor
import cn.huohuas001.huhobotPenguin.nukkit.commands.QqBindCommand
import cn.huohuas001.huhobotPenguin.nukkit.commands.SendCommand
import cn.huohuas001.huhobotPenguin.nukkit.events.PlayerEvents
import cn.huohuas001.huhobotPenguin.nukkit.inventory.OfflineInventorySnapshots
import cn.huohuas001.huhobotPenguin.nukkit.inventory.InventoryRenderer
import cn.huohuas001.huhobotPenguin.nukkit.integration.PlaceholderApiSupport
import cn.huohuas001.huhobotPenguin.nukkit.manager.ConfigMigrator
import cn.huohuas001.huhobotPenguin.nukkit.manager.QrLoginManager
import cn.nukkit.command.Command
import cn.nukkit.command.CommandSender
import cn.nukkit.command.PluginIdentifiableCommand
import cn.nukkit.command.data.CommandData
import cn.nukkit.level.Sound
import cn.nukkit.plugin.PluginBase
import cn.nukkit.plugin.PluginLogger
import com.alibaba.fastjson.JSONArray
import com.alibaba.fastjson.JSONObject
import java.io.File
import java.util.concurrent.CompletableFuture

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
        PlaceholderApiSupport.setup(this)
        initializeRuntime()
        log_info("HuHoBotPenguin-NukkitPlatform 已加载（平台：Nukkit-MOT，服务端版本：${server.version}）")
    }

    override fun onDisable() {
        try {
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

    /** 供扫码登录写回凭据；走定点写入，保留 config.yml 里的注释。 */
    fun applyCredentialChanges(appId: String, secret: String) {
        config.set("bot.app-id", appId)
        config.set("bot.secret", secret)
        config.save()
    }

    // ---------------------------------------------------------------- 扫码登录

    /**
     * 未配置凭据时走扫码登录。
     *
     * 与 Spigot 适配器的差别：这里把扫码流程放进异步线程，避免未扫码时把服务端启动挂死。
     */
    override fun launchQqClient() {
        val appId = getBotAppId()
        val secret = getBotSecret()
        if (appId.isNotBlank() && secret.isNotBlank()) {
            submitAsync { startClient(appId, secret) }
            return
        }

        log_warning("未配置 bot.app-id / bot.secret，尝试扫码登录（也可手动填写后重启）")
        submitAsync {
            val credentials = try {
                QrLoginManager.doQrLogin(this)
            } catch (error: Throwable) {
                log_error("扫码登录失败: ${error.message}")
                null
            }
            if (credentials == null) {
                log_warning("未获得 QQ 机器人凭据，QQ 客户端未启动")
                return@submitAsync
            }
            startClient(credentials.appId, credentials.appSecret)
        }
    }

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
    }
}
