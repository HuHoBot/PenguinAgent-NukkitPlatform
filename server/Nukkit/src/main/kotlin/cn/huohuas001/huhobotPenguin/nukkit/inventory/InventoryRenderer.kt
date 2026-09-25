package cn.huohuas001.huhobotPenguin.nukkit.inventory

import cn.huohuas001.huhobotPenguin.adapter.config.InventoryRenderConfig
import cn.nukkit.item.Item
import cn.nukkit.item.RuntimeItems
import cn.nukkit.item.StringItem
import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.GradientPaint
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.Shape
import java.awt.geom.AffineTransform
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO
import kotlin.math.ceil
import kotlin.math.max

/**
 * 背包 / 末影箱渲染管线（Nukkit 侧）。
 *
 * Faithful 只负责物品纹理；背景、圆角遮罩、格子卡片与人物预览均由程序独立合成，
 * 与 Spigot 适配器保持同一套几何参数，因此两个平台的出图观感一致。
 *
 * Bedrock 的物品标识是「数字 id + meta」或命名空间 id，与 Java 的扁平化名称不同，
 * 贴图名统一交给 [NukkitItemTextureTable] 换算。
 */
object InventoryRenderer {

    private const val RESOURCE_ROOT = "inventory/faithful32x"
    private const val MAX_BACKGROUND_BYTES = 16L * 1024L * 1024L
    private const val MAX_BACKGROUND_PIXELS = 32L * 1024L * 1024L

    private const val INVENTORY_WIDTH = 1359
    private const val INVENTORY_HEIGHT = 1017
    private const val SLOT_SIZE = 104
    private const val ITEM_SIZE = 96
    private const val STORAGE_X = 151
    private const val STORAGE_Y = 485
    private const val STORAGE_STEP_X = 119
    private const val STORAGE_STEP_Y = 114
    private const val HOTBAR_Y = 861
    private const val PREVIEW_X = 537
    private const val PREVIEW_Y = 80
    private const val PREVIEW_WIDTH = 272
    private const val PREVIEW_HEIGHT = 373

    private const val ENDER_WIDTH = 1620
    private const val ENDER_HEIGHT = 694
    private const val ENDER_SLOT_SIZE = 128
    private const val ENDER_ITEM_SIZE = 112
    private const val ENDER_X = 175
    private const val ENDER_Y = 138
    private const val ENDER_STEP_X = 143
    private const val ENDER_STEP_Y = 149

    /** 主背包在 36 格存储数组中的起始下标：0-8 是快捷栏。 */
    private const val STORAGE_OFFSET = 9

    private const val DEFAULT_LEATHER_COLOR = 0xA06540

    /** 与 Java 1.19.3+ 对齐，使用现代默认皮肤而不是 Steve/Alex 旧版。 */
    private const val MODERN_SKIN_VERSION = "1.20.5"

    private val armorPositions = mapOf(
        "head" to Rectangle(399, 143, SLOT_SIZE, SLOT_SIZE),
        "chest" to Rectangle(399, 286, SLOT_SIZE, SLOT_SIZE),
        "legs" to Rectangle(844, 143, SLOT_SIZE, SLOT_SIZE),
        "feet" to Rectangle(844, 286, SLOT_SIZE, SLOT_SIZE)
    )
    private val offhandPosition = Rectangle(210, 221, SLOT_SIZE, SLOT_SIZE)

    private val cardFill = Color(244, 247, 249, 42)
    private val playerFill = Color(244, 247, 249, 48)
    private val cardEdge = Color(255, 255, 255, 78)
    private val cardInnerEdge = Color(35, 51, 64, 26)

    private val COLOR_CODE = Regex("§[0-9a-fk-orA-FK-OR]")

    @Volatile
    private var inventoryBackground: BufferedImage? = null

    @Volatile
    private var enderChestBackground: BufferedImage? = null

    private val textureCache = ConcurrentHashMap<String, BufferedImage>()
    private var fallbackTexture: BufferedImage? = null
    private val playerModelRenderer by lazy { PlayerModelRenderer(PREVIEW_WIDTH, PREVIEW_HEIGHT) }
    private val equipmentAssets by lazy { EquipmentAssetResolver() }

