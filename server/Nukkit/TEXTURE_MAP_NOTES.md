# NukkitItemTextureTable — coverage & verification notes

Companion to `NukkitItemTextureTable.kt`, the Bedrock→Java texture-name table used by the
Nukkit port of the Java2D inventory renderer.

## 1. Look-up contract

```kotlin
NukkitItemTextureTable.BY_ID_META[key(item.id, item.damage)]   // legacy numeric id + meta
NukkitItemTextureTable.BY_NAMESPACE[namespaceId]               // StringItem / RuntimeItems string
```

`key(id, meta) = (id.toLong() shl 32) or (meta.toLong() and 0xFFFFFFFFL)` — `id` in the high
32 bits, `meta` in the low 32, so the two never overlap.

**Read both maps with the values Nukkit itself exposes**: `Item.getId()` / `Item.getDamage()`,
and `StringItem.getNamespaceId()` (or `RuntimeItems.getLegacyStringFromLegacyId(id)`).

### Gotcha: block ids above 255 live in the *item* id space as `255 - blockId`

`Block.getItemId()` (Nukkit-MOT `block/Block.java:696`) maps any block id above 255 to the
negative item alias `255 - id`, and `Item.getBlockItem(blockId)` does the same
(`item/Item.java:1101-1126`). `Item.get(386, 0)` returns a **book and quill** (legacy item 386),
while coral (block 386) is the item **-131**. `legacy_item_ids.json` uses the same convention
(`"minecraft:coral": -131`, `"minecraft:polished_granite_stairs": -172`).

That is why the table contains entries such as `key(255 - BlockID.CORAL, 0) to "tube_coral"`
rather than `key(BlockID.CORAL, 0)` — the latter can never match a real inventory stack. 71 of
the 260 legacy ids in the table are such negative aliases.

## 2. How a texture name resolves

Mirrors `InventoryRenderer.loadTexture()` (`server/Spigot/.../InventoryRenderer.kt:283-297`):

1. `inventory/faithful32x/special-variants/minecraft/<name>.png` — wins outright, drawn flat
2. `inventory/faithful32x/overrides/items/minecraft/<name>.png` — wins outright, drawn flat
3. `inventory/faithful32x/assets/minecraft/<name>.png`
   - non-solid item/block → drawn flat
   - **solid block** → composed isometrically from `<name>_top.png` + `<name>_side.png`
     (or `<name>_front.png`), each falling back to `<name>.png`

So a value is valid when it resolves through (1)/(2), or exists at (3) directly, or — for a
*solid* block — provides `<name>_top` plus `<name>_side`/`<name>_front`.

## 3. Coverage

| metric | value |
| --- | --- |
| `BY_ID_META` entries | **706** (706 distinct keys, verified at runtime after `mapOf` de-duplication) |
| distinct legacy ids covered | 260 (71 of them negative block aliases) |
| `BY_NAMESPACE` entries | **47** |
| distinct texture names referenced | **536** (521 from `BY_ID_META`, 41 from `BY_NAMESPACE`, 26 shared) |
| ↳ resolved by `overrides/items` | 33 |
| ↳ resolved by `special-variants` | 8 |
| ↳ resolved directly from `assets/minecraft` | 485 |
| ↳ resolved only via the `_top`/`_side` isometric path | 10 |
| PNGs in `faithful32x` | 2028 |
| ↳ named directly by a table value | 526 |
| ↳ additionally pulled in by the 10 isometric-only names (2 faces each) | 20 |
| **PNGs left unreferenced** | **1502** |

The 1502 unreferenced PNGs are *not* a gap: the pack is a full Java asset dump. It contains
`_top`/`_side`/`_front` faces, door halves, animated strips (`clock_00…`, `compass_00…`),
pottery-sherd and banner-pattern sprites, and ~1500 items whose Bedrock name is already
identical to the Java name. Those need no table entry at all — the port derives the name from
`StringItem.getNamespaceId()` / `RuntimeItems` and only falls back to this table when the names
differ (see §5).

## 4. Deliberate substitutions

