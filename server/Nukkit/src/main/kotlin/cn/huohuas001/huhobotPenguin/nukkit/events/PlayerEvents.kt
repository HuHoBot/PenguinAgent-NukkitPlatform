package cn.huohuas001.huhobotPenguin.nukkit.events

import cn.huohuas001.bot.QClient
import cn.huohuas001.huhobotPenguin.nukkit.HuHoBotNukkit
import cn.nukkit.event.EventHandler
import cn.nukkit.event.Listener
import cn.nukkit.event.player.PlayerChatEvent
import cn.nukkit.event.player.PlayerDeathEvent
import cn.nukkit.event.player.PlayerJoinEvent
import cn.nukkit.event.player.PlayerQuitEvent

/** 游戏内聊天与进退服事件 → QQ 群。 */
class PlayerEvents(private val plugin: HuHoBotNukkit) : Listener {

    @EventHandler(ignoreCancelled = true)
    fun onChat(event: PlayerChatEvent) {
        QClient.broadcastGameMessage(event.player.name, event.message)
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        if (suppressed(event.joinMessage)) return
        QClient.broadcastPlayerJoin(event.player.name)
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        if (suppressed(event.quitMessage)) return
        QClient.broadcastPlayerQuit(event.player.name)
    }

    /** 玩家死亡播报；开关与格式由 player-events.death 配置控制（在 QClient.broadcastPlayerDeath 内判断）。 */
    @EventHandler
    fun onPlayerDeath(event: PlayerDeathEvent) {
        QClient.broadcastPlayerDeath(
            event.entity.name,
            DeathMessage.describe(event),
            DeathMessage.killerOf(event)
        )
    }

    /**
     * 隐藏（vanish）类的插件会把进服/退服消息置空。
     * `player-events.always-forward` 打开时忽略该判断，始终转发。
     */
    private fun suppressed(message: Any?): Boolean =
        !plugin.getPlayerEventFormat().alwaysForward && message == null
}
