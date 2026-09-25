package cn.huohuas001.huhobotPenguin.adapter.config

import cn.huohuas001.bot.agent.AgentCommandMode
import cn.huohuas001.bot.provider.AdminMode
import cn.huohuas001.bot.provider.ChatFormat
import cn.huohuas001.bot.provider.CustomCommandDetail
import cn.huohuas001.bot.provider.Motd
import cn.huohuas001.bot.provider.PlayerEventFormat
import cn.huohuas001.bot.provider.WhiteList
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.io.InputStream

/**
 * Allay、Nukkit 与代理端共享的轻量 YAML 配置读取器。
 *
 * 平台适配器保持同一套配置键；首次启动直接复制带注释的默认配置，重载时只读取文件，
 * 不会为了补全默认值而重写用户文件。缺失的新配置项由强类型 getter 的默认值兜底。
 *
 * 需要写回配置的场景（WebUI 保存、扫码登录写入凭据、自动收录群号）通过
 * [set] + [save] 完成，底层由 [YamlFileEditor] 定点改写，保留文件里的注释。
 */
class YamlConfig(
    val file: File,
    private val defaultPort: Int,
    private val logger: (String) -> Unit
) {
    @Volatile
    private var values: Map<String, Any?> = emptyMap()

    /** 待写回文件的改动：dotted path -> 新值。 */
    private val pendingWrites = LinkedHashMap<String, Any?>()

    fun initialize(defaultConfig: () -> InputStream?) {
        if (!file.exists()) {
            file.parentFile?.mkdirs()
            val input = defaultConfig()
                ?: throw IllegalStateException("找不到默认配置资源 config.yml")
            input.use { source -> file.outputStream().use(source::copyTo) }
        }
        reload()
    }

    @Synchronized
    fun reload() {
        val yaml = Yaml(DumperOptions().apply {
            defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
        })
        val loaded = file.inputStream().buffered().use { input -> yaml.load<Any?>(input) }
        values = normalizeMap(loaded as? Map<*, *> ?: emptyMap<Any?, Any?>())
    }

    fun botAppId(): String = string("bot.app-id")
    fun botSecret(): String = string("bot.secret")
    fun botName(): String = string("bot.name", "HuHoBot")
    fun serverName(): String = string("serverName", botName())
    fun groupOpenIds(): List<String> = stringList("bot.groups")
    fun suppressQqBotConsoleOutput(): Boolean = boolean("bot.suppress-console-output", true)

    fun redisEnabled(): Boolean = boolean("redis.enabled", false)
    fun redisHost(): String = string("redis.host", "localhost")
    fun redisPort(): Int = integer("redis.port", 6379)
    fun redisPassword(): String? = string("redis.password").takeIf(String::isNotBlank)
    fun redisChannel(): String = string("redis.channel", "HuHoBotChannel")

    fun chatFormat(): ChatFormat = ChatFormat(
        fromGame = string("chat-format.from-game", "[游戏] {message}"),
        fromGroup = string("chat-format.from-group", "[QQ] {name}: {message}"),
        postChat = boolean("chat-format.post-chat", true),
        startWith = string("chat-format.start-with")
    )

    fun playerEventFormat(): PlayerEventFormat = PlayerEventFormat(
        joinEnabled = boolean("player-events.join.enabled", true),
        joinFormat = string("player-events.join.format", "[游戏] {name} 加入了服务器"),
        quitEnabled = boolean("player-events.quit.enabled", true),
        quitFormat = string("player-events.quit.format", "[游戏] {name} 离开了服务器"),
        alwaysForward = boolean("player-events.always-forward", false)
    )

    fun markdownFiles(): Map<String, String> {
        val configured = (node("markdown") as? Map<*, *>)?.entries
            ?.mapNotNull { (key, value) ->
                val name = key?.toString()?.trim().orEmpty()
                if (name.isEmpty() || value == null) null else name to value.toString()
            }
            ?.toMap()
            .orEmpty()
        return mapOf("queryOnline" to "online.md") + configured
    }

    fun motd(): Motd = Motd(
        serverIP = string("motd.server-ip", "127.0.0.1"),
        serverPort = integer("motd.server-port", defaultPort),
        api = string("motd.api"),
        text = string("motd.text"),
        postImg = boolean("motd.post-img", false),
        useMarkdown = boolean("motd.use-markdown", false)
    )

    fun whiteList(): WhiteList = WhiteList(
        addCommand = string("whitelist.add-command", "whitelist add {name}"),
        delCommand = string("whitelist.del-command", "whitelist remove {name}")
    )

    fun filterRegexList(): List<String> = stringList("filter-regex")
    fun adminMode(): AdminMode = AdminMode.from(string("admin.mode", "both")) ?: AdminMode.BOTH
    fun adminOpenIds(): List<String> = stringList("admin.openids")
    fun fullForwardingByDefault(): Boolean = boolean("features.full-amount", false)

    fun commandSwitches(): Map<String, Boolean> {
        val commands = node("commands") as? Map<*, *> ?: return emptyMap()
        return commands.entries.associate { (key, value) ->
            val name = key.toString()
            val enableNode = (value as? Map<*, *>)?.get("enable")
            name to (if (enableNode == null) toBoolean(value) else toBoolean(enableNode))
        }
    }

    /** 命令名 -> 是否推送到 QQ 指令面板。语义与 Spigot 适配器保持一致。 */
    fun commandMenuSwitches(): Map<String, Boolean> {
        val commands = node("commands") as? Map<*, *> ?: return emptyMap()
        return commands.entries.associate { (key, value) ->
            val name = key.toString()
            val default = name !in HIDDEN_FROM_MENU
            val pushMenu = if (value is Map<*, *>) {
                // 复杂格式：commands.xxx.pushMenu: true
                val configured = value["pushMenu"]
                if (configured == null) default else toBoolean(configured, default)
            } else {
                // 简写格式 commands.xxx: true/false 只表达启用与否。
                if (toBoolean(value)) default else false
            }
            name to pushMenu
        }
    }

    /** 数值越小越先进入 QQ 面板；只影响面板，不影响命令执行。 */
    fun commandMenuPriorities(): Map<String, Int> {
        val commands = node("commands") as? Map<*, *> ?: return emptyMap()
        return commands.entries.associate { (key, value) ->
            val name = key.toString()
            val priority = (value as? Map<*, *>)?.get("priority")
            name to ((priority?.toString()?.toIntOrNull() ?: if (name == "agent") 0 else 100).coerceIn(0, 999))
        }
    }

    fun auditBaseUrl(): String? = string("audit.base-url").takeIf(String::isNotBlank)
    fun auditApiKey(): String? = string("audit.api-key").takeIf(String::isNotBlank)
    fun auditModel(): String? = string("audit.model").takeIf(String::isNotBlank)

    fun customCommands(): List<CustomCommandDetail> {
        val entries = node("custom-commands") as? List<*> ?: return emptyList()
        return entries.mapNotNull { raw ->
            val map = raw as? Map<*, *> ?: return@mapNotNull null
            val key = map["key"]?.toString()?.trim().orEmpty()
            val command = map["command"]?.toString()?.trim().orEmpty()
            val permission = map["permission"]?.toString()?.toIntOrNull() ?: 0
            val pushMenu = map["pushMenu"]?.let { toBoolean(it, true) } ?: true
            if (key.isBlank() || command.isBlank()) {
                logger("忽略缺少 key 或 command 的自定义命令配置: $map")
                null
            } else {
                CustomCommandDetail(key, command, permission, pushMenu)
            }
        }
    }

    // ---------------------------------------------------------------- 与 Spigot 适配器对齐的配置项

    /** 收到未配置群的 QQ 消息时，自动把该群 OpenID 写入群列表。 */
    fun autoAddGroups(): Boolean = boolean("bot.auto-add-groups", true)

    fun webUiPort(): Int = integer("webui-port", 5678)

    fun configVersion(): Int = integer("config-version", 0)

    /** 是否启用 QQ 头像认证功能。 */
    fun authenticationEnabled(): Boolean = boolean("features.enable-auth", true)

    fun commandSenderMode(): String = string("command-sender", "Hybrid").ifBlank { "Hybrid" }

    fun showAdminCommandsInMenu(): Boolean = boolean("command-panel.show-admin-commands", true)

    fun updateCheckEnabled(): Boolean = boolean("update-check.enabled", true)

    fun updateCheckUrls(): String = string("update-check.url")

    fun placeholderApiEnabled(): Boolean = boolean("placeholder-api.enabled", true)

    fun bindingRequireGameVerification(): Boolean = boolean("binding.require-game-verification", false)

    fun commandBlacklist(): List<String> = stringList("command-blacklist")
        .map { it.trim().lowercase() }
        .filter(String::isNotEmpty)

    fun agentEnabled(): Boolean = boolean("agent.enabled", false)

    fun agentBaseUrl(): String? = string("agent.base-url").takeIf(String::isNotBlank)

    fun agentApiKey(): String? = string("agent.api-key").takeIf(String::isNotBlank)

    fun agentModel(): String? = string("agent.model").takeIf(String::isNotBlank)

    fun agentCommandMode(): AgentCommandMode =
        AgentCommandMode.from(string("agent.command-mode", "manual")) ?: AgentCommandMode.MANUAL

    fun agentFetchResultHidden(): Boolean = boolean("agent.hide-fetch-results", true)

    fun inventoryRender(): InventoryRenderConfig = InventoryRenderConfig(
        customBackgroundEnabled = boolean("inventory.render.custom-background.enabled", false),
        inventoryFile = string("inventory.render.custom-background.inventory-file", "inventory.png"),
        enderChestFile = string("inventory.render.custom-background.ender-chest-file"),
        fit = string("inventory.render.custom-background.fit", "cover")
    )

    // ---------------------------------------------------------------- 写入支持

    /** 读取原始节点值（dotted path）。 */
    fun raw(path: String): Any? = node(path)

    /** 以 dotted path 展平全部配置值，供 WebUI 表单绑定。 */
    fun flatten(): Map<String, Any?> {
        val result = LinkedHashMap<String, Any?>()
        fun walk(prefix: String, value: Any?) {
            if (value is Map<*, *>) {
                value.forEach { (key, child) ->
                    walk(if (prefix.isEmpty()) key.toString() else "$prefix.$key", child)
                }
            } else {
                result[prefix] = value
            }
        }
        values.forEach { (key, value) -> walk(key, value) }
        return result
    }

    /** 记录一次待落盘的改动，同时立即更新内存值。 */
    fun set(path: String, value: Any?) {
        val trimmed = path.trim()
        if (trimmed.isEmpty()) return
        pendingWrites[trimmed] = value
        applyToMemory(trimmed, value)
    }

    /** 把待落盘改动定点写回文件（保留注释），随后重新加载。 */
    @Synchronized
    fun save() {
        if (pendingWrites.isEmpty()) return
        val editor = YamlFileEditor(file)
        pendingWrites.forEach { (path, value) -> editor.set(path, value) }
        editor.commit()
        pendingWrites.clear()
        reload()
    }

    private fun applyToMemory(path: String, value: Any?) {
        val segments = path.split('.')
        val root = deepMutable(values)
        var cursor = root
        for (index in 0 until segments.size - 1) {
            val key = segments[index]
            cursor = when (val child = cursor[key]) {
                is MutableMap<*, *> -> {
                    @Suppress("UNCHECKED_CAST")
                    child as MutableMap<String, Any?>
                }

                else -> LinkedHashMap<String, Any?>().also { cursor[key] = it }
            }
        }
        cursor[segments.last()] = value
        values = root
    }

    private fun deepMutable(source: Map<String, Any?>): MutableMap<String, Any?> {
        val out = LinkedHashMap<String, Any?>(source.size)
        source.forEach { (key, value) ->
            out[key] = when (value) {
                is Map<*, *> -> deepMutable(value.entries.associate { it.key.toString() to it.value })
                is List<*> -> value.map { item ->
                    if (item is Map<*, *>) deepMutable(item.entries.associate { it.key.toString() to it.value }) else item
                }

                else -> value
            }
        }
        return out
    }

    private fun string(path: String, default: String = ""): String = node(path)?.toString() ?: default

    private fun boolean(path: String, default: Boolean): Boolean = when (val value = node(path)) {
        is Boolean -> value
        is Number -> value.toInt() != 0
        is String -> value.toBooleanStrictOrNull() ?: default
        else -> default
    }

    private fun integer(path: String, default: Int): Int = when (val value = node(path)) {
        is Number -> value.toInt()
        is String -> value.toIntOrNull() ?: default
        else -> default
    }

    private fun stringList(path: String): List<String> =
        (node(path) as? Iterable<*>)?.mapNotNull { it?.toString() } ?: emptyList()

    private fun node(path: String): Any? {
        var current: Any? = values
        for (part in path.split('.')) {
            current = (current as? Map<*, *>)?.get(part) ?: return null
        }
        return current
    }

    private fun normalizeMap(source: Map<*, *>): Map<String, Any?> = source.entries.associate { (key, value) ->
        key.toString() to when (value) {
            is Map<*, *> -> normalizeMap(value)
            is List<*> -> value.map { item -> if (item is Map<*, *>) normalizeMap(item) else item }
            else -> value
        }
    }

    private companion object {
        /** 这些命令默认不推送到 QQ 指令面板。 */
        val HIDDEN_FROM_MENU = setOf("blockMotd", "unblockMotd")

        /** 宽松布尔解析：兼容 true/false、1/0、on/off、yes/no。 */
        fun toBoolean(value: Any?, default: Boolean = true): Boolean = when (value) {
            is Boolean -> value
            is Number -> value.toInt() != 0
            is String -> value.toBooleanStrictOrNull()
                ?: when (value.trim().lowercase()) {
                    "on", "yes", "y" -> true
                    "off", "no", "n" -> false
                    else -> default
                }

            else -> default
        }
    }
}

/** 背包/末影箱渲染的底图配置。 */
class InventoryRenderConfig(
    val customBackgroundEnabled: Boolean,
    val inventoryFile: String,
    /** 留空表示末影箱复用 [inventoryFile]。 */
    val enderChestFile: String,
    /** cover：等比裁切填满；stretch：拉伸填满。 */
    val fit: String
) {
    fun resolvedEnderChestFile(): String = enderChestFile.ifBlank { inventoryFile }
}
