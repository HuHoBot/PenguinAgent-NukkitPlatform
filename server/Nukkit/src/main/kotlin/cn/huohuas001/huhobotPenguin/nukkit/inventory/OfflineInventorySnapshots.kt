package cn.huohuas001.huhobotPenguin.nukkit.inventory

import cn.huohuas001.bot.tools.Cancelable
import cn.huohuas001.huhobotPenguin.nukkit.HuHoBotNukkit
import cn.nukkit.Player
import cn.nukkit.event.EventHandler
import cn.nukkit.event.EventPriority
import cn.nukkit.event.Listener
import cn.nukkit.event.player.PlayerQuitEvent
import cn.nukkit.item.Item
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Base64
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 背包快照仓库：玩家退服与定时全量采集，落盘到 `inventory/snapshots/<玩家名>.yml`，
 * 以便玩家离线时仍能查询背包/末影箱。
 *
 * 落盘在单线程守护线程池里做，避免阻塞服务端主线程；读取只认「在线实时 → 内存缓存 → 磁盘」。
 */
class OfflineInventorySnapshots(private val plugin: HuHoBotNukkit) : Listener {

    private val directory = File(plugin.dataFolder, "inventory/snapshots")

    /** 玩家名（小写）→ 快照。 */
    private val cache = ConcurrentHashMap<String, InventorySnapshot>()

