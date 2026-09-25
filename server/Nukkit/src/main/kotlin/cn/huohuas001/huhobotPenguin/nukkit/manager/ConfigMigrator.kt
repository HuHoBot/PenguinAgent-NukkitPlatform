package cn.huohuas001.huhobotPenguin.nukkit.manager

import cn.huohuas001.huhobotPenguin.adapter.config.YamlConfig

/**
 * 配置文件版本迁移。
 *
 * 与 Spigot 适配器的 `ConfigManager` 语义一致：补齐全新键、按 `config-version`
 * 覆盖已经过时的默认值。区别是这里**先读旧版本号再补键** ——
 * Spigot 侧把 `config-version` 也放进默认值表并先执行补齐，导致没有版本号的老配置
 * 被直接当成最新版，v6/v7 的迁移被静默跳过。
 */
object ConfigMigrator {

    private const val CURRENT_CONFIG_VERSION = 8
    private const val CONFIG_VERSION_PATH = "config-version"

    /** 应当存在的键与默认值；只补「文件里完全没有」的键，不覆盖用户已填的值。 */
    private val DEFAULT_VALUES: Map<String, Any?> = linkedMapOf(
        "webui-port" to 5678,
        "serverName" to "HuHoBot",
        "bot.name" to "HuHoBot",
        "bot.groups" to emptyList<String>(),
        "bot.suppress-console-output" to true,
        "bot.auto-add-groups" to true,
        "chat-format.from-game" to "[游戏] {name}: {message}",
        "chat-format.from-group" to "[QQ] {name}: {message}",
        "chat-format.post-chat" to true,
        "chat-format.start-with" to "#",
        "player-events.join.enabled" to true,
        "player-events.join.format" to "[游戏] {name} 加入了服务器",
        "player-events.quit.enabled" to true,
        "player-events.quit.format" to "[游戏] {name} 离开了服务器",
        "player-events.always-forward" to false,
        "markdown.queryOnline" to "online.md",
        "command-sender" to "Hybrid",
        "motd.server-ip" to "127.0.0.1",
        "motd.server-port" to 19132,
        "motd.post-img" to true,
        "motd.use-markdown" to true,
        "whitelist.add-command" to "whitelist add {name}",
        "whitelist.del-command" to "whitelist remove {name}",
        "filter-regex" to emptyList<String>(),
        "admin.mode" to "both",
        "admin.openids" to emptyList<String>(),
        "features.full-amount" to false,
        "features.enable-auth" to true,
        "binding.require-game-verification" to false,
        "inventory.render.custom-background.enabled" to false,
        "inventory.render.custom-background.inventory-file" to "inventory.png",
        "inventory.render.custom-background.ender-chest-file" to "",
        "inventory.render.custom-background.fit" to "cover",
        "command-blacklist" to emptyList<String>(),
        "command-panel.show-admin-commands" to true,
        "update-check.enabled" to true,
        "update-check.url" to "",
        "placeholder-api.enabled" to true,
        "audit.base-url" to "",
        "audit.api-key" to "",
        "audit.model" to "gpt-4o-mini",
        "agent.enabled" to false,
        "agent.base-url" to "",
        "agent.api-key" to "",
        "agent.model" to "gpt-4o-mini",
        "agent.command-mode" to "manual",
        "agent.hide-fetch-results" to true,
        "custom-commands" to emptyList<Any>()
    )

    /** 版本化升级：旧版本号 < toVersion 时覆盖对应字段的值。 */
    private val VERSIONED_UPGRADES = listOf(
        VersionedUpgrade(6, mapOf("motd.post-img" to true, "motd.use-markdown" to true)),
        VersionedUpgrade(7, mapOf("webui-port" to 5678))
    )

    fun upgrade(config: YamlConfig, log: (String) -> Unit) {
        // 必须先取旧版本号：补键会写入 config-version，之后再读就拿不到真实的历史版本了。
        val previousVersion = config.configVersion()
        var changed = false

        DEFAULT_VALUES.forEach { (path, value) ->
            if (config.raw(path) == null) {
                config.set(path, value)
                changed = true
            }
        }

        VERSIONED_UPGRADES.forEach { upgrade ->
            if (previousVersion < upgrade.toVersion) {
                upgrade.values.forEach { (path, value) -> config.set(path, value) }
                changed = true
                log("配置迁移：应用 v${upgrade.toVersion} 的默认值变更")
            }
        }

        if (previousVersion != CURRENT_CONFIG_VERSION) {
            config.set(CONFIG_VERSION_PATH, CURRENT_CONFIG_VERSION)
            changed = true
        }

        if (changed) {
            config.save()
            log("配置文件已升级至 v$CURRENT_CONFIG_VERSION（原有注释与自定义值保留）")
        }
    }

    private class VersionedUpgrade(val toVersion: Int, val values: Map<String, Any?>)
}
