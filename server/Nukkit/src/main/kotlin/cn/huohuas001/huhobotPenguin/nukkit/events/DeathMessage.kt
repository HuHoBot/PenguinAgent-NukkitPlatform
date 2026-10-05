package cn.huohuas001.huhobotPenguin.nukkit.events

import cn.nukkit.event.entity.EntityDamageByEntityEvent
import cn.nukkit.event.entity.EntityDamageEvent
import cn.nukkit.event.player.PlayerDeathEvent
import cn.nukkit.entity.projectile.EntityProjectile

/**
 * 把 Nukkit 死亡事件转成中文播报文案。
 *
 * 原版 [PlayerDeathEvent.getDeathMessage] 取自服务端语言包，在 Bedrock 上
 * 跟随客户端语言，直接转发到 QQ 群可读性差，因此这里按击杀者与死亡原因自行拼装中文描述。
 *
 * 与 Spigot 版的差异：Nukkit 的 [EntityDamageEvent.DamageCause] 枚举值不同
 * （无 POISON/WITHER/STARVATION/FALLING_BLOCK/MELTING/FLY_INTO_WALL，
 * 多了 CONTACT/MAGMA/SUICIDE/CUSTOM/FREEZING），按语义逐条映射。
 */
object DeathMessage {

    /**
     * 击杀者名称：玩家击杀取玩家名，投射物取发射者。
     *
     * @return 环境死亡（摔落、岩浆等）时返回 null
     */
    fun killerOf(event: PlayerDeathEvent): String? {
        val victim = event.entity.name
        val killer = event.entity.killer
        if (killer != null && !killer.name.equals(victim, ignoreCase = true)) {
            return killer.name
        }
        val damager = (event.entity.lastDamageCause as? EntityDamageByEntityEvent)?.damager
        val sourceName = damager?.let { sourceName(it) }
        return sourceName?.takeIf { it.isNotBlank() && !it.equals(victim, ignoreCase = true) }
    }

    /**
     * 中文死亡描述，**不含死者名**，可直接拼在 {message} 里。
     *
     * 例如「被苦力怕杀死了」「从高处摔落而死」。
     */
    fun describe(event: PlayerDeathEvent): String {
        val killer = killerOf(event)
        if (killer != null) return "被${killer}杀死了"

        return when (event.entity.lastDamageCause?.cause) {
            EntityDamageEvent.DamageCause.FALL -> "从高处摔落而死"
            EntityDamageEvent.DamageCause.LAVA -> "掉进了岩浆"
            EntityDamageEvent.DamageCause.FIRE,
            EntityDamageEvent.DamageCause.FIRE_TICK -> "被火烧死了"
            // Nukkit 用 MAGMA（岩浆块）承担 Spigot 侧 MELTING/HOT_FLOOR 的语义
            EntityDamageEvent.DamageCause.MAGMA,
            EntityDamageEvent.DamageCause.HOT_FLOOR -> "被岩浆烫死了"
            EntityDamageEvent.DamageCause.DROWNING -> "溺水而死了"
            EntityDamageEvent.DamageCause.SUFFOCATION -> "窒息而死了"
            EntityDamageEvent.DamageCause.VOID -> "掉进了虚空"
            EntityDamageEvent.DamageCause.LIGHTNING -> "被闪电劈死了"
            EntityDamageEvent.DamageCause.BLOCK_EXPLOSION,
            EntityDamageEvent.DamageCause.ENTITY_EXPLOSION -> "被炸死了"
            EntityDamageEvent.DamageCause.MAGIC -> "中毒而死了"
            EntityDamageEvent.DamageCause.HUNGER -> "饿死了"
            EntityDamageEvent.DamageCause.THORNS -> "被荆棘刺死了"
            EntityDamageEvent.DamageCause.CONTACT -> "碰到了尖刺"
            EntityDamageEvent.DamageCause.FREEZING -> "冻死了"
            // SUICIDE（/kill）、CUSTOM、以及带击杀者但 sourceName 拿不到的情况
            else -> "死了"
        }
    }

    /** 投射物取发射者，其它实体取自己。 */
    private fun sourceName(damager: cn.nukkit.entity.Entity): String? = when (damager) {
        is EntityProjectile -> damager.shootingEntity?.name
        else -> damager.name
    }
}
