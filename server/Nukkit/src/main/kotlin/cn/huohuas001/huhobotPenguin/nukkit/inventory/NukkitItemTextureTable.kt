package cn.huohuas001.huhobotPenguin.nukkit.inventory

import cn.nukkit.block.BlockID
import cn.nukkit.item.ItemID

/**
 * Bedrock (Nukkit) item -> Java Edition texture base name, for the Java2D inventory renderer.
 *
 * A texture name resolves against
 * `inventory/faithful32x/special-variants/minecraft/<name>.png`,
 * `.../overrides/items/minecraft/<name>.png` and
 * `.../assets/minecraft/<name>.png` (a solid block may instead be composed from
 * `<name>_top.png` + `<name>_side.png`/`<name>_front.png`). Every value below was
 * checked to resolve through one of those paths - see TEXTURE_MAP_NOTES.md.
 *
 * `BY_ID_META` is keyed by `key(id, meta)`; `BY_NAMESPACE` is keyed by the Bedrock
 * namespace/legacy identifier string and only lists names that differ from Java's.
 *
 * Look both maps up with `Item.getId()` / `Item.getDamage()`.
 *
 * NOTE ON BLOCK IDS ABOVE 255: `Item.getId()` lives in the *item* id space, where a block
 * with an id above 255 is addressed as `255 - blockId` (`Block.getItemId()` /
 * `Item.getBlockItem()`). That is why entries such as `key(255 - BlockID.CORAL, 0)` appear
 * below: `key(BlockID.CORAL, 0)` would never match a real inventory stack, because ids 256+
 * are also the legacy *item* ids (e.g. block 386 is coral, item 386 is a book and quill).
 */
object NukkitItemTextureTable {