    @Volatile
    private var warningSink: (String) -> Unit = {}

    private fun warn(message: String) {
        warningSink(message)
    }

    /** 初始化默认壁纸或用户壁纸，并预先合成所有强制遮罩。 */
    fun init(dataFolder: File, renderConfig: InventoryRenderConfig, warn: (String) -> Unit) {
        warningSink = warn
        if (System.getProperty("java.awt.headless") == null) {
            System.setProperty("java.awt.headless", "true")
        }
        textureCache.clear()
        fallbackTexture = loadResource("$RESOURCE_ROOT/fallback/unknown.png")

        val normalizedFit = renderConfig.fit.trim().lowercase().takeIf { it == "cover" || it == "stretch" }
            ?: run {
                warn("inventory.render.custom-background.fit 只能是 cover 或 stretch，已使用 cover")
                "cover"
            }
        val defaultInventory = loadResource("$RESOURCE_ROOT/default-wallpaper.png") ?: solidWallpaper()

        var inventoryWallpaper = defaultInventory
        var enderWallpaper = defaultInventory
        if (renderConfig.customBackgroundEnabled) {
            val directory = File(dataFolder, "inventory/backgrounds")
            if (!directory.exists() && !directory.mkdirs()) {
                warn("无法创建自定义背包底图目录: ${directory.absolutePath}")
            }
            inventoryWallpaper = loadCustomWallpaper(directory, renderConfig.inventoryFile, warn) ?: defaultInventory
            enderWallpaper = if (renderConfig.enderChestFile.isBlank()) {
                inventoryWallpaper
            } else {
                loadCustomWallpaper(directory, renderConfig.resolvedEnderChestFile(), warn) ?: defaultInventory
            }
        }

        inventoryBackground = composeInventoryBackground(inventoryWallpaper, normalizedFit)
        enderChestBackground = composeEnderChestBackground(enderWallpaper, normalizedFit)
    }

    /** 背包 PNG；[init] 未完成或渲染失败时返回 null。 */
    fun renderInventory(snapshot: InventorySnapshot?): ByteArray? {
        if (snapshot == null) return null
        val background = inventoryBackground ?: return null
        return try {
            val canvas = copyImage(background)
            val graphics = canvas.createGraphics()
            try {
                configureItemGraphics(graphics)
                drawPlayerPreview(graphics, snapshot)

                drawSlot(graphics, snapshot.armor.getOrNull(0), armorPositions.getValue("head"), ITEM_SIZE)
                drawSlot(graphics, snapshot.armor.getOrNull(1), armorPositions.getValue("chest"), ITEM_SIZE)
                drawSlot(graphics, snapshot.armor.getOrNull(2), armorPositions.getValue("legs"), ITEM_SIZE)
                drawSlot(graphics, snapshot.armor.getOrNull(3), armorPositions.getValue("feet"), ITEM_SIZE)
                drawSlot(graphics, snapshot.offhand, offhandPosition, ITEM_SIZE)

                val storage = snapshot.storage
                for (index in 9 until 36) {
                    val normalized = index - 9
                    drawSlot(
                        graphics,
                        storage.getOrNull(index),
                        Rectangle(
                            STORAGE_X + normalized % 9 * STORAGE_STEP_X,
                            STORAGE_Y + normalized / 9 * STORAGE_STEP_Y,
                            SLOT_SIZE,
                            SLOT_SIZE
                        ),
                        ITEM_SIZE
                    )
                }
                for (index in 0 until 9) {
                    drawSlot(
                        graphics,
                        storage.getOrNull(index),
                        Rectangle(STORAGE_X + index * STORAGE_STEP_X, HOTBAR_Y, SLOT_SIZE, SLOT_SIZE),
                        ITEM_SIZE
                    )
                }
            } finally {
                graphics.dispose()
            }
            encode(canvas)
        } catch (error: Exception) {
            warn("背包渲染失败: ${error.message}")
            null
        }
    }

