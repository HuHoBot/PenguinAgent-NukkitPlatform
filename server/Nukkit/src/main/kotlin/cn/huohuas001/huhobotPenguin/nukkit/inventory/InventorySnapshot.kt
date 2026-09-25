package cn.huohuas001.huhobotPenguin.nukkit.inventory

import cn.nukkit.item.Item

/**
 * 一次背包快照。
 *
 * 槽位语义与 Nukkit 的 `PlayerInventory` 保持一致：
 * - [storage] 下标 0-8 是快捷栏，9-35 是主背包（共 36 格）
 * - [armor] 依次为头盔、胸甲、护腿、靴子
 * - [enderChest] 为 27 格末影箱
 */
class InventorySnapshot(
    val playerName: String,
    val storage: List<Item?>,
    val armor: List<Item?>,
    val offhand: Item?,
    val enderChest: List<Item?>,
    /** 玩家皮肤原始 RGBA 数据（64x64 或 64x32），用于渲染人物预览。 */
    val skinData: ByteArray? = null,
    val skinWidth: Int = 64,
    val skinHeight: Int = 64,
    /** 是否为 Alex 细手臂模型。 */
    val skinSlim: Boolean = false
) {
    fun isEmpty(): Boolean =
        storage.all { isAir(it) } && armor.all { isAir(it) } && isAir(offhand) && enderChest.all { isAir(it) }

    companion object {
        /** Nukkit 用 `Item.isNull()` 表示空槽（空气）。 */
        fun isAir(item: Item?): Boolean = item == null || item.isNull()
    }
}

/** 皮肤模型判定：Bedrock 把细手臂模型写在 geometry 名里。 */
internal object SkinGeometry {

    private const val SLIM_MARKER = "geometry.humanoid.customSlim"

    fun isSlim(geometryData: String?): Boolean =
        geometryData?.contains(SLIM_MARKER, ignoreCase = true) == true
}