    /** `key(id, meta)` -> Java Edition texture base name (no extension). */
    val BY_ID_META: Map<Long, String> = mapOf(
        // ---- wool ----
        key(BlockID.WOOL, 0) to "white_wool",
        key(BlockID.WOOL, 1) to "orange_wool",
        key(BlockID.WOOL, 2) to "magenta_wool",
        key(BlockID.WOOL, 3) to "light_blue_wool",
        key(BlockID.WOOL, 4) to "yellow_wool",
        key(BlockID.WOOL, 5) to "lime_wool",
        key(BlockID.WOOL, 6) to "pink_wool",
        key(BlockID.WOOL, 7) to "gray_wool",
        key(BlockID.WOOL, 8) to "light_gray_wool",
        key(BlockID.WOOL, 9) to "cyan_wool",
        key(BlockID.WOOL, 10) to "purple_wool",
        key(BlockID.WOOL, 11) to "blue_wool",
        key(BlockID.WOOL, 12) to "brown_wool",
        key(BlockID.WOOL, 13) to "green_wool",
        key(BlockID.WOOL, 14) to "red_wool",
        key(BlockID.WOOL, 15) to "black_wool",
        // ---- carpet ----
        key(BlockID.CARPET, 0) to "white_wool", // Java <colour>_carpet has no PNG; vanilla's carpet model samples the wool texture
        key(BlockID.CARPET, 1) to "orange_wool", // Java <colour>_carpet has no PNG; vanilla's carpet model samples the wool texture
        key(BlockID.CARPET, 2) to "magenta_wool", // Java <colour>_carpet has no PNG; vanilla's carpet model samples the wool texture
        key(BlockID.CARPET, 3) to "light_blue_wool", // Java <colour>_carpet has no PNG; vanilla's carpet model samples the wool texture
        key(BlockID.CARPET, 4) to "yellow_wool", // Java <colour>_carpet has no PNG; vanilla's carpet model samples the wool texture
        key(BlockID.CARPET, 5) to "lime_wool", // Java <colour>_carpet has no PNG; vanilla's carpet model samples the wool texture
        key(BlockID.CARPET, 6) to "pink_wool", // Java <colour>_carpet has no PNG; vanilla's carpet model samples the wool texture
        key(BlockID.CARPET, 7) to "gray_wool", // Java <colour>_carpet has no PNG; vanilla's carpet model samples the wool texture
        key(BlockID.CARPET, 8) to "light_gray_wool", // Java <colour>_carpet has no PNG; vanilla's carpet model samples the wool texture
        key(BlockID.CARPET, 9) to "cyan_wool", // Java <colour>_carpet has no PNG; vanilla's carpet model samples the wool texture
        key(BlockID.CARPET, 10) to "purple_wool", // Java <colour>_carpet has no PNG; vanilla's carpet model samples the wool texture
        key(BlockID.CARPET, 11) to "blue_wool", // Java <colour>_carpet has no PNG; vanilla's carpet model samples the wool texture
        key(BlockID.CARPET, 12) to "brown_wool", // Java <colour>_carpet has no PNG; vanilla's carpet model samples the wool texture
        key(BlockID.CARPET, 13) to "green_wool", // Java <colour>_carpet has no PNG; vanilla's carpet model samples the wool texture
        key(BlockID.CARPET, 14) to "red_wool", // Java <colour>_carpet has no PNG; vanilla's carpet model samples the wool texture
        key(BlockID.CARPET, 15) to "black_wool", // Java <colour>_carpet has no PNG; vanilla's carpet model samples the wool texture
        // ---- concrete ----
        key(BlockID.CONCRETE, 0) to "white_concrete",
        key(BlockID.CONCRETE, 1) to "orange_concrete",
        key(BlockID.CONCRETE, 2) to "magenta_concrete",
        key(BlockID.CONCRETE, 3) to "light_blue_concrete",
        key(BlockID.CONCRETE, 4) to "yellow_concrete",
        key(BlockID.CONCRETE, 5) to "lime_concrete",
        key(BlockID.CONCRETE, 6) to "pink_concrete",
        key(BlockID.CONCRETE, 7) to "gray_concrete",
        key(BlockID.CONCRETE, 8) to "light_gray_concrete",
        key(BlockID.CONCRETE, 9) to "cyan_concrete",
        key(BlockID.CONCRETE, 10) to "purple_concrete",
        key(BlockID.CONCRETE, 11) to "blue_concrete",
        key(BlockID.CONCRETE, 12) to "brown_concrete",
        key(BlockID.CONCRETE, 13) to "green_concrete",
        key(BlockID.CONCRETE, 14) to "red_concrete",
        key(BlockID.CONCRETE, 15) to "black_concrete",
        // ---- concrete powder ----
        key(BlockID.CONCRETE_POWDER, 0) to "white_concrete_powder",
        key(BlockID.CONCRETE_POWDER, 1) to "orange_concrete_powder",
        key(BlockID.CONCRETE_POWDER, 2) to "magenta_concrete_powder",
        key(BlockID.CONCRETE_POWDER, 3) to "light_blue_concrete_powder",
        key(BlockID.CONCRETE_POWDER, 4) to "yellow_concrete_powder",
        key(BlockID.CONCRETE_POWDER, 5) to "lime_concrete_powder",
        key(BlockID.CONCRETE_POWDER, 6) to "pink_concrete_powder",
        key(BlockID.CONCRETE_POWDER, 7) to "gray_concrete_powder",
        key(BlockID.CONCRETE_POWDER, 8) to "light_gray_concrete_powder",
        key(BlockID.CONCRETE_POWDER, 9) to "cyan_concrete_powder",
        key(BlockID.CONCRETE_POWDER, 10) to "purple_concrete_powder",
        key(BlockID.CONCRETE_POWDER, 11) to "blue_concrete_powder",
        key(BlockID.CONCRETE_POWDER, 12) to "brown_concrete_powder",
        key(BlockID.CONCRETE_POWDER, 13) to "green_concrete_powder",
        key(BlockID.CONCRETE_POWDER, 14) to "red_concrete_powder",
        key(BlockID.CONCRETE_POWDER, 15) to "black_concrete_powder",
        // ---- stained glass ----
        key(BlockID.STAINED_GLASS, 0) to "white_stained_glass",
        key(BlockID.STAINED_GLASS, 1) to "orange_stained_glass",
        key(BlockID.STAINED_GLASS, 2) to "magenta_stained_glass",
        key(BlockID.STAINED_GLASS, 3) to "light_blue_stained_glass",
        key(BlockID.STAINED_GLASS, 4) to "yellow_stained_glass",
        key(BlockID.STAINED_GLASS, 5) to "lime_stained_glass",
        key(BlockID.STAINED_GLASS, 6) to "pink_stained_glass",
        key(BlockID.STAINED_GLASS, 7) to "gray_stained_glass",
        key(BlockID.STAINED_GLASS, 8) to "light_gray_stained_glass",
        key(BlockID.STAINED_GLASS, 9) to "cyan_stained_glass",
        key(BlockID.STAINED_GLASS, 10) to "purple_stained_glass",
        key(BlockID.STAINED_GLASS, 11) to "blue_stained_glass",
        key(BlockID.STAINED_GLASS, 12) to "brown_stained_glass",
        key(BlockID.STAINED_GLASS, 13) to "green_stained_glass",
        key(BlockID.STAINED_GLASS, 14) to "red_stained_glass",
        key(BlockID.STAINED_GLASS, 15) to "black_stained_glass",
        // ---- stained glass pane ----
        key(BlockID.STAINED_GLASS_PANE, 0) to "white_stained_glass", // Java <colour>_stained_glass_pane has no PNG; vanilla's pane model samples the glass texture
        key(BlockID.STAINED_GLASS_PANE, 1) to "orange_stained_glass", // Java <colour>_stained_glass_pane has no PNG; vanilla's pane model samples the glass texture
        key(BlockID.STAINED_GLASS_PANE, 2) to "magenta_stained_glass", // Java <colour>_stained_glass_pane has no PNG; vanilla's pane model samples the glass texture
        key(BlockID.STAINED_GLASS_PANE, 3) to "light_blue_stained_glass", // Java <colour>_stained_glass_pane has no PNG; vanilla's pane model samples the glass texture
        key(BlockID.STAINED_GLASS_PANE, 4) to "yellow_stained_glass", // Java <colour>_stained_glass_pane has no PNG; vanilla's pane model samples the glass texture
        key(BlockID.STAINED_GLASS_PANE, 5) to "lime_stained_glass", // Java <colour>_stained_glass_pane has no PNG; vanilla's pane model samples the glass texture
        key(BlockID.STAINED_GLASS_PANE, 6) to "pink_stained_glass", // Java <colour>_stained_glass_pane has no PNG; vanilla's pane model samples the glass texture
        key(BlockID.STAINED_GLASS_PANE, 7) to "gray_stained_glass", // Java <colour>_stained_glass_pane has no PNG; vanilla's pane model samples the glass texture
        key(BlockID.STAINED_GLASS_PANE, 8) to "light_gray_stained_glass", // Java <colour>_stained_glass_pane has no PNG; vanilla's pane model samples the glass texture
        key(BlockID.STAINED_GLASS_PANE, 9) to "cyan_stained_glass", // Java <colour>_stained_glass_pane has no PNG; vanilla's pane model samples the glass texture
        key(BlockID.STAINED_GLASS_PANE, 10) to "purple_stained_glass", // Java <colour>_stained_glass_pane has no PNG; vanilla's pane model samples the glass texture
        key(BlockID.STAINED_GLASS_PANE, 11) to "blue_stained_glass", // Java <colour>_stained_glass_pane has no PNG; vanilla's pane model samples the glass texture
        key(BlockID.STAINED_GLASS_PANE, 12) to "brown_stained_glass", // Java <colour>_stained_glass_pane has no PNG; vanilla's pane model samples the glass texture
        key(BlockID.STAINED_GLASS_PANE, 13) to "green_stained_glass", // Java <colour>_stained_glass_pane has no PNG; vanilla's pane model samples the glass texture
        key(BlockID.STAINED_GLASS_PANE, 14) to "red_stained_glass", // Java <colour>_stained_glass_pane has no PNG; vanilla's pane model samples the glass texture
        key(BlockID.STAINED_GLASS_PANE, 15) to "black_stained_glass", // Java <colour>_stained_glass_pane has no PNG; vanilla's pane model samples the glass texture
        // ---- stained hardened clay / terracotta ----
        key(BlockID.STAINED_TERRACOTTA, 0) to "white_terracotta",
        key(BlockID.STAINED_TERRACOTTA, 1) to "orange_terracotta",
        key(BlockID.STAINED_TERRACOTTA, 2) to "magenta_terracotta",
        key(BlockID.STAINED_TERRACOTTA, 3) to "light_blue_terracotta",
        key(BlockID.STAINED_TERRACOTTA, 4) to "yellow_terracotta",
        key(BlockID.STAINED_TERRACOTTA, 5) to "lime_terracotta",
        key(BlockID.STAINED_TERRACOTTA, 6) to "pink_terracotta",
        key(BlockID.STAINED_TERRACOTTA, 7) to "gray_terracotta",
        key(BlockID.STAINED_TERRACOTTA, 8) to "light_gray_terracotta",
        key(BlockID.STAINED_TERRACOTTA, 9) to "cyan_terracotta",
        key(BlockID.STAINED_TERRACOTTA, 10) to "purple_terracotta",
        key(BlockID.STAINED_TERRACOTTA, 11) to "blue_terracotta",
        key(BlockID.STAINED_TERRACOTTA, 12) to "brown_terracotta",
        key(BlockID.STAINED_TERRACOTTA, 13) to "green_terracotta",
        key(BlockID.STAINED_TERRACOTTA, 14) to "red_terracotta",
        key(BlockID.STAINED_TERRACOTTA, 15) to "black_terracotta",
        // ---- terracotta ----
        key(BlockID.TERRACOTTA, 0) to "terracotta",
        // ---- glazed terracotta ----
        key(BlockID.WHITE_GLAZED_TERRACOTTA, 0) to "white_glazed_terracotta",
        key(BlockID.ORANGE_GLAZED_TERRACOTTA, 0) to "orange_glazed_terracotta",
        key(BlockID.MAGENTA_GLAZED_TERRACOTTA, 0) to "magenta_glazed_terracotta",
        key(BlockID.LIGHT_BLUE_GLAZED_TERRACOTTA, 0) to "light_blue_glazed_terracotta",
        key(BlockID.YELLOW_GLAZED_TERRACOTTA, 0) to "yellow_glazed_terracotta",
        key(BlockID.LIME_GLAZED_TERRACOTTA, 0) to "lime_glazed_terracotta",
        key(BlockID.PINK_GLAZED_TERRACOTTA, 0) to "pink_glazed_terracotta",
        key(BlockID.GRAY_GLAZED_TERRACOTTA, 0) to "gray_glazed_terracotta",
        key(BlockID.SILVER_GLAZED_TERRACOTTA, 0) to "light_gray_glazed_terracotta", // Bedrock calls this colour 'silver'
        key(BlockID.CYAN_GLAZED_TERRACOTTA, 0) to "cyan_glazed_terracotta",
        key(BlockID.PURPLE_GLAZED_TERRACOTTA, 0) to "purple_glazed_terracotta",
        key(BlockID.BLUE_GLAZED_TERRACOTTA, 0) to "blue_glazed_terracotta",
        key(BlockID.BROWN_GLAZED_TERRACOTTA, 0) to "brown_glazed_terracotta",
        key(BlockID.GREEN_GLAZED_TERRACOTTA, 0) to "green_glazed_terracotta",
        key(BlockID.RED_GLAZED_TERRACOTTA, 0) to "red_glazed_terracotta",
        key(BlockID.BLACK_GLAZED_TERRACOTTA, 0) to "black_glazed_terracotta",
        // ---- shulker box ----
        key(BlockID.SHULKER_BOX, 0) to "white_shulker_box",
        key(BlockID.SHULKER_BOX, 1) to "orange_shulker_box",
        key(BlockID.SHULKER_BOX, 2) to "magenta_shulker_box",
        key(BlockID.SHULKER_BOX, 3) to "light_blue_shulker_box",
        key(BlockID.SHULKER_BOX, 4) to "yellow_shulker_box",
        key(BlockID.SHULKER_BOX, 5) to "lime_shulker_box",
        key(BlockID.SHULKER_BOX, 6) to "pink_shulker_box",
        key(BlockID.SHULKER_BOX, 7) to "gray_shulker_box",
        key(BlockID.SHULKER_BOX, 8) to "light_gray_shulker_box",
        key(BlockID.SHULKER_BOX, 9) to "cyan_shulker_box",
        key(BlockID.SHULKER_BOX, 10) to "purple_shulker_box",
        key(BlockID.SHULKER_BOX, 11) to "blue_shulker_box",
        key(BlockID.SHULKER_BOX, 12) to "brown_shulker_box",
        key(BlockID.SHULKER_BOX, 13) to "green_shulker_box",
        key(BlockID.SHULKER_BOX, 14) to "red_shulker_box",
        key(BlockID.SHULKER_BOX, 15) to "black_shulker_box",
        key(BlockID.UNDYED_SHULKER_BOX, 0) to "shulker_box", // undyed shulker box
        // ---- bed ----
        key(ItemID.BED, 0) to "white_bed",
        key(ItemID.BED, 1) to "orange_bed",
        key(ItemID.BED, 2) to "magenta_bed",
        key(ItemID.BED, 3) to "light_blue_bed",
        key(ItemID.BED, 4) to "yellow_bed",
        key(ItemID.BED, 5) to "lime_bed",
        key(ItemID.BED, 6) to "pink_bed",
        key(ItemID.BED, 7) to "gray_bed",
        key(ItemID.BED, 8) to "light_gray_bed",
        key(ItemID.BED, 9) to "cyan_bed",
        key(ItemID.BED, 10) to "purple_bed",
        key(ItemID.BED, 11) to "blue_bed",
        key(ItemID.BED, 12) to "brown_bed",
        key(ItemID.BED, 13) to "green_bed",
        key(ItemID.BED, 14) to "red_bed",
        key(ItemID.BED, 15) to "black_bed",
        key(BlockID.BED_BLOCK, 0) to "white_bed", // bed block, same 16 colours
        key(BlockID.BED_BLOCK, 1) to "orange_bed", // bed block, same 16 colours
        key(BlockID.BED_BLOCK, 2) to "magenta_bed", // bed block, same 16 colours
        key(BlockID.BED_BLOCK, 3) to "light_blue_bed", // bed block, same 16 colours
        key(BlockID.BED_BLOCK, 4) to "yellow_bed", // bed block, same 16 colours
        key(BlockID.BED_BLOCK, 5) to "lime_bed", // bed block, same 16 colours
        key(BlockID.BED_BLOCK, 6) to "pink_bed", // bed block, same 16 colours
        key(BlockID.BED_BLOCK, 7) to "gray_bed", // bed block, same 16 colours
        key(BlockID.BED_BLOCK, 8) to "light_gray_bed", // bed block, same 16 colours
        key(BlockID.BED_BLOCK, 9) to "cyan_bed", // bed block, same 16 colours
        key(BlockID.BED_BLOCK, 10) to "purple_bed", // bed block, same 16 colours
        key(BlockID.BED_BLOCK, 11) to "blue_bed", // bed block, same 16 colours
        key(BlockID.BED_BLOCK, 12) to "brown_bed", // bed block, same 16 colours
        key(BlockID.BED_BLOCK, 13) to "green_bed", // bed block, same 16 colours
        key(BlockID.BED_BLOCK, 14) to "red_bed", // bed block, same 16 colours
        key(BlockID.BED_BLOCK, 15) to "black_bed", // bed block, same 16 colours
        // ---- candle ----
        key(255 - BlockID.CANDLE, 0) to "candle",
        key(255 - BlockID.WHITE_CANDLE, 0) to "white_candle",
        key(255 - BlockID.ORANGE_CANDLE, 0) to "orange_candle",
        key(255 - BlockID.MAGENTA_CANDLE, 0) to "magenta_candle",
        key(255 - BlockID.LIGHT_BLUE_CANDLE, 0) to "light_blue_candle",
        key(255 - BlockID.YELLOW_CANDLE, 0) to "yellow_candle",
        key(255 - BlockID.LIME_CANDLE, 0) to "lime_candle",
        key(255 - BlockID.PINK_CANDLE, 0) to "pink_candle",
        key(255 - BlockID.GRAY_CANDLE, 0) to "gray_candle",
        key(255 - BlockID.LIGHT_GRAY_CANDLE, 0) to "light_gray_candle",
        key(255 - BlockID.CYAN_CANDLE, 0) to "cyan_candle",
        key(255 - BlockID.PURPLE_CANDLE, 0) to "purple_candle",
        key(255 - BlockID.BLUE_CANDLE, 0) to "blue_candle",
        key(255 - BlockID.BROWN_CANDLE, 0) to "brown_candle",
        key(255 - BlockID.GREEN_CANDLE, 0) to "green_candle",
        key(255 - BlockID.RED_CANDLE, 0) to "red_candle",
        key(255 - BlockID.BLACK_CANDLE, 0) to "black_candle",
        // ---- nether brick ----
        key(ItemID.NETHER_BRICK, 0) to "nether_brick",
        // ---- chain ----
        key(ItemID.CHAIN, 0) to "iron_chain", // Java 1.21.9 renamed chain -> iron_chain
        // ---- dye ----
        key(ItemID.DYE, 0) to "ink_sac",
        key(ItemID.DYE, 1) to "red_dye",
        key(ItemID.DYE, 2) to "green_dye",
        key(ItemID.DYE, 3) to "cocoa_beans",
        key(ItemID.DYE, 4) to "lapis_lazuli",
        key(ItemID.DYE, 5) to "purple_dye",
        key(ItemID.DYE, 6) to "cyan_dye",
        key(ItemID.DYE, 7) to "light_gray_dye",
        key(ItemID.DYE, 8) to "gray_dye",
        key(ItemID.DYE, 9) to "pink_dye",
        key(ItemID.DYE, 10) to "lime_dye",
        key(ItemID.DYE, 11) to "yellow_dye",
        key(ItemID.DYE, 12) to "light_blue_dye",
        key(ItemID.DYE, 13) to "magenta_dye",
        key(ItemID.DYE, 14) to "orange_dye",
        key(ItemID.DYE, 15) to "bone_meal",
        key(ItemID.DYE, 16) to "black_dye",
        key(ItemID.DYE, 17) to "brown_dye",
        key(ItemID.DYE, 18) to "blue_dye",
        key(ItemID.DYE, 19) to "white_dye",
        key(ItemID.DYE, 20) to "glow_ink_sac",
        // ---- planks ----
        key(BlockID.PLANKS, 0) to "oak_planks",
        key(BlockID.PLANKS, 1) to "spruce_planks",
        key(BlockID.PLANKS, 2) to "birch_planks",
        key(BlockID.PLANKS, 3) to "jungle_planks",
        key(BlockID.PLANKS, 4) to "acacia_planks",
        key(BlockID.PLANKS, 5) to "dark_oak_planks",
        // ---- log ----
        key(BlockID.LOG, 0) to "oak_log",
        key(BlockID.LOG, 1) to "spruce_log",
        key(BlockID.LOG, 2) to "birch_log",
        key(BlockID.LOG, 3) to "jungle_log",
        key(BlockID.LOG, 12) to "oak_log", // bark on all six sides; Java <wood>_wood has no PNG, same texture as the log side
        key(BlockID.LOG, 13) to "spruce_log", // bark on all six sides; Java <wood>_wood has no PNG, same texture as the log side
        key(BlockID.LOG, 14) to "birch_log", // bark on all six sides; Java <wood>_wood has no PNG, same texture as the log side
        key(BlockID.LOG, 15) to "jungle_log", // bark on all six sides; Java <wood>_wood has no PNG, same texture as the log side
        // ---- log2 ----
        key(BlockID.LOG2, 0) to "acacia_log",
        key(BlockID.LOG2, 1) to "dark_oak_log",
        key(BlockID.LOG2, 12) to "acacia_log", // bark on all six sides, see log
        key(BlockID.LOG2, 13) to "dark_oak_log", // bark on all six sides, see log
        // ---- leaves ----
        key(BlockID.LEAVES, 0) to "oak_leaves",
        key(BlockID.LEAVES, 1) to "spruce_leaves",
        key(BlockID.LEAVES, 2) to "birch_leaves",
        key(BlockID.LEAVES, 3) to "jungle_leaves",
        key(BlockID.LEAVES2, 0) to "acacia_leaves",
        key(BlockID.LEAVES2, 1) to "dark_oak_leaves",
        // ---- sapling ----
        key(BlockID.SAPLING, 0) to "oak_sapling",
        key(BlockID.SAPLING, 1) to "spruce_sapling",
        key(BlockID.SAPLING, 2) to "birch_sapling",
        key(BlockID.SAPLING, 3) to "jungle_sapling",
        key(BlockID.SAPLING, 4) to "acacia_sapling",
        key(BlockID.SAPLING, 5) to "dark_oak_sapling",
        // ---- wooden slab ----
        key(BlockID.WOOD_SLAB, 0) to "oak_planks", // Java <wood>_slab has no PNG
        key(BlockID.WOOD_SLAB, 1) to "spruce_planks", // Java <wood>_slab has no PNG
        key(BlockID.WOOD_SLAB, 2) to "birch_planks", // Java <wood>_slab has no PNG
        key(BlockID.WOOD_SLAB, 3) to "jungle_planks", // Java <wood>_slab has no PNG
        key(BlockID.WOOD_SLAB, 4) to "acacia_planks", // Java <wood>_slab has no PNG
        key(BlockID.WOOD_SLAB, 5) to "dark_oak_planks", // Java <wood>_slab has no PNG
        // ---- fence ----
        key(BlockID.FENCE, 0) to "oak_planks", // Java <wood>_fence has no PNG
        key(BlockID.FENCE, 1) to "spruce_planks", // Java <wood>_fence has no PNG
        key(BlockID.FENCE, 2) to "birch_planks", // Java <wood>_fence has no PNG
        key(BlockID.FENCE, 3) to "jungle_planks", // Java <wood>_fence has no PNG
        key(BlockID.FENCE, 4) to "acacia_planks", // Java <wood>_fence has no PNG
        key(BlockID.FENCE, 5) to "dark_oak_planks", // Java <wood>_fence has no PNG
        // ---- fence gate ----
        key(BlockID.FENCE_GATE, 0) to "oak_planks", // Java oak_fence_gate has no PNG
        key(BlockID.FENCE_GATE_SPRUCE, 0) to "spruce_planks", // Java spruce_fence_gate has no PNG
        key(BlockID.FENCE_GATE_BIRCH, 0) to "birch_planks", // Java birch_fence_gate has no PNG
        key(BlockID.FENCE_GATE_JUNGLE, 0) to "jungle_planks", // Java jungle_fence_gate has no PNG
        key(BlockID.FENCE_GATE_DARK_OAK, 0) to "dark_oak_planks", // Java dark_oak_fence_gate has no PNG
        key(BlockID.FENCE_GATE_ACACIA, 0) to "acacia_planks", // Java acacia_fence_gate has no PNG
        // ---- door ----
        key(ItemID.WOODEN_DOOR, 0) to "oak_door",
        key(ItemID.SPRUCE_DOOR, 0) to "spruce_door",
        key(ItemID.BIRCH_DOOR, 0) to "birch_door",
        key(ItemID.JUNGLE_DOOR, 0) to "jungle_door",
        key(ItemID.ACACIA_DOOR, 0) to "acacia_door",
        key(ItemID.DARK_OAK_DOOR, 0) to "dark_oak_door",
        // ---- trapdoor ----
        key(BlockID.TRAPDOOR, 0) to "oak_trapdoor",
        key(BlockID.IRON_TRAPDOOR, 0) to "iron_trapdoor",
        key(255 - BlockID.SPRUCE_TRAPDOOR, 0) to "spruce_trapdoor",
        key(255 - BlockID.BIRCH_TRAPDOOR, 0) to "birch_trapdoor",
        key(255 - BlockID.JUNGLE_TRAPDOOR, 0) to "jungle_trapdoor",
        key(255 - BlockID.ACACIA_TRAPDOOR, 0) to "acacia_trapdoor",
        key(255 - BlockID.DARK_OAK_TRAPDOOR, 0) to "dark_oak_trapdoor",
        // ---- button ----
        key(BlockID.WOODEN_BUTTON, 0) to "oak_planks", // Java oak_button has no PNG
        key(255 - BlockID.SPRUCE_BUTTON, 0) to "spruce_planks", // Java spruce_button has no PNG
        key(255 - BlockID.BIRCH_BUTTON, 0) to "birch_planks", // Java birch_button has no PNG
        key(255 - BlockID.JUNGLE_BUTTON, 0) to "jungle_planks", // Java jungle_button has no PNG
        key(255 - BlockID.ACACIA_BUTTON, 0) to "acacia_planks", // Java acacia_button has no PNG
        key(255 - BlockID.DARK_OAK_BUTTON, 0) to "dark_oak_planks", // Java dark_oak_button has no PNG
        // ---- pressure plate ----
        key(BlockID.WOODEN_PRESSURE_PLATE, 0) to "oak_planks", // Java oak_pressure_plate has no PNG
        key(255 - BlockID.SPRUCE_PRESSURE_PLATE, 0) to "spruce_planks", // Java spruce_pressure_plate has no PNG
        key(255 - BlockID.BIRCH_PRESSURE_PLATE, 0) to "birch_planks", // Java birch_pressure_plate has no PNG
        key(255 - BlockID.JUNGLE_PRESSURE_PLATE, 0) to "jungle_planks", // Java jungle_pressure_plate has no PNG
        key(255 - BlockID.ACACIA_PRESSURE_PLATE, 0) to "acacia_planks", // Java acacia_pressure_plate has no PNG
        key(255 - BlockID.DARK_OAK_PRESSURE_PLATE, 0) to "dark_oak_planks", // Java dark_oak_pressure_plate has no PNG
        // ---- sign ----
        key(BlockID.SIGN_POST, 0) to "oak_sign",
        key(BlockID.WALL_SIGN, 0) to "oak_sign",
        key(ItemID.SIGN, 0) to "oak_sign",
        key(ItemID.SPRUCE_SIGN, 0) to "spruce_sign",
        key(ItemID.BIRCH_SIGN, 0) to "birch_sign",
        key(ItemID.JUNGLE_SIGN, 0) to "jungle_sign",
        key(ItemID.ACACIA_SIGN, 0) to "acacia_sign",
        key(ItemID.DARKOAK_SIGN, 0) to "dark_oak_sign",
        // ---- stairs ----
        key(BlockID.WOOD_STAIRS, 0) to "oak_planks",
        key(BlockID.SPRUCE_WOOD_STAIRS, 0) to "spruce_planks",
        key(BlockID.BIRCH_WOOD_STAIRS, 0) to "birch_planks",
        key(BlockID.JUNGLE_WOOD_STAIRS, 0) to "jungle_planks",
        key(BlockID.ACACIA_WOOD_STAIRS, 0) to "acacia_planks",
        key(BlockID.DARK_OAK_WOOD_STAIRS, 0) to "dark_oak_planks",
        key(BlockID.COBBLE_STAIRS, 0) to "cobblestone",
        key(BlockID.BRICK_STAIRS, 0) to "bricks",
        key(BlockID.STONE_BRICK_STAIRS, 0) to "stone_bricks",
        key(BlockID.NETHER_BRICKS_STAIRS, 0) to "nether_bricks",
        key(BlockID.SANDSTONE_STAIRS, 0) to "sandstone",
        key(BlockID.QUARTZ_STAIRS, 0) to "quartz_block",
        key(BlockID.RED_SANDSTONE_STAIRS, 0) to "red_sandstone",
        key(BlockID.PURPUR_STAIRS, 0) to "purpur_block",
        key(255 - BlockID.PRISMARINE_STAIRS, 0) to "prismarine",
        key(255 - BlockID.DARK_PRISMARINE_STAIRS, 0) to "dark_prismarine",
        key(255 - BlockID.PRISMARINE_BRICKS_STAIRS, 0) to "prismarine_bricks",
        key(255 - BlockID.GRANITE_STAIRS, 0) to "granite",
        key(255 - BlockID.DIORITE_STAIRS, 0) to "diorite",
        key(255 - BlockID.ANDESITE_STAIRS, 0) to "andesite",
        key(255 - BlockID.POLISHED_GRANITE_STAIRS, 0) to "polished_granite",
        key(255 - BlockID.POLISHED_DIORITE_STAIRS, 0) to "polished_diorite",
        key(255 - BlockID.POLISHED_ANDESITE_STAIRS, 0) to "polished_andesite",
        key(255 - BlockID.MOSSY_STONE_BRICK_STAIRS, 0) to "mossy_stone_bricks",
        key(255 - BlockID.SMOOTH_RED_SANDSTONE_STAIRS, 0) to "red_sandstone_top", // smooth red sandstone
        key(255 - BlockID.SMOOTH_SANDSTONE_STAIRS, 0) to "sandstone_top", // smooth sandstone
        key(255 - BlockID.END_BRICK_STAIRS, 0) to "end_stone_bricks",
        key(255 - BlockID.MOSSY_COBBLESTONE_STAIRS, 0) to "mossy_cobblestone",
        key(255 - BlockID.NORMAL_STONE_STAIRS, 0) to "stone",
        key(255 - BlockID.RED_NETHER_BRICK_STAIRS, 0) to "red_nether_bricks",
        key(255 - BlockID.SMOOTH_QUARTZ_STAIRS, 0) to "quartz_block_bottom", // smooth quartz
        key(255 - BlockID.CRIMSON_STAIRS, 0) to "crimson_planks",
        key(255 - BlockID.WARPED_STAIRS, 0) to "warped_planks",
        key(255 - BlockID.BLACKSTONE_STAIRS, 0) to "blackstone",
        key(255 - BlockID.POLISHED_BLACKSTONE_BRICK_STAIRS, 0) to "polished_blackstone_bricks",
        key(255 - BlockID.POLISHED_BLACKSTONE_STAIRS, 0) to "polished_blackstone",
        key(255 - BlockID.COBBLED_DEEPSLATE_STAIRS, 0) to "cobbled_deepslate",
        key(255 - BlockID.POLISHED_DEEPSLATE_STAIRS, 0) to "polished_deepslate",
        key(255 - BlockID.DEEPSLATE_TILE_STAIRS, 0) to "deepslate_tiles",
        key(255 - BlockID.DEEPSLATE_BRICK_STAIRS, 0) to "deepslate_bricks",
        // ---- stone variants ----
        key(BlockID.STONE, 0) to "stone",
        key(BlockID.STONE, 1) to "granite",
        key(BlockID.STONE, 2) to "polished_granite",
        key(BlockID.STONE, 3) to "diorite",
        key(BlockID.STONE, 4) to "polished_diorite",
        key(BlockID.STONE, 5) to "andesite",
        key(BlockID.STONE, 6) to "polished_andesite",
        // ---- stone bricks ----
        key(BlockID.STONE_BRICKS, 0) to "stone_bricks",
        key(BlockID.STONE_BRICKS, 1) to "mossy_stone_bricks",
        key(BlockID.STONE_BRICKS, 2) to "cracked_stone_bricks",
        key(BlockID.STONE_BRICKS, 3) to "chiseled_stone_bricks",
        // ---- sandstone ----
        key(BlockID.SANDSTONE, 0) to "sandstone",
        key(BlockID.SANDSTONE, 1) to "chiseled_sandstone",
        key(BlockID.SANDSTONE, 2) to "cut_sandstone",
        key(BlockID.SANDSTONE, 3) to "sandstone_top", // smooth sandstone
        key(BlockID.RED_SANDSTONE, 0) to "red_sandstone",
        key(BlockID.RED_SANDSTONE, 1) to "chiseled_red_sandstone",
        key(BlockID.RED_SANDSTONE, 2) to "cut_red_sandstone",
        key(BlockID.RED_SANDSTONE, 3) to "red_sandstone_top", // smooth red sandstone
        // ---- monster egg (infested stone) ----
        key(BlockID.MONSTER_EGG, 0) to "stone",
        key(BlockID.MONSTER_EGG, 1) to "cobblestone",
        key(BlockID.MONSTER_EGG, 2) to "stone_bricks",
        key(BlockID.MONSTER_EGG, 3) to "mossy_stone_bricks",
        key(BlockID.MONSTER_EGG, 4) to "cracked_stone_bricks",
        key(BlockID.MONSTER_EGG, 5) to "chiseled_stone_bricks",
        // ---- cobblestone wall ----
        key(BlockID.COBBLE_WALL, 0) to "cobblestone",
        key(BlockID.COBBLE_WALL, 1) to "mossy_cobblestone",
        key(BlockID.COBBLE_WALL, 2) to "granite",
        key(BlockID.COBBLE_WALL, 3) to "diorite",
        key(BlockID.COBBLE_WALL, 4) to "andesite",
        key(BlockID.COBBLE_WALL, 5) to "sandstone",
        key(BlockID.COBBLE_WALL, 6) to "bricks",
        key(BlockID.COBBLE_WALL, 7) to "stone_bricks",
        key(BlockID.COBBLE_WALL, 8) to "mossy_stone_bricks",
        key(BlockID.COBBLE_WALL, 9) to "nether_bricks",
        key(BlockID.COBBLE_WALL, 10) to "end_stone_bricks",
        key(BlockID.COBBLE_WALL, 11) to "prismarine",
        key(BlockID.COBBLE_WALL, 12) to "red_sandstone",
        key(BlockID.COBBLE_WALL, 13) to "red_nether_bricks",
        // ---- quartz block ----
        key(BlockID.QUARTZ_BLOCK, 0) to "quartz_block",
        key(BlockID.QUARTZ_BLOCK, 1) to "chiseled_quartz_block",
        key(BlockID.QUARTZ_BLOCK, 2) to "quartz_pillar",
        key(BlockID.QUARTZ_BLOCK, 3) to "quartz_block_bottom", // smooth quartz
        // ---- prismarine ----
        key(BlockID.PRISMARINE, 0) to "prismarine",
        key(BlockID.PRISMARINE, 1) to "dark_prismarine",
        key(BlockID.PRISMARINE, 2) to "prismarine_bricks",
        // ---- stone slab ----
        key(BlockID.SLAB, 0) to "smooth_stone_slab_side", // Java smooth_stone_slab has no PNG
        key(BlockID.SLAB, 1) to "sandstone", // Java sandstone_slab has no PNG
        key(BlockID.SLAB, 2) to "oak_planks", // Java petrified_oak_slab has no PNG
        key(BlockID.SLAB, 3) to "cobblestone", // Java cobblestone_slab has no PNG
        key(BlockID.SLAB, 4) to "bricks", // Java brick_slab has no PNG
        key(BlockID.SLAB, 5) to "stone_bricks", // Java stone_brick_slab has no PNG
        key(BlockID.SLAB, 6) to "quartz_block", // Java quartz_slab has no PNG
        key(BlockID.SLAB, 7) to "nether_bricks", // Java nether_brick_slab has no PNG
        // ---- stone slab 2 ----
        key(BlockID.RED_SANDSTONE_SLAB, 0) to "red_sandstone",
        key(BlockID.RED_SANDSTONE_SLAB, 1) to "purpur_block",
        key(BlockID.RED_SANDSTONE_SLAB, 2) to "prismarine",
        key(BlockID.RED_SANDSTONE_SLAB, 3) to "dark_prismarine",
        key(BlockID.RED_SANDSTONE_SLAB, 4) to "prismarine_bricks",
        key(BlockID.RED_SANDSTONE_SLAB, 5) to "mossy_cobblestone",
        key(BlockID.RED_SANDSTONE_SLAB, 6) to "sandstone_top",
        key(BlockID.RED_SANDSTONE_SLAB, 7) to "red_nether_bricks",
        // ---- stone slab 3 ----
        key(255 - BlockID.STONE_SLAB3, 0) to "end_stone_bricks",
        key(255 - BlockID.STONE_SLAB3, 1) to "red_sandstone_top",
        key(255 - BlockID.STONE_SLAB3, 2) to "polished_andesite",
        key(255 - BlockID.STONE_SLAB3, 3) to "andesite",
        key(255 - BlockID.STONE_SLAB3, 4) to "diorite",
        key(255 - BlockID.STONE_SLAB3, 5) to "polished_diorite",
        key(255 - BlockID.STONE_SLAB3, 6) to "granite",
        key(255 - BlockID.STONE_SLAB3, 7) to "polished_granite",
        // ---- stone slab 4 ----
        key(255 - BlockID.STONE_SLAB4, 0) to "mossy_stone_bricks",
        key(255 - BlockID.STONE_SLAB4, 1) to "quartz_block_bottom",
        key(255 - BlockID.STONE_SLAB4, 2) to "smooth_stone_slab_side",
        key(255 - BlockID.STONE_SLAB4, 3) to "cut_sandstone",
        key(255 - BlockID.STONE_SLAB4, 4) to "cut_red_sandstone",
        // ---- coral ----
        key(255 - BlockID.CORAL, 0) to "tube_coral",
        key(255 - BlockID.CORAL, 1) to "brain_coral",
        key(255 - BlockID.CORAL, 2) to "bubble_coral",
        key(255 - BlockID.CORAL, 3) to "fire_coral",
        key(255 - BlockID.CORAL, 4) to "horn_coral",
        key(255 - BlockID.CORAL, 8) to "dead_tube_coral",
        key(255 - BlockID.CORAL, 9) to "dead_brain_coral",
        key(255 - BlockID.CORAL, 10) to "dead_bubble_coral",
        key(255 - BlockID.CORAL, 11) to "dead_fire_coral",
        key(255 - BlockID.CORAL, 12) to "dead_horn_coral",
        // ---- coral block ----
        key(255 - BlockID.CORAL_BLOCK, 0) to "tube_coral_block",
        key(255 - BlockID.CORAL_BLOCK, 1) to "brain_coral_block",
        key(255 - BlockID.CORAL_BLOCK, 2) to "bubble_coral_block",
        key(255 - BlockID.CORAL_BLOCK, 3) to "fire_coral_block",
        key(255 - BlockID.CORAL_BLOCK, 4) to "horn_coral_block",
        key(255 - BlockID.CORAL_BLOCK, 8) to "dead_tube_coral_block",
        key(255 - BlockID.CORAL_BLOCK, 9) to "dead_brain_coral_block",
        key(255 - BlockID.CORAL_BLOCK, 10) to "dead_bubble_coral_block",
        key(255 - BlockID.CORAL_BLOCK, 11) to "dead_fire_coral_block",
        key(255 - BlockID.CORAL_BLOCK, 12) to "dead_horn_coral_block",
        // ---- coral fan ----
        key(255 - BlockID.CORAL_FAN, 0) to "tube_coral_fan",
        key(255 - BlockID.CORAL_FAN, 1) to "brain_coral_fan",
        key(255 - BlockID.CORAL_FAN, 2) to "bubble_coral_fan",
        key(255 - BlockID.CORAL_FAN, 3) to "fire_coral_fan",
        key(255 - BlockID.CORAL_FAN, 4) to "horn_coral_fan",
        key(255 - BlockID.CORAL_FAN_DEAD, 0) to "dead_tube_coral_fan",
        key(255 - BlockID.CORAL_FAN_DEAD, 1) to "dead_brain_coral_fan",
        key(255 - BlockID.CORAL_FAN_DEAD, 2) to "dead_bubble_coral_fan",
        key(255 - BlockID.CORAL_FAN_DEAD, 3) to "dead_fire_coral_fan",
        key(255 - BlockID.CORAL_FAN_DEAD, 4) to "dead_horn_coral_fan",
        // ---- tall grass ----
        key(BlockID.TALL_GRASS, 0) to "short_grass", // Bedrock 'grass'
        key(BlockID.TALL_GRASS, 1) to "short_grass", // Bedrock 'grass'
        key(BlockID.TALL_GRASS, 2) to "fern",
        key(BlockID.TALL_GRASS, 3) to "fern",
        // ---- double plant ----
        key(BlockID.DOUBLE_PLANT, 0) to "sunflower_front", // Java sunflower has no PNG; front face is the item icon
        key(BlockID.DOUBLE_PLANT, 1) to "lilac_top", // Java lilac has no PNG
        key(BlockID.DOUBLE_PLANT, 2) to "tall_grass_top", // Java tall_grass has no PNG
        key(BlockID.DOUBLE_PLANT, 3) to "large_fern_top", // Java large_fern has no PNG
        key(BlockID.DOUBLE_PLANT, 4) to "rose_bush_top", // Java rose_bush has no PNG
        key(BlockID.DOUBLE_PLANT, 5) to "peony_top", // Java peony has no PNG
        // ---- flowers ----
        key(BlockID.DANDELION, 0) to "dandelion",
        key(BlockID.POPPY, 0) to "poppy",
        key(BlockID.POPPY, 1) to "blue_orchid",
        key(BlockID.POPPY, 2) to "allium",
        key(BlockID.POPPY, 3) to "azure_bluet",
        key(BlockID.POPPY, 4) to "red_tulip",
        key(BlockID.POPPY, 5) to "orange_tulip",
        key(BlockID.POPPY, 6) to "white_tulip",
        key(BlockID.POPPY, 7) to "pink_tulip",
        key(BlockID.POPPY, 8) to "oxeye_daisy",
        key(BlockID.POPPY, 9) to "cornflower",
        key(BlockID.POPPY, 10) to "lily_of_the_valley",
        // ---- misc blocks ----
        key(BlockID.GRASS, 0) to "grass_block", // Bedrock legacy name is 'grass'
        key(BlockID.DIRT, 0) to "dirt",
        key(BlockID.DIRT, 1) to "coarse_dirt",
        key(BlockID.SAND, 0) to "sand",
        key(BlockID.SAND, 1) to "red_sand",
        key(BlockID.SPONGE, 0) to "sponge",
        key(BlockID.SPONGE, 1) to "wet_sponge",
        key(BlockID.NOTEBLOCK, 0) to "note_block", // Bedrock legacy name is 'noteblock'
        key(BlockID.COBWEB, 0) to "cobweb", // Bedrock legacy name is 'web'
        key(BlockID.DEADBUSH, 0) to "dead_bush", // Bedrock legacy name is 'deadbush'
        key(BlockID.MOSS_STONE, 0) to "mossy_cobblestone", // Bedrock legacy name is 'moss_stone'
        key(BlockID.MONSTER_SPAWNER, 0) to "spawner", // Bedrock legacy name is 'mob_spawner'
        key(BlockID.LIT_REDSTONE_ORE, 0) to "redstone_ore", // Bedrock 'lit_redstone_ore' is a block state in Java
        key(BlockID.CLAY_BLOCK, 0) to "clay",
        key(BlockID.REEDS, 0) to "sugar_cane", // Bedrock legacy name is 'reeds'
        key(BlockID.LIT_PUMPKIN, 0) to "jack_o_lantern", // Bedrock legacy name is 'lit_pumpkin'
        key(BlockID.IRON_BAR, 0) to "iron_bars",
        key(BlockID.GLASS_PANE, 0) to "glass", // Java glass_pane has no PNG; vanilla's pane model samples the glass texture
        key(BlockID.MELON_BLOCK, 0) to "melon", // Bedrock legacy name is 'melon_block'
        key(BlockID.WATER_LILY, 0) to "lily_pad", // Bedrock legacy name is 'waterlily'
        key(BlockID.LIT_REDSTONE_LAMP, 0) to "redstone_lamp", // Bedrock 'lit_redstone_lamp' is a block state in Java
        key(BlockID.COCOA, 0) to "cocoa_stage2", // fully grown cocoa pod, the item icon vanilla uses
        key(BlockID.QUARTZ_ORE, 0) to "nether_quartz_ore", // Bedrock legacy name is 'quartz_ore'
        key(BlockID.SLIME_BLOCK, 0) to "slime_block", // Bedrock legacy name is 'slime'
        key(BlockID.SEA_LANTERN, 0) to "sea_lantern",
        key(BlockID.DAYLIGHT_DETECTOR, 0) to "daylight_detector_top", // daylight detector is not solid, so only the flat top texture can resolve
        key(BlockID.DAYLIGHT_DETECTOR_INVERTED, 0) to "daylight_detector_top", // Bedrock 'daylight_detector_inverted' is a block state in Java
        key(BlockID.GRASS_PATH, 0) to "dirt_path", // Bedrock legacy name is 'grass_path'
        key(BlockID.PURPUR_BLOCK, 0) to "purpur_block",
        key(BlockID.PURPUR_BLOCK, 2) to "purpur_pillar",
        key(BlockID.END_BRICKS, 0) to "end_stone_bricks", // Bedrock legacy name is 'end_bricks'
        key(BlockID.RED_NETHER_BRICK, 0) to "red_nether_bricks", // Bedrock legacy name is 'red_nether_brick'
        key(255 - BlockID.WOOD_BARK, 0) to "oak_log", // wood bark
        key(255 - BlockID.WOOD_BARK, 1) to "spruce_log",
        key(255 - BlockID.WOOD_BARK, 2) to "birch_log",
        key(255 - BlockID.WOOD_BARK, 3) to "jungle_log",
        key(255 - BlockID.WOOD_BARK, 4) to "acacia_log",
        key(255 - BlockID.WOOD_BARK, 5) to "dark_oak_log",
        key(255 - BlockID.WOOD_BARK, 8) to "stripped_oak_log",
        key(255 - BlockID.WOOD_BARK, 9) to "stripped_spruce_log",
        key(255 - BlockID.WOOD_BARK, 10) to "stripped_birch_log",
        key(255 - BlockID.WOOD_BARK, 11) to "stripped_jungle_log",
        key(255 - BlockID.WOOD_BARK, 12) to "stripped_acacia_log",
        key(255 - BlockID.WOOD_BARK, 13) to "stripped_dark_oak_log",
        key(BlockID.ANVIL, 0) to "anvil",
        key(BlockID.ANVIL, 4) to "anvil", // chipped anvil, side texture identical
        key(BlockID.ANVIL, 8) to "anvil", // damaged anvil, side texture identical
        // ---- misc items ----
        key(ItemID.COAL, 0) to "coal",
        key(ItemID.COAL, 1) to "charcoal",
        key(ItemID.RAW_PORKCHOP, 0) to "porkchop",
        key(ItemID.COOKED_PORKCHOP, 0) to "cooked_porkchop",
        key(ItemID.CLAY_BALL, 0) to "clay_ball",
        key(ItemID.SUGAR_CANE, 0) to "sugar_cane", // Bedrock legacy name is 'sugarcane'
        key(ItemID.SLIMEBALL, 0) to "slime_ball",
        key(ItemID.RAW_FISH, 0) to "cod",
        key(ItemID.RAW_FISH, 1) to "salmon",
        key(ItemID.RAW_FISH, 2) to "tropical_fish",
        key(ItemID.RAW_FISH, 3) to "pufferfish",
        key(ItemID.COOKED_FISH, 0) to "cooked_cod",
        key(ItemID.COOKED_FISH, 1) to "cooked_salmon",
        key(ItemID.MAP, 0) to "filled_map",
        key(ItemID.EMPTY_MAP, 0) to "map", // empty map
        key(ItemID.BEEF, 0) to "beef",
        key(ItemID.STEAK, 0) to "cooked_beef",
        key(ItemID.RAW_CHICKEN, 0) to "chicken",
        key(ItemID.COOKED_CHICKEN, 0) to "cooked_chicken",
        key(ItemID.GLASS_BOTTLE, 0) to "glass_bottle",
        key(ItemID.BOOK_AND_QUILL, 0) to "writable_book", // Bedrock legacy name is 'book_and_quill'
        key(ItemID.FIREWORKS, 0) to "firework_rocket", // Bedrock legacy name is 'fireworks'
        key(ItemID.FIREWORKSCHARGE, 0) to "firework_star", // Bedrock legacy name is 'fireworkscharge'
        key(ItemID.RAW_RABBIT, 0) to "rabbit",
        key(ItemID.COOKED_RABBIT, 0) to "cooked_rabbit",
        key(ItemID.LEATHER_HORSE_ARMOR, 0) to "leather_horse_armor",
        key(ItemID.IRON_HORSE_ARMOR, 0) to "iron_horse_armor",
        key(ItemID.GOLD_HORSE_ARMOR, 0) to "golden_horse_armor",
        key(ItemID.DIAMOND_HORSE_ARMOR, 0) to "diamond_horse_armor",
        key(ItemID.NAME_TAG, 0) to "name_tag", // Bedrock legacy name is 'nametag'
        key(ItemID.RAW_MUTTON, 0) to "mutton",
        key(ItemID.COOKED_MUTTON, 0) to "cooked_mutton",
        key(ItemID.RAW_SALMON, 0) to "salmon",
        key(ItemID.CLOWNFISH, 0) to "tropical_fish", // Bedrock legacy name is 'clownfish'
        key(ItemID.COOKED_SALMON, 0) to "cooked_salmon",
        key(ItemID.GOLDEN_APPLE_ENCHANTED, 0) to "golden_apple", // Java enchanted_golden_apple has no PNG
        key(ItemID.TURTLE_SCUTE, 0) to "turtle_scute", // Bedrock legacy name is 'scute'
        key(ItemID.TURTLE_HELMET, 0) to "turtle_helmet", // Bedrock legacy name is 'turtle_shell'
        key(ItemID.CROSSBOW, 0) to "crossbow_standby", // Java crossbow has no PNG; standby is the idle icon
        key(ItemID.ITEM_FRAME, 0) to "item_frame", // Bedrock legacy name is 'frame'
        key(ItemID.GLOW_ITEM_FRAME, 0) to "glow_item_frame", // Bedrock legacy name is 'glow_frame'
        key(ItemID.LODESTONE_COMPASS, 0) to "compass", // Bedrock 'lodestone_compass' has no PNG
        key(ItemID.RECORD_13, 0) to "music_disc_13",
        key(ItemID.RECORD_CAT, 0) to "music_disc_cat",
        key(ItemID.RECORD_BLOCKS, 0) to "music_disc_blocks",
        key(ItemID.RECORD_CHIRP, 0) to "music_disc_chirp",
        key(ItemID.RECORD_FAR, 0) to "music_disc_far",
        key(ItemID.RECORD_MALL, 0) to "music_disc_mall",
        key(ItemID.RECORD_MELLOHI, 0) to "music_disc_mellohi",
        key(ItemID.RECORD_STAL, 0) to "music_disc_stal",
        key(ItemID.RECORD_STRAD, 0) to "music_disc_strad",
        key(ItemID.RECORD_WARD, 0) to "music_disc_ward",
        key(ItemID.RECORD_11, 0) to "music_disc_11",
        key(ItemID.RECORD_WAIT, 0) to "music_disc_wait",
        key(ItemID.RECORD_5, 0) to "music_disc_5",
        key(ItemID.DISC_FRAGMENT_5, 0) to "disc_fragment_5",
        key(ItemID.RECORD_RELIC, 0) to "music_disc_relic",
        key(ItemID.RECORD_PIGSTEP, 0) to "music_disc_pigstep",
        key(ItemID.RECORD_OTHERSIDE, 0) to "music_disc_otherside",
        // ---- skull ----
        key(ItemID.SKULL, 0) to "skeleton_skull",
        key(ItemID.SKULL, 1) to "wither_skeleton_skull",
        key(ItemID.SKULL, 2) to "zombie_head",
        key(ItemID.SKULL, 3) to "player_head",
        key(ItemID.SKULL, 4) to "creeper_head",
        key(ItemID.SKULL, 5) to "dragon_head",
        key(ItemID.SKULL, 6) to "piglin_head",
        key(255 - BlockID.WITHER_SKELETON_SKULL, 0) to "wither_skeleton_skull",
        key(255 - BlockID.ZOMBIE_HEAD, 0) to "zombie_head",
        key(255 - BlockID.PLAYER_HEAD, 0) to "player_head",
        key(255 - BlockID.CREEPER_HEAD, 0) to "creeper_head",
        key(255 - BlockID.DRAGON_HEAD, 0) to "dragon_head",
        key(255 - BlockID.PIGLIN_HEAD, 0) to "piglin_head",
        // ---- bucket ----
        key(ItemID.BUCKET, 0) to "bucket",
        key(ItemID.BUCKET, 1) to "milk_bucket",
        key(ItemID.BUCKET, 2) to "cod_bucket",
        key(ItemID.BUCKET, 3) to "salmon_bucket",
        key(ItemID.BUCKET, 4) to "tropical_fish_bucket",
        key(ItemID.BUCKET, 5) to "pufferfish_bucket",
        key(ItemID.BUCKET, 8) to "water_bucket",
        key(ItemID.BUCKET, 10) to "lava_bucket",
        key(ItemID.BUCKET, 11) to "powder_snow_bucket",
        key(ItemID.BUCKET, 12) to "axolotl_bucket",
        key(ItemID.BUCKET, 13) to "tadpole_bucket",
        // ---- boat ----
        key(ItemID.BOAT, 0) to "oak_boat",
        key(ItemID.BOAT, 1) to "spruce_boat",
        key(ItemID.BOAT, 2) to "birch_boat",
        key(ItemID.BOAT, 3) to "jungle_boat",
        key(ItemID.BOAT, 4) to "acacia_boat",
        key(ItemID.BOAT, 5) to "dark_oak_boat",
        key(ItemID.BOAT, 6) to "mangrove_boat",
        key(ItemID.BOAT, 7) to "bamboo_raft",
        key(ItemID.BOAT, 8) to "cherry_boat",
        key(ItemID.BOAT, 9) to "pale_oak_boat",
        key(ItemID.OAK_CHEST_BOAT, 0) to "oak_chest_boat",
        key(ItemID.BIRCH_CHEST_BOAT, 0) to "birch_chest_boat",
        key(ItemID.JUNGLE_CHEST_BOAT, 0) to "jungle_chest_boat",
        key(ItemID.SPRUCE_CHEST_BOAT, 0) to "spruce_chest_boat",
        key(ItemID.ACACIA_CHEST_BOAT, 0) to "acacia_chest_boat",
        key(ItemID.DARK_OAK_CHEST_BOAT, 0) to "dark_oak_chest_boat",
        key(ItemID.MANGROVE_CHEST_BOAT, 0) to "mangrove_chest_boat",
        key(ItemID.BAMBOO_CHEST_RAFT, 0) to "bamboo_chest_raft",
        key(ItemID.CHERRY_CHEST_BOAT, 0) to "cherry_chest_boat",
        key(ItemID.PALE_OAK_CHEST_BOAT, 0) to "pale_oak_chest_boat",
        // ---- banner pattern ----
        key(ItemID.BANNER_PATTERN, 0) to "creeper_banner_pattern",
        key(ItemID.BANNER_PATTERN, 1) to "skull_banner_pattern",
        key(ItemID.BANNER_PATTERN, 2) to "flower_banner_pattern",
        key(ItemID.BANNER_PATTERN, 3) to "mojang_banner_pattern",
        key(ItemID.BANNER_PATTERN, 4) to "field_masoned_banner_pattern",
        key(ItemID.BANNER_PATTERN, 5) to "bordure_indented_banner_pattern",
        key(ItemID.BANNER_PATTERN, 6) to "piglin_banner_pattern",
        key(ItemID.BANNER_PATTERN, 7) to "globe_banner_pattern",
        // ---- spawn egg ----
        key(ItemID.SPAWN_EGG, 10) to "chicken_spawn_egg",
        key(ItemID.SPAWN_EGG, 11) to "cow_spawn_egg",
        key(ItemID.SPAWN_EGG, 12) to "pig_spawn_egg",
        key(ItemID.SPAWN_EGG, 13) to "sheep_spawn_egg",
        key(ItemID.SPAWN_EGG, 14) to "wolf_spawn_egg",
        key(ItemID.SPAWN_EGG, 15) to "villager_spawn_egg",
        key(ItemID.SPAWN_EGG, 16) to "mooshroom_spawn_egg",
        key(ItemID.SPAWN_EGG, 17) to "squid_spawn_egg",
        key(ItemID.SPAWN_EGG, 18) to "rabbit_spawn_egg",
        key(ItemID.SPAWN_EGG, 19) to "bat_spawn_egg",
        key(ItemID.SPAWN_EGG, 20) to "iron_golem_spawn_egg",
        key(ItemID.SPAWN_EGG, 21) to "snow_golem_spawn_egg",
        key(ItemID.SPAWN_EGG, 22) to "ocelot_spawn_egg",
        key(ItemID.SPAWN_EGG, 23) to "horse_spawn_egg",
        key(ItemID.SPAWN_EGG, 24) to "donkey_spawn_egg",
        key(ItemID.SPAWN_EGG, 25) to "mule_spawn_egg",
        key(ItemID.SPAWN_EGG, 26) to "skeleton_horse_spawn_egg",
        key(ItemID.SPAWN_EGG, 27) to "zombie_horse_spawn_egg",
        key(ItemID.SPAWN_EGG, 28) to "polar_bear_spawn_egg",
        key(ItemID.SPAWN_EGG, 29) to "llama_spawn_egg",
        key(ItemID.SPAWN_EGG, 30) to "parrot_spawn_egg",
        key(ItemID.SPAWN_EGG, 31) to "dolphin_spawn_egg",
        key(ItemID.SPAWN_EGG, 32) to "zombie_spawn_egg",
        key(ItemID.SPAWN_EGG, 33) to "creeper_spawn_egg",
        key(ItemID.SPAWN_EGG, 34) to "skeleton_spawn_egg",
        key(ItemID.SPAWN_EGG, 35) to "spider_spawn_egg",
        key(ItemID.SPAWN_EGG, 36) to "zombified_piglin_spawn_egg",
        key(ItemID.SPAWN_EGG, 37) to "slime_spawn_egg",
        key(ItemID.SPAWN_EGG, 38) to "enderman_spawn_egg",
        key(ItemID.SPAWN_EGG, 39) to "silverfish_spawn_egg",
        key(ItemID.SPAWN_EGG, 40) to "cave_spider_spawn_egg",
        key(ItemID.SPAWN_EGG, 41) to "ghast_spawn_egg",
        key(ItemID.SPAWN_EGG, 42) to "magma_cube_spawn_egg",
        key(ItemID.SPAWN_EGG, 43) to "blaze_spawn_egg",
        key(ItemID.SPAWN_EGG, 44) to "zombie_villager_spawn_egg",
        key(ItemID.SPAWN_EGG, 45) to "witch_spawn_egg",
        key(ItemID.SPAWN_EGG, 46) to "stray_spawn_egg",
        key(ItemID.SPAWN_EGG, 47) to "husk_spawn_egg",
        key(ItemID.SPAWN_EGG, 48) to "wither_skeleton_spawn_egg",
        key(ItemID.SPAWN_EGG, 49) to "guardian_spawn_egg",
        key(ItemID.SPAWN_EGG, 50) to "elder_guardian_spawn_egg",
        key(ItemID.SPAWN_EGG, 54) to "shulker_spawn_egg",
        key(ItemID.SPAWN_EGG, 55) to "endermite_spawn_egg",
        key(ItemID.SPAWN_EGG, 57) to "vindicator_spawn_egg",
        key(ItemID.SPAWN_EGG, 58) to "phantom_spawn_egg",
        key(ItemID.SPAWN_EGG, 59) to "ravager_spawn_egg",
        key(ItemID.SPAWN_EGG, 74) to "turtle_spawn_egg",
        key(ItemID.SPAWN_EGG, 75) to "cat_spawn_egg",
        key(ItemID.SPAWN_EGG, 104) to "evoker_spawn_egg",
        key(ItemID.SPAWN_EGG, 105) to "vex_spawn_egg",
        key(ItemID.SPAWN_EGG, 108) to "pufferfish_spawn_egg",
        key(ItemID.SPAWN_EGG, 109) to "salmon_spawn_egg",
        key(ItemID.SPAWN_EGG, 110) to "drowned_spawn_egg",
        key(ItemID.SPAWN_EGG, 111) to "tropical_fish_spawn_egg",
        key(ItemID.SPAWN_EGG, 112) to "cod_spawn_egg",
        key(ItemID.SPAWN_EGG, 113) to "panda_spawn_egg",
        key(ItemID.SPAWN_EGG, 114) to "pillager_spawn_egg",
        key(ItemID.SPAWN_EGG, 115) to "villager_spawn_egg",
        key(ItemID.SPAWN_EGG, 116) to "zombie_villager_spawn_egg",
        key(ItemID.SPAWN_EGG, 118) to "wandering_trader_spawn_egg",
        key(ItemID.SPAWN_EGG, 121) to "fox_spawn_egg",
        key(ItemID.SPAWN_EGG, 122) to "bee_spawn_egg",
        key(ItemID.SPAWN_EGG, 123) to "piglin_spawn_egg",
        key(ItemID.SPAWN_EGG, 124) to "hoglin_spawn_egg",
        key(ItemID.SPAWN_EGG, 125) to "strider_spawn_egg",
        key(ItemID.SPAWN_EGG, 126) to "zoglin_spawn_egg",
        key(ItemID.SPAWN_EGG, 127) to "piglin_brute_spawn_egg",
        key(ItemID.SPAWN_EGG, 128) to "goat_spawn_egg",
        key(ItemID.SPAWN_EGG, 129) to "glow_squid_spawn_egg",
        key(ItemID.SPAWN_EGG, 130) to "axolotl_spawn_egg",
        key(ItemID.SPAWN_EGG, 131) to "warden_spawn_egg",
        key(ItemID.SPAWN_EGG, 132) to "frog_spawn_egg",
        key(ItemID.SPAWN_EGG, 133) to "tadpole_spawn_egg",
        key(ItemID.SPAWN_EGG, 134) to "allay_spawn_egg",
        key(ItemID.SPAWN_EGG, 138) to "camel_spawn_egg",
        key(ItemID.SPAWN_EGG, 139) to "sniffer_spawn_egg",
        key(ItemID.SPAWN_EGG, 140) to "breeze_spawn_egg",
        key(ItemID.SPAWN_EGG, 142) to "armadillo_spawn_egg",
        key(ItemID.SPAWN_EGG, 144) to "bogged_spawn_egg",
        key(ItemID.SPAWN_EGG, 146) to "creaking_spawn_egg",
        key(ItemID.SPAWN_EGG, 147) to "happy_ghast_spawn_egg",
        key(ItemID.SPAWN_EGG, 148) to "copper_golem_spawn_egg",
    )