117 of the 706 `BY_ID_META` values are **not** the flattened Bedrock name; they are the closest
texture that actually exists in the pack. Each one carries a `//` comment in the Kotlin file.
All of them reuse the exact pixels vanilla itself samples for that block, so the item's material
and colour stay correct even though the sprite is a full cube instead of the shaped model:

* **Carpet** (16) → `<colour>_wool`. Vanilla's carpet model samples the wool texture; `<colour>_carpet` has no PNG.
* **Stained glass pane** (16) → `<colour>_stained_glass`. Vanilla's pane model samples the glass texture; only `<colour>_stained_glass_pane_top` (the edge strip) ships.
* **Plain glass pane** → `glass`.
* **Wooden slabs** (6) → `<wood>_planks`; **fences** (6), **fence gates** (6), **buttons** (6),
  **pressure plates** (6) → the matching planks texture. No `<wood>_slab`/`_fence`/`_fence_gate`/`_button`/`_pressure_plate` PNGs exist.
* **Stairs** (40) → the base material texture (`oak_stairs`→`oak_planks`, `stone_stairs`→`cobblestone`,
  `quartz_stairs`→`quartz_block`, …). The pack contains *no* `*stair*` texture at all.
* **Stone slabs** (29) → base material (`smooth_stone_slab`→`smooth_stone_slab_side`, `quartz_slab`→`quartz_block`,
  `smooth_quartz_slab`→`quartz_block_bottom`, …).
* **Cobblestone walls** (14) → base material; no `*_wall` texture exists.
* **Monster eggs / infested blocks** (6) → `stone`, `cobblestone`, `stone_bricks`, … — identical pixels by definition.
* **Smooth sandstone / smooth red sandstone** → `sandstone_top` / `red_sandstone_top` (the same 16×16).
* **Wood bark & stripped wood** (`BlockID.WOOD_BARK`) → `<wood>_log` / `stripped_<wood>_log`; no `<wood>_wood` PNG exists.
* **Chipped / damaged anvil** → `anvil` (the side texture is identical, only the top differs, and the pack ships no `chipped_anvil`/`damaged_anvil` side).
* **Cocoa** → `cocoa_stage2` (the mature pod, i.e. the item icon vanilla uses).
* **Enchanted golden apple** → `golden_apple`; **crossbow** → `crossbow_standby` (its idle sprite);
  **lodestone compass** → `compass`.
* **Daylight detector** (151 and 178) → `daylight_detector_top` and **small dripleaf** →
  `small_dripleaf_top`: both blocks are non-solid in Nukkit (`BlockDaylightDetector.isSolid()`
  returns `false`, `BlockDripleafSmall` is flowable), so the renderer skips the isometric path
  and only a directly-present PNG can resolve.
* **Spawn egg 36** → `zombified_piglin_spawn_egg` (Bedrock calls it `zombie_pigman_spawn_egg`).
* **`minecraft:crafter`** (BY_NAMESPACE) → `crafter_top`; only the directional faces ship.

The 10 names that resolve *only* through the `_top`/`_side` isometric path
(`blast_furnace`, `dirt_path`, `furnace`, `grass_block`, `melon`, `purpur_pillar`, `quartz_block`,
`quartz_pillar`, `smoker`, `tnt`) were each checked against the Nukkit block classes and are all
solid, so the port's `isBlock && isSolid` branch will reach them.

## 5. Deliberately skipped

1. **Banners** — ids 446 (`banner`), 176/177 (`standing_banner`, `wall_banner`), all 16 colours.
   The pack contains no banner texture of any kind (`grep -i banner` matches only the *banner
   pattern item* sprites), and unlike carpet/slab/stair there is no vanilla texture that a banner
   is built from, so every candidate substitute (wool, concrete) would misreport the item rather
   than reuse its own pixels. These fall through to `fallback/unknown.png`.
2. **Spawn eggs for Bedrock-only mobs** — meta 51 `npc_spawn_egg`, 56 `agent_spawn_egg`,
   135 `firefly_spawn_egg`. No Java item, no texture.
3. **Double slabs** — ids 43, 157, 181, 422, 423, 521, 522, … Not obtainable as items in Bedrock
   (they only exist as placed blocks), so they never appear in an inventory snapshot.