    /** 末影箱 PNG；[init] 未完成或渲染失败时返回 null。 */
    fun renderEnderChest(snapshot: InventorySnapshot?): ByteArray? {
        if (snapshot == null) return null
        val background = enderChestBackground ?: return null
        return try {
            val canvas = copyImage(background)
            val graphics = canvas.createGraphics()
            try {
                configureItemGraphics(graphics)
                val contents = snapshot.enderChest
                for (index in 0 until 27) {
                    drawSlot(
                        graphics,
                        contents.getOrNull(index),
                        Rectangle(
                            ENDER_X + index % 9 * ENDER_STEP_X,
                            ENDER_Y + index / 9 * ENDER_STEP_Y,
                            ENDER_SLOT_SIZE,
                            ENDER_SLOT_SIZE
                        ),
                        ENDER_ITEM_SIZE
                    )
                }
            } finally {
                graphics.dispose()
            }
            encode(canvas)
        } catch (error: Exception) {
            warn("末影箱渲染失败: ${error.message}")
            null
        }
    }

    /** 文本形态的背包内容；没有快照时返回 null。 */
    fun renderTextInventory(snapshot: InventorySnapshot?): String? {
        if (snapshot == null) return null

        val lines = mutableListOf<String>()

        lines.add("=== 护甲 ===")
        val armorLabels = listOf("头盔", "胸甲", "护腿", "靴子")
        lines.add(
            armorLabels.mapIndexed { index, label ->
                "$label: ${formatItem(snapshot.armor.getOrNull(index))}"
            }.joinToString(" | ")
        )

        lines.add("=== 副手 ===")
        lines.add("副手: ${formatItem(snapshot.offhand)}")

        // 主背包 9-35。Spigot 适配器这里误把 0-26 当物品栏，导致快捷栏重复显示、
        // 27-35 永远不显示；Nukkit 侧按真实槽位输出。
        lines.add("=== 物品栏 ===")
        for (row in 0 until 3) {
            lines.add(
                (0 until 9).map { column ->
                    formatItem(snapshot.storage.getOrNull(STORAGE_OFFSET + row * 9 + column))
                }.joinToString(" | ")
            )
        }

        lines.add("=== 快捷栏 ===")
        lines.add((0 until 9).map { formatItem(snapshot.storage.getOrNull(it)) }.joinToString(" | "))

        // 放进代码块：QQ 的 markdown 会把 "--- | --- | …" 这种行当成表格/分割线解析，
        // 一整行全空时会渲染成一个很突兀的方框；代码块能保持等宽、原样输出。
        return buildString {
            appendLine("```")
            lines.forEach { appendLine(it) }
            append("```")
        }
    }

    // ---------------------------------------------------------------- 绘制

    private fun drawPlayerPreview(graphics: Graphics2D, snapshot: InventorySnapshot) {
        val skin = skinOf(snapshot)
        try {
            val equipment = ArmorEquipmentSet.from(snapshot.armor, ::textureName)
            graphics.drawImage(
                playerModelRenderer.render(skin, equipment, equipmentAssets),
                PREVIEW_X,
                PREVIEW_Y,
                null
            )
        } catch (error: Exception) {
            warn("盔甲模型渲染失败，改用普通模型: ${snapshot.playerName}: ${error.message}")
            graphics.drawImage(playerModelRenderer.render(skin), PREVIEW_X, PREVIEW_Y, null)
        }
    }

    private fun drawSlot(graphics: Graphics2D, item: Item?, bounds: Rectangle, itemSize: Int) {
        if (InventorySnapshot.isAir(item)) return
        val target = item ?: return
        if (target.count <= 0) return

        val texture = getTexture(target) ?: fallbackTexture ?: return
        val itemX = bounds.x + (bounds.width - itemSize) / 2
        val itemY = bounds.y + (bounds.height - itemSize) / 2
        graphics.composite = AlphaComposite.SrcOver
        graphics.drawImage(texture, itemX, itemY, itemSize, itemSize, null)

        if (target.hasEnchantments()) {
            graphics.color = Color(130, 95, 255, 150)
            graphics.stroke = BasicStroke(3f)
            graphics.drawRoundRect(itemX + 1, itemY + 1, itemSize - 2, itemSize - 2, 8, 8)
        }
        if (target.count > 1) drawAmount(graphics, bounds, target.count)

        val maxDamage = maxDurability(target)
        if (maxDamage > 0 && target.damage > 0) drawDurability(graphics, bounds, maxDamage, target.damage)
    }