    /** Bedrock namespace id -> Java Edition texture base name, where the names differ. */
    val BY_NAMESPACE: Map<String, String> = mapOf(
        "minecraft:brick_block" to "bricks",
        "minecraft:chain" to "iron_chain",
        "minecraft:crafter" to "crafter_top", // pack ships only the directional crafter faces
        "minecraft:deadbush" to "dead_bush",
        "minecraft:end_bricks" to "end_stone_bricks",
        "minecraft:frame" to "item_frame",
        "minecraft:frog_spawn" to "frogspawn",
        "minecraft:glow_frame" to "glow_item_frame",
        "minecraft:glow_stick" to "glowstone_dust", // Bedrock-only item, closest Java texture
        "minecraft:golden_rail" to "powered_rail",
        "minecraft:grass_path" to "dirt_path",
        "minecraft:hardened_clay" to "terracotta",
        "minecraft:item.frame" to "item_frame",
        "minecraft:item.glow_frame" to "glow_item_frame",
        "minecraft:item.reeds" to "sugar_cane",
        "minecraft:lit_blast_furnace" to "blast_furnace",
        "minecraft:lit_deepslate_redstone_ore" to "deepslate_redstone_ore",
        "minecraft:lit_furnace" to "furnace",
        "minecraft:lit_pumpkin" to "jack_o_lantern",
        "minecraft:lit_redstone_lamp" to "redstone_lamp",
        "minecraft:lit_redstone_ore" to "redstone_ore",
        "minecraft:lit_smoker" to "smoker",
        "minecraft:melon_block" to "melon",
        "minecraft:mob_spawner" to "spawner",
        "minecraft:nether_brick" to "nether_bricks",
        "minecraft:netherbrick" to "nether_bricks",
        "minecraft:noteblock" to "note_block",
        "minecraft:powered_comparator" to "comparator",
        "minecraft:powered_repeater" to "repeater",
        "minecraft:quartz_ore" to "nether_quartz_ore",
        "minecraft:red_nether_brick" to "red_nether_bricks",
        "minecraft:reeds" to "sugar_cane",
        "minecraft:silver_glazed_terracotta" to "light_gray_glazed_terracotta",
        "minecraft:slime" to "slime_block",
        "minecraft:small_dripleaf_block" to "small_dripleaf_top", // small dripleaf is not solid, so only the flat top texture can resolve
        "minecraft:snow_layer" to "snow",
        "minecraft:stonebrick" to "stone_bricks",
        "minecraft:tallgrass" to "short_grass",
        "minecraft:trip_wire" to "tripwire",
        "minecraft:underwater_tnt" to "tnt",
        "minecraft:undyed_shulker_box" to "shulker_box",
        "minecraft:unlit_redstone_torch" to "redstone_torch",
        "minecraft:unpowered_comparator" to "comparator",
        "minecraft:unpowered_repeater" to "repeater",
        "minecraft:waterlily" to "lily_pad",
        "minecraft:web" to "cobweb",
        "minecraft:zombie_pigman_spawn_egg" to "zombified_piglin_spawn_egg",
    )

    /** 便捷查询：按 Bedrock 的 (数字 id, meta) 取 Java Edition 贴图名。 */
    fun byIdMeta(id: Int, meta: Int): String? = BY_ID_META[key(id, meta)]

    private fun key(id: Int, meta: Int): Long = (id.toLong() shl 32) or (meta.toLong() and 0xFFFFFFFFL)
}
