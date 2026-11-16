package cn.huohuas001.huhobotPenguin.nukkit.events

import cn.huohuas001.bot.state.CommandRepositories
import cn.huohuas001.huhobotPenguin.nukkit.HuHoBotNukkit
import cn.nukkit.event.EventHandler
import cn.nukkit.event.EventPriority
import cn.nukkit.event.Listener
import cn.nukkit.event.player.PlayerJoinEvent
import cn.nukkit.utils.TextFormat

/**
 * 账号锁定守卫（Nukkit 版）。
 *
 * 玩家在 QQ 群执行 `/锁定` 后，对应的游戏账号会被标记为锁定；
 * 之后每次进服都会被立即踢出，直到在 QQ 群执行 `/解除锁定`。
 *
 * 与强制绑定相互独立：强制绑定拦的是「没有绑定 QQ」的玩家，
 * 这里拦的是「绑定者主动锁起来」的玩家，所以 [PlayerJoinEvent] 里只查锁定状态。
 * 免验证名单不豁免：锁定优先级更高。
 */
class AccountLockGuard(private val plugin: HuHoBotNukkit) : Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        if (!CommandRepositories.bindings.isLocked(player.name)) return

        // 下一 tick 再踢，让入服流程走完；理由整段放进踢出画面
        plugin.submitLater(1L) {
            if (player.isOnline) {
                player.kick(buildKickMessage())
                plugin.log_info("账号锁定: 已拒绝 ${player.name} 进入服务器")
            }
        }
    }

    /**
     * 踢出理由。
     *
     * 与强制绑定提示保持同样的分区样式：暗色分隔线 + 红色标题 + 灰色说明。
     */
    fun buildKickMessage(): String = buildString {
        val line = "${TextFormat.DARK_GRAY}${TextFormat.STRIKETHROUGH}${"─".repeat(30)}"
        appendLine(line)
        appendLine("${TextFormat.RED}${TextFormat.BOLD}账号已被锁定")
        appendLine(line)
        appendLine("${TextFormat.GRAY}该游戏账号已被绑定者锁定，暂时无法进入服务器。")
        appendLine()
        appendLine("${TextFormat.GRAY}如需恢复，请在 QQ 群执行 ${TextFormat.GREEN}/解除锁定${TextFormat.GRAY} 后再重新连接。")
        appendLine(line)
    }
}