    private fun drawAmount(graphics: Graphics2D, bounds: Rectangle, amount: Int) {
        val text = amount.toString()
        graphics.font = Font(Font.SANS_SERIF, Font.BOLD, 26)
        val metrics = graphics.fontMetrics
        val x = bounds.x + bounds.width - 4 - metrics.stringWidth(text)
        val y = bounds.y + bounds.height - 4
        graphics.color = Color(0, 0, 0, 210)
        graphics.drawString(text, x + 2, y + 2)
        graphics.color = Color.WHITE
        graphics.drawString(text, x, y)
    }

    private fun drawDurability(graphics: Graphics2D, bounds: Rectangle, maxDamage: Int, damage: Int) {
        val remaining = (1.0 - damage.toDouble() / maxDamage.toDouble()).coerceIn(0.0, 1.0)
        val x = bounds.x + 12
        val y = bounds.y + 96
        val width = 80
        graphics.color = Color(18, 18, 18, 230)
        graphics.fillRect(x, y, width, 6)
        graphics.color = if (remaining > 0.5) Color(73, 214, 112) else Color(238, 177, 47)
        graphics.fillRect(x, y, (width * remaining).toInt(), 6)
    }

    // ---------------------------------------------------------------- 贴图

    private fun getTexture(item: Item): BufferedImage? {
        val name = textureName(item) ?: return fallbackTexture

        if (name in LEATHER_ARMOR_KEYS) {
            val color = leatherColor(item) ?: DEFAULT_LEATHER_COLOR
            runCatching { equipmentAssets.resolveLeatherItemTexture(name, color) }
                .onFailure { warn("皮革物品贴图渲染失败: $name: ${it.message}") }
                .getOrNull()?.let { return it }
        }

        val cacheKey = "$name|${isBlockItem(item)}"
        textureCache[cacheKey]?.let { return it }
        val loaded = loadTexture(name, isBlockItem(item)) ?: fallbackTexture ?: return null
        return textureCache.putIfAbsent(cacheKey, loaded) ?: loaded
    }

    /**
     * Bedrock 物品 → Java Edition 贴图名。
     *
     * 顺序：`(id, meta)` 精确表 → 命名空间 id 别名表 → 命名空间 id 本身
     * → 旧版数字 id 的扁平化名称 → 由命名空间 id 去掉前缀兜底。
     */
    internal fun textureName(item: Item): String? {
        NukkitItemTextureTable.byIdMeta(item.id, item.damage)?.let { return it }

        val namespace = namespaceId(item)
        if (namespace != null) {
            NukkitItemTextureTable.BY_NAMESPACE[namespace]?.let { return it }
            namespace.removePrefix("minecraft:").takeIf { it.isNotBlank() }?.let { return it }
        }

        val legacy = RuntimeItems.getLegacyStringFromLegacyId(item.id)?.lowercase()
        if (legacy != null) {
            NukkitItemTextureTable.BY_NAMESPACE[legacy]?.let { return it }
            legacy.removePrefix("minecraft:").takeIf { it.isNotBlank() }?.let { return it }
        }
        return null
    }

    private fun namespaceId(item: Item): String? =
        (item as? StringItem)?.namespaceId?.lowercase()?.takeIf { it.isNotBlank() }

    /** 与 Spigot 的 `material.isBlock && material.isSolid` 对齐：只有实心方块才走等距立方体。 */
    private fun isBlockItem(item: Item): Boolean = try {
        val block = item.block
        !block.isAir && block.isSolid
    } catch (_: Throwable) {
        false
    }

