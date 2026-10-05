package cn.huohuas001.huhobotPenguin.nukkit.events

import cn.huohuas001.bot.QClient
import cn.huohuas001.bot.state.CommandRepositories
import cn.huohuas001.bot.state.PendingBindingStore
import cn.huohuas001.huhobotPenguin.nukkit.HuHoBotNukkit
import cn.nukkit.event.EventHandler
import cn.nukkit.event.EventPriority
import cn.nukkit.event.Listener
import cn.nukkit.event.player.PlayerJoinEvent
import cn.nukkit.utils.TextFormat

/**
 * 强制绑定守卫（Nukkit 版）。
 *
 * 开启 `binding.force-bind` 后，未绑定的玩家进服务器会被立刻踢出，
 * 踢出画面里带一个 5 位验证码；玩家在 QQ 群执行 `/绑定 <验证码>`
 * 完成绑定后，重新进服即可正常游玩。
 *
 * 该功能依赖 QQ 机器人可用：若机器人未连接，开启强制绑定会把所有人
 * 挡在门外却无法完成绑定，因此此时会自动把开关改回关闭
 * （见 [HuHoBotNukkit.ensureForceBindAvailable]）。
 */
class ForceBindGuard(private val plugin: HuHoBotNukkit) : Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlayerJoin(event: PlayerJoinEvent) {
        if (!plugin.isForceBindEnabled()) return

        val player = event.player
        // 已绑定（openid 对应的角色名匹配）直接放行
        if (CommandRepositories.bindings.findByPlayerName(player.name) != null) return

        // 机器人不可用时无法完成绑定，不能把玩家挡在门外
        if (QClient.getStarter() == null) {
            plugin.log_warning("QQ 机器人未连接，已临时放行未绑定玩家 ${player.name}")
            return
        }

        val code = PendingBindingStore.issueForceCode(player.name)
        val groups = plugin.getForceBindGroups().filter { it.isNotBlank() }
        val serverName = plugin.getServerName()
        val kickMessage = buildKickMessage(serverName, code, groups)

        // 延后到下一 tick 再踢，让人服流程走完；信息全部放在踢出理由里，不再另外发标题。
        // Nukkit 的 submitLater 单位是 tick（20 tick = 1 秒），与 Bukkit 一致。
        plugin.submitLater(1L) {
            if (player.isOnline) {
                player.kick(kickMessage)
                plugin.log_info("强制绑定: 已踢出未绑定玩家 ${player.name}，验证码 $code")
            }
        }
    }

    /**
     * 踢出理由。
     *
     * 踢出画面是玩家唯一能看到的地方，所以标题、说明、验证码、操作指引全放这里，
     * 用颜色分区：暗色分隔线 + 红色标题 + 灰色说明 + 绿色高亮验证码与命令。
     */
    private fun buildKickMessage(serverName: String, code: String, groups: List<String>): String = buildString {
        val line = TextFormat.DARK_GRAY.toString() + TextFormat.STRIKETHROUGH + "─".repeat(30)
        appendLine(line)
        appendLine("${TextFormat.RED}${TextFormat.BOLD}$serverName - 需要绑定QQ")
        appendLine(line)
        appendLine("${TextFormat.GRAY}此服务器已开启强制绑定，必须绑定QQ号才能够进入游戏。")
        appendLine()
        appendLine("${TextFormat.GRAY}你的绑定验证码：${TextFormat.GREEN}${TextFormat.BOLD}$code")
        appendLine()
        append(buildInstruction(code, groups))
        appendLine()
        appendLine("${TextFormat.DARK_GRAY}${TextFormat.STRIKETHROUGH}${"─".repeat(30)}")
        append("${TextFormat.GRAY}重新进入服务器将刷新验证码")
    }

    /**
     * 操作指引。
     *
     * 群号列表按用户配置展示：单个显示单个，多个用「, 」分隔；未配置则省略群号。
     */
    private fun buildInstruction(code: String, groups: List<String>): String = buildString {
        append("${TextFormat.GRAY}该验证码5分钟内有效。")
        if (groups.isNotEmpty()) {
            append("${TextFormat.GRAY}请在QQ群 ")
            append("${TextFormat.AQUA}${groups.joinToString(", ")}")
            append("${TextFormat.GRAY} 内执行")
        } else {
            append("${TextFormat.GRAY}请在QQ群内执行")
        }
        appendLine()
        append("  ${TextFormat.GREEN}${TextFormat.BOLD}/绑定 $code")
        append("${TextFormat.GRAY} 来绑定QQ并进入游戏")
    }

    /** 供 QQ 侧查询某玩家是否已有强制绑定验证码（调试用）。 */
    fun hasPendingForceCode(playerName: String): Boolean =
        PendingBindingStore.hasForceCodeFor(playerName)
}