4. **Bedrock / Education-only content** — `element_*`, `compound`, `chemistry_table`, `lab_table`,
   `material_reducer`, `bleach`, `ice_bomb`, `rapid_fertilizer`, `medicine`, `sparkler`, `balloon`,
   `camera`, `chalkboard`/`board`, `portfolio`, `photo_item`, `underwater_torch`,
   `colored_torch_*`, `hard_glass*`, `netherreactor`, `info_update*`, `reserved6`,
   `invisible_bedrock`, `allow`/`deny`/`border_block`, `moving_block`, `light_block*`,
   `client_request_placeholder_block`. None has a Java counterpart or a texture in the pack.
5. **Candle cakes** (ids 684-700) — block states, not carryable items.
6. **Items whose Bedrock name already equals the Java name** — intentionally absent, per the
   brief: the port resolves those from the namespace string, and listing ~1500 identical pairs
   would be noise. `BY_NAMESPACE` therefore holds only the 47 genuine differences
   (`noteblock`→`note_block`, `web`→`cobweb`, `tallgrass`→`short_grass`, `stonebrick`→`stone_bricks`,
   `hardened_clay`→`terracotta`, `grass_path`→`dirt_path`, `silver_glazed_terracotta`→
   `light_gray_glazed_terracotta`, `lit_furnace`→`furnace`, …).

## 6. Verification performed

Throwaway tooling lives in `/tmp/texwork` (nothing was added to the repository):

* `texset.py` — rebuilds the texture-name set from the three PNG directories and implements the
  renderer's resolution rule verbatim.
* `gen.py` — declares the table, asserts every value resolves (direct / override / special-variant /
  `_top`+`_side` isometric), asserts key uniqueness, and emits the Kotlin file with real
  `ItemID.*` / `BlockID.*` constants.
* `crosscheck.py` — re-parses the **generated Kotlin** and checks each key against Nukkit's own
  `legacy_item_ids.json` + `item_mappings.json`.

Results:

```
BY_ID_META entries: 706 (distinct keys 706)      FAILURES: 0
BY_NAMESPACE entries: 47                          FAILURES: 0
rows parsed from the .kt file: 706
legacy ids absent from legacy_item_ids.json: 0
agree with item_mappings.json: 339
intentional substitutions: 117
```

The generated file was additionally compiled **and executed** against the real
`cn.nukkit.item.ItemID` / `cn.nukkit.block.BlockID` sources from Nukkit-MOT
(`kotlin-compiler-embeddable`, standalone, outside Gradle — `./gradlew` was never run). The
compiled `NukkitItemTextureTable` was then loaded and every value re-checked against the live
PNG directory:

```
COMPILE EXIT: 0
BY_ID_META entries = 706
BY_NAMESPACE entries = 47
unresolved values = 0
distinct texture names referenced = 536
PNGs in pack = 2028        unreferenced PNGs = 1502
```

Zero unresolved names — nothing had to be dropped for being unresolvable; the only drops are the
identity-changing cases listed in §5.

## 7. Notes for the port

* `BY_ID_META` is keyed in the **item** id space: for `ItemBlock`s use `item.id` as-is (already
  negative for blocks above 255), never `item.block.id`. If you would rather key by block id,
  apply `255 - id` yourself.
* `BlockID` and `ItemID` are both implemented by `cn.nukkit.item.Item`, so the Kotlin file
  imports both — `ItemID.WOOL` does not exist, `BlockID.WOOL` does.
* Family metas follow Bedrock's standard colour order 0=white … 15=black, which Nukkit confirms
  via `DyeColor.getByWoolData()` (`BlockWool`, `BlockCarpet`, `BlockConcrete`, `BlockGlassStained`,
  `BlockTerracottaStained`, `BlockShulkerBox`, `ItemBed`, …).
* Leaf/log/double-plant metas that carry state bits (e.g. leaves `0x4`/`0x8`, double-plant top
  half `0x8`, log axis bits `0x4`/`0x8`) are **not** listed; mask with the family's type bits or
  read the item form (`Block.toItem()` already masks) before the look-up.
* Iron chain is `iron_chain`, not `chain`: the pack follows Java 1.21.9 naming
  (`short_grass`, `dirt_path`, `iron_chain`), so match that generation of names.
