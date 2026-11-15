package cn.huohuas001.huhobotPenguin.nukkit.onlinelist

import java.awt.AlphaComposite
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.util.Collections
import java.util.LinkedHashMap

/**
 * 有界 LRU 皮肤头像缓存。
 *
 * 与 Spigot 侧的差别：Bedrock 客户端登录时就把皮肤像素交给服务端，
 * 这里直接从 [OnlineListPlayer.skinData] 还原成图片再裁头部，不需要下载，
 * 因此没有超时/并发下载参数，也不会因为外网不通而掉头像。
 */
class AvatarCache(
    private val cacheCapacity: Int,
    private val enabled: Boolean
) {
    private companion object {
        const val ICON_SIZE = 128
    }

    private val cache: MutableMap<String, BufferedImage> = Collections.synchronizedMap(
        object : LinkedHashMap<String, BufferedImage>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: Map.Entry<String, BufferedImage>): Boolean =
                size > cacheCapacity
        }
    )

    /**
     * 为所有玩家生成头像，返回 uuid → 头像图的映射。
     * 没有皮肤数据或尺寸不合法的玩家不在结果中，渲染时走回退占位。
     */
    fun loadAll(players: List<OnlineListPlayer>): Map<String, BufferedImage> {
        if (!enabled) return emptyMap()
        val result = LinkedHashMap<String, BufferedImage>()
        for (player in players) {
            val avatar = avatarOf(player) ?: continue
            result[player.uuid] = avatar
        }
        return result
    }

    private fun avatarOf(player: OnlineListPlayer): BufferedImage? {
        val data = player.skinData ?: return null
        val width = player.skinWidth
        val height = player.skinHeight
        if (width <= 0 || height <= 0 || data.size < width * height * 4) return null

        // 缓存键用尺寸 + 内容哈希：皮肤一换哈希就变，不需要额外的失效逻辑。
        val key = "${width}x${height}:${data.contentHashCode()}"
        cache[key]?.let { return it }

        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        image.setRGB(0, 0, width, height, toArgb(data, width * height), 0, width)
        val normalized = normalizeSkinSize(image) ?: return null
        val icon = cropHead(normalized)
        cache[key] = icon
        return icon
    }

    /** Bedrock 皮肤是 RGBA 字节序，`setRGB` 要的是 ARGB。 */
    private fun toArgb(data: ByteArray, pixelCount: Int): IntArray {
        val argb = IntArray(pixelCount)
        var index = 0
        for (i in 0 until pixelCount) {
            val r = data[index].toInt() and 0xFF
            val g = data[index + 1].toInt() and 0xFF
            val b = data[index + 2].toInt() and 0xFF
            val a = data[index + 3].toInt() and 0xFF
            argb[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
            index += 4
        }
        return argb
    }

    /**
     * 把任意尺寸的 Bedrock 皮肤归一化成 64×64 / 64×32。
     *
     * HD 皮肤（128×128、256×256）与标清皮肤的 UV 布局完全一致，只是分辨率翻倍，
     * 用最近邻缩小即可无损还原，这样下面的裁剪坐标可以只写一套。
     */
    private fun normalizeSkinSize(image: BufferedImage): BufferedImage? {
        if (image.width <= 0 || image.height <= 0) return null
        if (image.width == 64 && (image.height == 64 || image.height == 32)) return image
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
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * 裁出 Minecraft 皮肤的头部（含帽子层），输出 128×128 图标。
     */
    fun cropHead(skin: BufferedImage): BufferedImage {
        val icon = BufferedImage(ICON_SIZE, ICON_SIZE, BufferedImage.TYPE_INT_ARGB)
        val graphics: Graphics2D = icon.createGraphics()
        try {
            graphics.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
            )
            graphics.drawImage(skin, 0, 0, ICON_SIZE, ICON_SIZE, 8, 8, 16, 16, null)
            if (skin.height >= 64) {
                graphics.composite = AlphaComposite.SrcOver
                graphics.drawImage(skin, 0, 0, ICON_SIZE, ICON_SIZE, 40, 8, 48, 16, null)
            }
        } finally {
            graphics.dispose()
        }
        return icon
    }
}