    private val writer = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "Penguin-Inventory-Snapshot").apply { isDaemon = true }
    }

    private var periodicTask: Cancelable? = null

    fun start() {
        directory.mkdirs()
        loadExisting()
        plugin.server.pluginManager.registerEvents(this, plugin)
        periodicTask = plugin.submitTimer(PERIODIC_CAPTURE_TICKS, PERIODIC_CAPTURE_TICKS) { captureAllOnline() }
    }

    fun close() {
        periodicTask?.cancel()
        periodicTask = null
        captureAllOnline()
        writer.shutdown()
        try {
            writer.awaitTermination(3, TimeUnit.SECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerQuit(event: PlayerQuitEvent) {
        capture(event.player)
    }

    /** 查询快照：在线玩家取实时数据，离线玩家走缓存与磁盘。 */
    fun find(playerName: String): InventorySnapshot? {
        val name = playerName.trim()
        if (name.isEmpty()) return null
        val key = name.lowercase()

        val online = plugin.server.onlinePlayers.values.firstOrNull { it.name.equals(name, true) }
        if (online != null) {
            val live = onServerThread { captureOf(online) }
            if (live != null) {
                cache[key] = live
                return live
            }
        }

        cache[key]?.let { return it }
        return readFromDisk(name)?.also { cache[key] = it }
    }

    private fun captureAllOnline() {
        plugin.server.onlinePlayers.values.forEach { capture(it) }
    }

    private fun capture(player: Player) {
        val snapshot = try {
            captureOf(player)
        } catch (error: Throwable) {
            plugin.log_warning("采集 ${player.name} 的背包快照失败: ${error.message}")
            return
        }
        cache[player.name.lowercase()] = snapshot
        val target = File(directory, "${snapshot.playerName.lowercase()}.yml")
        writer.execute { writeAtomically(target, serialize(snapshot)) }
    }

    /** 必须回主线程执行：读取玩家背包不是线程安全的。 */
    private fun captureOf(player: Player): InventorySnapshot {
        val inventory = player.inventory
        val storage = (0 until STORAGE_SIZE).map { inventory.getItem(it) }
        val armor = listOf(inventory.helmet, inventory.chestplate, inventory.leggings, inventory.boots)
        val offhand = player.offhandInventory.getItem(0)
        val enderChest = (0 until ENDER_CHEST_SIZE).map { player.enderChestInventory.getItem(it) }
        val skin = player.skin
        val skinImage = skin?.skinData
        return InventorySnapshot(
            playerName = player.name,
            storage = storage,
            armor = armor,
            offhand = offhand,
            enderChest = enderChest,
            skinData = skinImage?.data,
            skinWidth = skinImage?.width ?: 64,
            skinHeight = skinImage?.height ?: 64,
            skinSlim = SkinGeometry.isSlim(skin?.geometryData)
        )
    }

    private fun <T> onServerThread(block: () -> T): T? {
        if (plugin.server.isPrimaryThread) {
            return try {
                block()
            } catch (error: Throwable) {
                plugin.log_warning("主线程读取背包失败: ${error.message}")
                null
            }
        }
        val future = CompletableFuture<T>()
        plugin.submit {
            try {
                future.complete(block())
            } catch (error: Throwable) {
                future.completeExceptionally(error)
            }
        }
        return try {
            future.get(SYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } catch (error: Exception) {
            plugin.log_warning("等待主线程返回背包数据超时: ${error.message}")
            null
        }
    }

    // ---------------------------------------------------------------- 序列化

    private fun serialize(snapshot: InventorySnapshot): String {
        val root = linkedMapOf<String, Any?>(
            "schema-version" to SCHEMA_VERSION,
            "player" to snapshot.playerName,
            "captured-at" to System.currentTimeMillis(),
            "storage" to snapshot.storage.map { encodeItem(it) },
            "armor" to snapshot.armor.map { encodeItem(it) },
            "offhand" to encodeItem(snapshot.offhand),
            "ender-chest" to snapshot.enderChest.map { encodeItem(it) }
        )
        return Yaml(DumperOptions().apply { defaultFlowStyle = DumperOptions.FlowStyle.BLOCK }).dump(root)
    }

    private fun readFromDisk(playerName: String): InventorySnapshot? {
        val file = File(directory, "${playerName.lowercase()}.yml")
        if (!file.isFile || file.length() > MAX_SNAPSHOT_BYTES) return null
        return try {
            @Suppress("UNCHECKED_CAST")
            val root = Yaml().load<Map<String, Any?>>(file.readText(Charsets.UTF_8)) ?: return null
            if ((root["schema-version"] as? Number)?.toInt() != SCHEMA_VERSION) return null
            val name = root["player"]?.toString()?.takeIf { it.isNotBlank() } ?: return null
            InventorySnapshot(
                playerName = name,
                storage = decodeList(root["storage"], STORAGE_SIZE),
                armor = decodeList(root["armor"], ARMOR_SIZE),
                offhand = decodeItem(root["offhand"]?.toString()),
                enderChest = decodeList(root["ender-chest"], ENDER_CHEST_SIZE)
            )
        } catch (error: Exception) {
            plugin.log_warning("读取背包快照 $playerName 失败: ${error.message}")
            null
        }
    }

    private fun loadExisting() {
        val files = directory.listFiles { file -> file.isFile && file.extension.equals("yml", true) } ?: return
        for (file in files) {
            val playerName = file.nameWithoutExtension
            if (!PLAYER_NAME.matches(playerName)) continue
            readFromDisk(playerName)?.let { cache[playerName.lowercase()] = it }
        }
        if (files.isNotEmpty()) plugin.log_info("已加载 ${cache.size} 份离线背包快照")
    }

    private fun writeAtomically(target: File, content: String) {
        val temporary = File(target.parentFile, "${target.name}.tmp")
        try {
            temporary.writeText(content, Charsets.UTF_8)
            try {
                Files.move(
                    temporary.toPath(), target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (error: Exception) {
            plugin.log_warning("写入背包快照 ${target.name} 失败: ${error.message}")
            temporary.delete()
        }
    }

    // ---------------------------------------------------------------- 物品编解码

    private fun decodeList(raw: Any?, expectedSize: Int): List<Item?> {
        val entries = raw as? List<*> ?: return List(expectedSize) { null }
        return (0 until expectedSize).map { index -> decodeItem(entries.getOrNull(index)?.toString()) }
    }

    /** 编码为 `id damage count base64(nbt)`；空槽为 null。 */
    private fun encodeItem(item: Item?): String? {
        if (InventorySnapshot.isAir(item)) return null
        val target = item ?: return null
        return try {
            val nbt = target.namedTag?.let { Base64.getEncoder().encodeToString(target.writeCompoundTag(it)) } ?: ""
            "${target.id} ${target.damage} ${target.count} $nbt"
        } catch (error: Exception) {
            plugin.log_warning("序列化物品失败(${target.id}): ${error.message}")
            "${target.id} ${target.damage} ${target.count} "
        }
    }

    private fun decodeItem(encoded: String?): Item? {
        if (encoded.isNullOrBlank()) return null
        val parts = encoded.trim().split(' ')
        val id = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val damage = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val count = parts.getOrNull(2)?.toIntOrNull() ?: 1
        val item = try {
            Item.get(id, damage, count)
        } catch (error: Exception) {
            plugin.log_warning("还原物品失败($encoded): ${error.message}")
            return null
        }
        val nbt = parts.getOrNull(3)?.takeIf { it.isNotBlank() } ?: return item
        return try {
            // Item.setCompoundTag(byte[]) 直接吃 NBT 字节，省一次手动解析。
            item.setCompoundTag(Base64.getDecoder().decode(nbt))
            item
        } catch (error: Exception) {
            // NBT 还原失败时退化成「同 id/meta/数量的普通物品」，总比整个背包读不出来好。
            plugin.log_warning("还原物品 NBT 失败($id:$damage): ${error.message}")
            item
        }
    }

    private companion object {
        const val SCHEMA_VERSION = 1
        const val STORAGE_SIZE = 36
        const val ARMOR_SIZE = 4
        const val ENDER_CHEST_SIZE = 27

        /** 5 分钟一次全量采集，与 Spigot 适配器保持一致。 */
        const val PERIODIC_CAPTURE_TICKS = 6000L

        const val SYNC_TIMEOUT_SECONDS = 10L
        const val MAX_SNAPSHOT_BYTES = 4L * 1024 * 1024

        val PLAYER_NAME = Regex("[A-Za-z0-9_]{1,16}")
    }
}
