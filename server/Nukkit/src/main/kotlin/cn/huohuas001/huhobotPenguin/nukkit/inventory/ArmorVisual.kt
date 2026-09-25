package cn.huohuas001.huhobotPenguin.nukkit.inventory

import cn.nukkit.item.Item
import cn.nukkit.nbt.tag.CompoundTag
import java.util.EnumMap

enum class ArmorSlot { HEAD, CHEST, LEGS, FEET }

data class ArmorVisualDescriptor(
    val slot: ArmorSlot,
    val equipmentModelKey: String,
    val trimPatternKey: String?,
    val trimMaterialKey: String?,
    val leatherColor: Int?,
    val glint: Boolean
) {
    fun hasTrim(): Boolean = trimPatternKey != null && trimMaterialKey != null
    fun hasGlint(): Boolean = glint
}

/**
 * 四件护甲的视觉描述集合。
 *
 * 盔甲「家族」直接由 [NukkitItemTextureTable] 解析出的 Java 贴图名反推
 * （`diamond_helmet` → `diamond`），避免维护第二张 Bedrock → 家族的映射表。
 */
class ArmorEquipmentSet private constructor(
    private val equipment: EnumMap<ArmorSlot, ArmorVisualDescriptor>
) {
    fun get(slot: ArmorSlot): ArmorVisualDescriptor? = equipment[slot]
    fun isEmpty(): Boolean = equipment.isEmpty()

    companion object {
        @JvmStatic
        fun empty(): ArmorEquipmentSet =
            ArmorEquipmentSet(EnumMap<ArmorSlot, ArmorVisualDescriptor>(ArmorSlot::class.java))

        @JvmStatic
        fun from(items: List<Item?>, textureName: (Item) -> String?): ArmorEquipmentSet {
            val resolved = EnumMap<ArmorSlot, ArmorVisualDescriptor>(ArmorSlot::class.java)
            SLOT_ORDER.forEachIndexed { index, slot ->
                ArmorVisualResolver.resolve(items.getOrNull(index), slot, textureName)?.let { resolved[slot] = it }
            }
            return ArmorEquipmentSet(resolved)
        }

        internal val SLOT_ORDER = arrayOf(ArmorSlot.HEAD, ArmorSlot.CHEST, ArmorSlot.LEGS, ArmorSlot.FEET)
    }
}

private object ArmorVisualResolver {

    private val supported = setOf("leather", "chainmail", "iron", "gold", "diamond", "netherite", "turtle_scute", "copper")

    private val suffixes = mapOf(
        ArmorSlot.HEAD to "_helmet",
        ArmorSlot.CHEST to "_chestplate",
        ArmorSlot.LEGS to "_leggings",
        ArmorSlot.FEET to "_boots"
    )

    /** trim 的 pattern/material 必须满足 EquipmentAssetResolver 的 keyPath 校验。 */
    private val SAFE_TRIM_KEY = Regex("[a-z0-9._-]+:[a-z0-9._/-]+")

    fun resolve(item: Item?, slot: ArmorSlot, textureName: (Item) -> String?): ArmorVisualDescriptor? {
        if (InventorySnapshot.isAir(item)) return null
        val target = item ?: return null

        val name = textureName(target)?.lowercase() ?: return null
        if (!name.endsWith(suffixes.getValue(slot))) return null
        val family = when {
            name.startsWith("turtle_") -> "turtle_scute"
            name.startsWith("golden_") -> "gold"
            else -> name.substringBefore('_')
        }
        if (family !in supported) return null

        val tag = target.namedTag
        val trim = trimKeys(tag)
        return ArmorVisualDescriptor(
            slot = slot,
            equipmentModelKey = "minecraft:$family",
            trimPatternKey = trim?.first,
            trimMaterialKey = trim?.second,
            leatherColor = leatherColor(tag),
            glint = target.hasEnchantments()
        )
    }

    private fun leatherColor(tag: CompoundTag?): Int? {
        val source = tag ?: return null
        if (!source.exist("customColor")) return null
        return source.getInt("customColor") and 0xFFFFFF
    }

    /**
     * Bedrock 把盔甲纹饰放在 NBT 的 `Trim` 复合标签里（`Pattern` / `Material`）。
     * 任何不符合 keyPath 期望格式的值都直接丢弃，交由调用方回退到无纹饰渲染。
     */
    private fun trimKeys(tag: CompoundTag?): Pair<String, String>? {
        val source = tag ?: return null
        if (!source.exist("Trim")) return null
        val trim = try {
            source.getCompound("Trim")
        } catch (_: Exception) {
            return null
        }
        val pattern = trim.getString("Pattern").lowercase().substringAfter(':').takeIf { it.isNotBlank() } ?: return null
        val material = trim.getString("Material").lowercase().substringAfter(':').takeIf { it.isNotBlank() } ?: return null

        val patternKey = "minecraft:$pattern"
        val materialKey = "minecraft:$material"
        if (!SAFE_TRIM_KEY.matches(patternKey) || !SAFE_TRIM_KEY.matches(materialKey)) return null
        return patternKey to materialKey
    }
}