    /**
     * 与 Spigot 侧一致：实体方块用「顶面 + 侧面」合成等距立方体，
     * 其余物品直接用平面图标。
     */
    private fun loadTexture(name: String, blockish: Boolean): BufferedImage? {
        loadResource("$RESOURCE_ROOT/special-variants/minecraft/$name.png")?.let { return it }
        loadResource("$RESOURCE_ROOT/overrides/items/minecraft/$name.png")?.let { return it }

        val direct = loadResource("$RESOURCE_ROOT/assets/minecraft/$name.png")
        if (blockish) {
            val top = loadResource("$RESOURCE_ROOT/assets/minecraft/${name}_top.png") ?: direct
            val side = loadResource("$RESOURCE_ROOT/assets/minecraft/${name}_side.png")
                ?: loadResource("$RESOURCE_ROOT/assets/minecraft/${name}_front.png")
                ?: direct
            if (top != null && side != null) return renderIsometricBlock(top, side)
        }
        return direct ?: fallbackTexture
    }

    private fun maxDurability(item: Item): Int = try {
        item.maxDurability
    } catch (_: Throwable) {
        0
    }

    private fun leatherColor(item: Item): Int? {
        val tag = item.namedTag ?: return null
        if (!tag.exist("customColor")) return null
        return tag.getInt("customColor") and 0xFFFFFF
    }

    // ---------------------------------------------------------------- 皮肤

    private fun skinOf(snapshot: InventorySnapshot): PlayerSkin {
        val data = snapshot.skinData
        if (data != null && data.isNotEmpty()) {
            val width = snapshot.skinWidth
            val height = snapshot.skinHeight
            if (width > 0 && height > 0 && data.size >= width * height * 4) {
                val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
                image.setRGB(0, 0, width, height, toArgb(data, width * height), 0, width)
                val normalized = normalizeSkinSize(image)
                if (normalized != null) {
                    return PlayerSkin(
                        image = normalized,
                        cacheKey = "snapshot:${snapshot.playerName.lowercase()}",
                        source = "player-skin",
                        slim = snapshot.skinSlim
                    )
                }
            }
        }
        // 没有皮肤数据（或尺寸无法归一化）时按离线 UUID 取内置默认皮肤，保证人物预览始终有内容。
        val uuid = UUID.nameUUIDFromBytes("OfflinePlayer:${snapshot.playerName}".toByteArray(Charsets.UTF_8))
        return DefaultPlayerSkinProvider.defaultSkin(uuid, MODERN_SKIN_VERSION)
    }

    /**
     * 把任意尺寸的 Bedrock 皮肤归一化成模型渲染器要求的 64x64 / 64x32。
     *
     * Bedrock 允许 128x128（HD）甚至 256x256 的皮肤，UV 布局与 64x64 完全一致、只是分辨率翻倍，
     * 因此用最近邻缩放即可无损还原成标准尺寸。 [PlayerSkin] 只接受 64x64 / 64x32，
     * 不做这一步会直接抛异常导致整张图渲染失败、回退成文本。
     */
    private fun normalizeSkinSize(image: BufferedImage): BufferedImage? {
        if (image.width <= 0 || image.height <= 0) return null
        if (image.width == 64 && (image.height == 64 || image.height == 32)) return image
        // 2:1 的旧版布局保持 64x32，其余按 64x64 处理。
        val targetHeight = if (image.height * 2 <= image.width) 32 else 64
        return try {
            BufferedImage(64, targetHeight, BufferedImage.TYPE_INT_ARGB).also { scaled ->
                scaled.createGraphics().run {
                    setRenderingHint(
                        RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
                    )
                    drawImage(image, 0, 0, 64, targetHeight, null)
                    dispose()
                }
            }
        } catch (error: Exception) {
            warn("皮肤尺寸归一化失败(${image.width}x${image.height}): ${error.message}")
            null
        }
    }

    /** Bedrock 皮肤是 RGBA 字节序，BufferedImage 需要 ARGB 整数。 */
    private fun toArgb(data: ByteArray, pixels: Int): IntArray {
        val out = IntArray(pixels)
        for (index in 0 until pixels) {
            val offset = index * 4
            val red = data[offset].toInt() and 0xFF
            val green = data[offset + 1].toInt() and 0xFF
            val blue = data[offset + 2].toInt() and 0xFF
            val alpha = data[offset + 3].toInt() and 0xFF
            out[index] = (alpha shl 24) or (red shl 16) or (green shl 8) or blue
        }
        return out
    }

    // ---------------------------------------------------------------- 等距立方体

    private fun renderIsometricBlock(top: BufferedImage, side: BufferedImage): BufferedImage =
        renderIsometricBlock(top, side, side)

    private fun renderIsometricBlock(
        top: BufferedImage,
        front: BufferedImage,
        right: BufferedImage
    ): BufferedImage {
        val result = BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB)
        val graphics = result.createGraphics()
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val centerX = 32
            val topY = 6
            val halfWidth = 25
            val halfHeight = 13
            val sideHeight = 31
            drawFace(
                graphics, top,
                intArrayOf(
                    centerX, topY,
                    centerX + halfWidth, topY + halfHeight,
                    centerX, topY + halfHeight * 2,
                    centerX - halfWidth, topY + halfHeight
                )
            )
            drawFace(
                graphics, shade(front, 0.80),
                intArrayOf(
                    centerX - halfWidth, topY + halfHeight,
                    centerX, topY + halfHeight * 2,
                    centerX, topY + halfHeight * 2 + sideHeight,
                    centerX - halfWidth, topY + halfHeight + sideHeight
                )
            )
            drawFace(
                graphics, shade(right, 0.68),
                intArrayOf(
                    centerX, topY + halfHeight * 2,
                    centerX + halfWidth, topY + halfHeight,
                    centerX + halfWidth, topY + halfHeight + sideHeight,
                    centerX, topY + halfHeight * 2 + sideHeight
                )
            )
        } finally {
            graphics.dispose()
        }
        return result
    }

    private fun drawFace(graphics: Graphics2D, texture: BufferedImage, target: IntArray) {
        val width = texture.width.toDouble()
        val height = texture.height.toDouble()
        val transform = AffineTransform(
            (target[2] - target[0]) / width,
            (target[3] - target[1]) / width,
            (target[6] - target[0]) / height,
            (target[7] - target[1]) / height,
            target[0].toDouble(),
            target[1].toDouble()
        )
        val oldClip = graphics.clip
        graphics.clip = java.awt.Polygon(
            intArrayOf(target[0], target[2], target[4], target[6]),
            intArrayOf(target[1], target[3], target[5], target[7]),
            4
        )
        graphics.drawImage(texture, transform, null)
        graphics.clip = oldClip
    }

    private fun shade(source: BufferedImage, factor: Double): BufferedImage {
        val result = BufferedImage(source.width, source.height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until source.height) {
            for (x in 0 until source.width) {
                val argb = source.getRGB(x, y)
                val alpha = argb ushr 24
                val red = (((argb ushr 16) and 0xff) * factor).toInt().coerceAtMost(255)
                val green = (((argb ushr 8) and 0xff) * factor).toInt().coerceAtMost(255)
                val blue = ((argb and 0xff) * factor).toInt().coerceAtMost(255)
                result.setRGB(x, y, (alpha shl 24) or (red shl 16) or (green shl 8) or blue)
            }
        }
        return result
    }

    // ---------------------------------------------------------------- 背景合成

    private fun composeInventoryBackground(wallpaper: BufferedImage, fit: String): BufferedImage {
        val result = wallpaper(wallpaper, INVENTORY_WIDTH, INVENTORY_HEIGHT, fit)
        val graphics = result.createGraphics()
        try {
            configureBackgroundGraphics(graphics)
            for (index in 0 until 27) {
                drawSelectionCard(
                    graphics,
                    Rectangle(
                        STORAGE_X + index % 9 * STORAGE_STEP_X + 2,
                        STORAGE_Y + index / 9 * STORAGE_STEP_Y + 2,
                        SLOT_SIZE - 4,
                        SLOT_SIZE - 4
                    ),
                    cardFill
                )
            }
            for (index in 0 until 9) {
                drawSelectionCard(
                    graphics,
                    Rectangle(STORAGE_X + index * STORAGE_STEP_X + 2, HOTBAR_Y + 2, SLOT_SIZE - 4, SLOT_SIZE - 4),
                    cardFill
                )
            }
            armorPositions.values.forEach { drawSelectionCard(graphics, inset(it, 2), cardFill) }
            drawSelectionCard(graphics, inset(offhandPosition, 2), cardFill)
            drawSelectionCard(
                graphics,
                Rectangle(PREVIEW_X, PREVIEW_Y, PREVIEW_WIDTH, PREVIEW_HEIGHT),
                playerFill,
                14
            )
            drawOuterFrame(graphics, INVENTORY_WIDTH, INVENTORY_HEIGHT)
        } finally {
            graphics.dispose()
        }
        return result
    }

    private fun composeEnderChestBackground(wallpaper: BufferedImage, fit: String): BufferedImage {
        val result = wallpaper(wallpaper, ENDER_WIDTH, ENDER_HEIGHT, fit)
        val graphics = result.createGraphics()
        try {
            configureBackgroundGraphics(graphics)
            for (index in 0 until 27) {
                drawSelectionCard(
                    graphics,
                    Rectangle(
                        ENDER_X + index % 9 * ENDER_STEP_X + 2,
                        ENDER_Y + index / 9 * ENDER_STEP_Y + 2,
                        ENDER_SLOT_SIZE - 4,
                        ENDER_SLOT_SIZE - 4
                    ),
                    cardFill
                )
            }
            drawOuterFrame(graphics, ENDER_WIDTH, ENDER_HEIGHT)
        } finally {
            graphics.dispose()
        }
        return result
    }

    private fun wallpaper(source: BufferedImage, width: Int, height: Int, fit: String): BufferedImage {
        val result = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val graphics = result.createGraphics()
        try {
            configureBackgroundGraphics(graphics)
            val clip: Shape = RoundRectangle2D.Float(2f, 2f, width - 4f, height - 4f, 60f, 60f)
            graphics.clip(clip)
            if (fit == "stretch") {
                graphics.drawImage(source, 0, 0, width, height, null)
            } else {
                val scale = max(width.toDouble() / source.width, height.toDouble() / source.height)
                val scaledWidth = max(1, ceil(source.width * scale).toInt())
                val scaledHeight = max(1, ceil(source.height * scale).toInt())
                graphics.drawImage(
                    source,
                    (width - scaledWidth) / 2,
                    (height - scaledHeight) / 2,
                    scaledWidth,
                    scaledHeight,
                    null
                )
            }
        } finally {
            graphics.dispose()
        }
        return result
    }

    private fun drawSelectionCard(
        graphics: Graphics2D,
        bounds: Rectangle,
        fill: Color,
        arc: Int = 10
    ) {
        val originalPaint = graphics.paint
        graphics.paint = GradientPaint(
            0f, bounds.y.toFloat(), Color(244, 247, 249, (fill.alpha * 1.15).toInt()),
            0f, (bounds.y + bounds.height).toFloat(), Color(214, 221, 226, (fill.alpha * 0.65).toInt())
        )
        graphics.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, arc, arc)
        graphics.paint = originalPaint
        graphics.stroke = BasicStroke(1.2f)
        graphics.color = cardEdge
        graphics.drawRoundRect(bounds.x, bounds.y, bounds.width - 1, bounds.height - 1, arc, arc)
        graphics.stroke = BasicStroke(1f)
        graphics.color = cardInnerEdge
        graphics.drawRoundRect(bounds.x + 2, bounds.y + 2, bounds.width - 5, bounds.height - 5, arc, arc)
    }

    private fun drawOuterFrame(graphics: Graphics2D, width: Int, height: Int) {
        graphics.stroke = BasicStroke(4f)
        graphics.color = Color(245, 252, 253, 210)
        graphics.draw(RoundRectangle2D.Float(2f, 2f, width - 5f, height - 5f, 60f, 60f))
        graphics.stroke = BasicStroke(1f)
        graphics.color = Color(20, 35, 47, 210)
        graphics.draw(RoundRectangle2D.Float(0.5f, 0.5f, width - 2f, height - 2f, 62f, 62f))
    }

    private fun loadCustomWallpaper(directory: File, fileName: String, warn: (String) -> Unit): BufferedImage? {
        val safeName = fileName.trim()
        if (!safeName.matches(Regex("[A-Za-z0-9._-]+\\.png"))) {
            warn("忽略不安全的自定义底图文件名: $fileName")
            return null
        }
        val root = directory.canonicalFile
        val file = File(root, safeName).canonicalFile
        if (!file.path.startsWith(root.path + File.separator) || !file.isFile) {
            warn("找不到自定义背包底图: ${file.absolutePath}")
            return null
        }
        return try {
            if (file.length() > MAX_BACKGROUND_BYTES) {
                warn("自定义背包底图超过 16 MiB: ${file.absolutePath}")
                null
            } else {
                val image = ImageIO.read(file)
                val pixels = if (image == null) Long.MAX_VALUE else image.width.toLong() * image.height.toLong()
                if (image == null || image.width < 1 || image.height < 1 || pixels > MAX_BACKGROUND_PIXELS) {
                    warn("自定义背包底图无法读取或尺寸不安全: ${file.absolutePath}")
                    null
                } else {
                    image
                }
            }
        } catch (error: Exception) {
            warn("读取自定义背包底图失败: ${file.absolutePath}: ${error.message}")
            null
        }
    }

    private fun loadResource(path: String): BufferedImage? = try {
        val stream: InputStream? = InventoryRenderer::class.java.classLoader.getResourceAsStream(path)
        stream?.use {
            val image = ImageIO.read(it) ?: return null
            // 动画贴图是纵向长条，裁成首帧的正方形，避免拉伸变形。
            if (image.height > image.width && image.height > 16) {
                image.getSubimage(0, 0, image.width, image.width)
            } else {
                image
            }
        }
    } catch (_: Exception) {
        null
    }

    private fun solidWallpaper(): BufferedImage = BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB).also {
        it.setRGB(0, 0, Color(45, 58, 72).rgb)
    }

    private fun copyImage(source: BufferedImage): BufferedImage =
        BufferedImage(source.width, source.height, BufferedImage.TYPE_INT_ARGB).also { target ->
            target.createGraphics().run {
                drawImage(source, 0, 0, null)
                dispose()
            }
        }

    private fun encode(image: BufferedImage): ByteArray? = try {
        drawWatermark(image)
        ByteArrayOutputStream(256 * 1024).use { output ->
            if (!ImageIO.write(image, "PNG", output)) null else output.toByteArray()
        }
    } catch (error: Exception) {
        warn("背包图片编码失败: ${error.message}")
        null
    }

    private fun drawWatermark(image: BufferedImage) {
        val text = "Textures: faithfulpack.net"
        val graphics = image.createGraphics()
        try {
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            graphics.font = Font(Font.SANS_SERIF, Font.PLAIN, 18)
            val metrics = graphics.fontMetrics
            val x = image.width - metrics.stringWidth(text) - 16
            val y = image.height - 16
            graphics.composite = AlphaComposite.SrcOver.derive(0.45f)
            graphics.color = Color(255, 255, 255)
            graphics.drawString(text, x, y)
            graphics.composite = AlphaComposite.SrcOver
        } finally {
            graphics.dispose()
        }
    }

    private fun configureItemGraphics(graphics: Graphics2D) {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
    }

    private fun configureBackgroundGraphics(graphics: Graphics2D) {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
    }

    private fun inset(bounds: Rectangle, amount: Int): Rectangle = Rectangle(
        bounds.x + amount,
        bounds.y + amount,
        bounds.width - amount * 2,
        bounds.height - amount * 2
    )

    private fun formatItem(item: Item?): String {
        if (InventorySnapshot.isAir(item)) return "---"
        val target = item ?: return "---"

        val rawName = target.customName?.takeIf { it.isNotBlank() } ?: target.name
        val builder = StringBuilder(rawName.replace(COLOR_CODE, ""))
        if (target.count > 1) builder.append(" x").append(target.count)

        val maxDamage = maxDurability(target)
        if (maxDamage > 0 && target.damage > 0) {
            builder.append(" (").append(maxDamage - target.damage).append('/').append(maxDamage).append(')')
        }
        return builder.toString()
    }

    private val LEATHER_ARMOR_KEYS = setOf(
        "leather_helmet", "leather_chestplate", "leather_leggings", "leather_boots"
    )
}
