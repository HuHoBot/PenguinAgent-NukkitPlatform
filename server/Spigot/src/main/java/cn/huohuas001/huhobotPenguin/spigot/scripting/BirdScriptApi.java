package cn.huohuas001.huhobotPenguin.spigot.scripting;

import cn.huohuas001.huhobotPenguin.spigot.HuHoBotSpigot;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Server;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.CommandMap;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/**
 * Scripting facade exposed to JS/Python/Lua addons as the global {@code Bird}.
 *
 * Ported from BirdLibraryApi's {@code BirdAPI}
 * (https://github.com/prach1121/birdlibraryapi, Apache-2.0) and adapted for
 * Spigot 1.16+ (no Adventure API) plus HuHoBot addon registration.
 */
public class BirdScriptApi {

    private final HuHoBotSpigot plugin;
    private final String scriptName;
    /** GraalJS hands functions over as raw values; this turns them into the interface a method declared. */
    private volatile java.lang.reflect.Method jsAdapter;
    private final Listener dummyListener = new Listener() {};
    private final List<Runnable> cleanupTasks = new ArrayList<>();
    /** QQ bot commands this script registered; dropped on reload so keys do not leak. */
    private final java.util.Set<String> botCommandKeys = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private static CommandMap commandMap;

    private static final ConcurrentHashMap<String, Long> cooldowns = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, CopyOnWriteArrayList<Consumer<Object>>> customEventBus = new ConcurrentHashMap<>();

    private final File dataFile;
    private final Properties data = new Properties();
    private boolean dataLoaded = false;

    private final File filesFolder;

    private static final Set<String> BLOCKED_EXTENSIONS = Set.of(
            "exe", "bat", "cmd", "dll", "so", "autorun", "ps1", "ps2", "psm1",
            "php", "sh", "bash", "vbs", "vbe", "wsf", "wsh", "jse", "jar", "msi", "scr"
    );

    public BirdScriptApi(HuHoBotSpigot plugin, String scriptName, File addonsFolder) {
        this.plugin = plugin;
        this.scriptName = scriptName;

        String baseName = baseName(scriptName);
        File dataFolder = new File(addonsFolder, "data");
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        this.dataFile = new File(dataFolder, baseName + ".properties");
        this.filesFolder = new File(new File(addonsFolder, "files"), baseName);
    }

    /** File name without the language extension, used as the HuHoBot addon name. */
    public String addonName() {
        return baseName(scriptName);
    }

    private static String baseName(String scriptName) {
        String lower = scriptName.toLowerCase();
        if (lower.endsWith(".js") || lower.endsWith(".lua") || lower.endsWith(".py")) {
            return scriptName.substring(0, scriptName.lastIndexOf('.'));
        }
        return scriptName;
    }

    /**
     * GraalJS keeps a script function as its own value object instead of converting it,
     * because its default conversion only ever produces {@code java.util.function.Function}
     * and that breaks callbacks with any other shape. When such a value reaches a method
     * declared with a functional interface, adapt it to exactly that interface.
     * Lua and Python already arrive as the right type and pass through untouched.
     */
    private Object adapt(Object value, Class<?> type) {
        if (value == null || type == null || !type.isInterface() || type.isInstance(value)) return value;
        java.lang.reflect.Method adapter = jsAdapter;
        if (adapter == null) {
            try {
                Class<?> bridge = Class.forName("cn.huohuas001.huhobot.graaljs.GraalJsBridge", false, value.getClass().getClassLoader());
                adapter = bridge.getMethod("adapt", Object.class, Class.class);
                jsAdapter = adapter;
            } catch (ClassNotFoundException absent) {
                return value;
            } catch (ReflectiveOperationException error) {
                warn("无法适配 JavaScript 回调: " + error.getMessage());
                return value;
            }
        }
        try {
            return adapter.invoke(null, value, type);
        } catch (ReflectiveOperationException error) {
            warn("无法适配 JavaScript 回调: " + error.getMessage());
            return value;
        }
    }

    public void log(Object message) {
        plugin.getLogger().info("[" + scriptName + "] " + message);
    }

    public void warn(Object message) {
        plugin.getLogger().warning("[" + scriptName + "] " + message);
    }

    // ---------------------------------------------------------------- events

    public void onEvent(String eventClassName, String priority, EventCallback callback) {
        EventCallback handler = (EventCallback) adapt(callback, EventCallback.class);
        try {
            @SuppressWarnings("unchecked")
            Class<? extends Event> eventClass = (Class<? extends Event>) Class.forName(eventClassName);

            EventPriority p;
            try {
                p = EventPriority.valueOf(priority.toUpperCase());
            } catch (Exception ex) {
                p = EventPriority.NORMAL;
            }

            EventExecutor executor = (listener, event) -> {
                if (eventClass.isInstance(event)) {
                    try {
                        handler.execute(event);
                    } catch (Throwable t) {
                        warn("Runtime error in event handler (" + eventClass.getSimpleName() + "): "
                                + (t.getMessage() != null ? t.getMessage() : t));
                        t.printStackTrace();
                    }
                }
            };

            plugin.getServer().getPluginManager()
                    .registerEvent(eventClass, dummyListener, p, executor, plugin, false);

            cleanupTasks.add(() -> HandlerList.unregisterAll(dummyListener));
            log("Registered event listener: " + eventClassName + " (priority=" + p + ")");
        } catch (ClassNotFoundException e) {
            warn("Unknown event class: " + eventClassName
                    + " (use the fully-qualified name, e.g. org.bukkit.event.player.PlayerJoinEvent)");
        }
    }

    public void onEvent(String eventClassName, EventCallback callback) {
        onEvent(eventClassName, "NORMAL", callback);
    }

    // ---------------------------------------------------------------- commands

    public void onCommand(String name, CommandCallback callback) {
        onCommand(name, null, callback, null);
    }

    public void onCommand(String name, String permission, CommandCallback callback) {
        onCommand(name, permission, callback, null);
    }

    public void onCommand(String name, CommandCallback callback, TabCompleteCallback tabCompleteCallback) {
        onCommand(name, null, callback, tabCompleteCallback);
    }

    public void onCommand(String name, String permission, CommandCallback callback, TabCompleteCallback tabCompleteCallback) {
        callback = (CommandCallback) adapt(callback, CommandCallback.class);
        tabCompleteCallback = (TabCompleteCallback) adapt(tabCompleteCallback, TabCompleteCallback.class);
        CommandMap map = getCommandMap();
        if (map == null) {
            warn("Could not access the CommandMap - command /" + name + " will not work");
            return;
        }
        BirdDynamicCommand cmd = new BirdDynamicCommand(name, callback);
        if (permission != null && !permission.isEmpty()) {
            cmd.setPermission(permission);
        }
        if (tabCompleteCallback != null) {
            cmd.setTabCompleteCallback(tabCompleteCallback);
        }
        map.register(plugin.getName().toLowerCase(), cmd);
        cleanupTasks.add(() -> removeCommand(map, cmd));
        log("Registered command: /" + name + (permission != null ? " (permission=" + permission + ")" : ""));
    }

    /**
     * Removes a dynamically registered command for real. Bukkit's
     * {@code Command.unregister(CommandMap)} only detaches the map from the command
     * and leaves it in {@code SimpleCommandMap#knownCommands}, so after a reload the
     * stale copy still answers the name and points at a closed script context.
     */
    @SuppressWarnings("unchecked")
    private static void removeCommand(CommandMap map, org.bukkit.command.Command command) {
        try {
            java.lang.reflect.Method known = map.getClass().getMethod("getKnownCommands");
            Map<String, org.bukkit.command.Command> commands = (Map<String, org.bukkit.command.Command>) known.invoke(map);
            commands.values().removeIf(candidate -> candidate == command);
        } catch (Throwable t) {
            command.unregister(map);
        }
    }

    // ---------------------------------------------------------------- scheduler

    public int runTask(Runnable task) {
        BukkitTask t = plugin.getServer().getScheduler().runTask(plugin, wrap(task));
        trackTask(t);
        return t.getTaskId();
    }

    public int runTaskLater(Runnable task, long delayTicks) {
        BukkitTask t = plugin.getServer().getScheduler().runTaskLater(plugin, wrap(task), delayTicks);
        trackTask(t);
        return t.getTaskId();
    }

    public int runTaskTimer(Runnable task, long delayTicks, long periodTicks) {
        BukkitTask t = plugin.getServer().getScheduler().runTaskTimer(plugin, wrap(task), delayTicks, periodTicks);
        trackTask(t);
        return t.getTaskId();
    }

    public int runTaskAsync(Runnable task) {
        BukkitTask t = plugin.getServer().getScheduler().runTaskAsynchronously(plugin, wrap(task));
        trackTask(t);
        return t.getTaskId();
    }

    public int runTaskTimerAsync(Runnable task, long delayTicks, long periodTicks) {
        BukkitTask t = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, wrap(task), delayTicks, periodTicks);
        trackTask(t);
        return t.getTaskId();
    }

    public void cancelTask(int taskId) {
        plugin.getServer().getScheduler().cancelTask(taskId);
    }

    private Runnable wrap(Runnable task) {
        Runnable adapted = (Runnable) adapt(task, Runnable.class);
        return () -> {
            try {
                adapted.run();
            } catch (Throwable t) {
                warn("Runtime error in scheduled task: " + (t.getMessage() != null ? t.getMessage() : t));
                t.printStackTrace();
            }
        };
    }

    private void trackTask(BukkitTask t) {
        cleanupTasks.add(() -> {
            try {
                t.cancel();
            } catch (Exception ignored) {
            }
        });
    }

    // ---------------------------------------------------------------- chat

    public void broadcast(String message) {
        plugin.getServer().broadcastMessage(colorize(message));
    }

    public void broadcast(String message, String permission) {
        String colored = colorize(message);
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (p.hasPermission(permission)) {
                p.sendMessage(colored);
            }
        }
    }

    public void broadcastChat(String message) {
        broadcast(message);
    }

    public void broadcastChat(String message, String permission) {
        broadcast(message, permission);
    }

    public void tell(Player player, String message) {
        if (player != null) {
            player.sendMessage(colorize(message));
        }
    }

    public boolean tell(String playerName, String message) {
        Player player = getPlayer(playerName);
        if (player == null) {
            return false;
        }
        player.sendMessage(colorize(message));
        return true;
    }

    public String colorize(String message) {
        return message == null ? null : ChatColor.translateAlternateColorCodes('&', message);
    }

    public void sendTitle(Player player, String title, String subtitle, int fadeInTicks, int stayTicks, int fadeOutTicks) {
        if (player == null) return;
        player.sendTitle(colorize(title), colorize(subtitle), ticks(fadeInTicks), ticks(stayTicks), ticks(fadeOutTicks));
    }

    public void sendTitle(Player player, String title, String subtitle) {
        sendTitle(player, title, subtitle, -1, -1, -1);
    }

    public void broadcastTitle(String title, String subtitle, int fadeInTicks, int stayTicks, int fadeOutTicks) {
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            sendTitle(p, title, subtitle, fadeInTicks, stayTicks, fadeOutTicks);
        }
    }

    public void broadcastTitle(String title, String subtitle) {
        broadcastTitle(title, subtitle, -1, -1, -1);
    }

    public void broadcastTitle(String title, String subtitle, String permission) {
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (p.hasPermission(permission)) {
                sendTitle(p, title, subtitle, -1, -1, -1);
            }
        }
    }

    private int ticks(int value) {
        return value >= 0 ? value : 10;
    }

    public void sendActionBar(Player player, String message) {
        if (player != null) {
            player.sendMessage(colorize(message));
        }
    }

    public void broadcastActionBar(String message) {
        broadcast(message);
    }

    public void broadcastActionBar(String message, String permission) {
        broadcast(message, permission);
    }

    // ---------------------------------------------------------------- players

    public Player getPlayer(String name) {
        return plugin.getServer().getPlayer(name);
    }

    public Player getPlayerExact(String name) {
        return plugin.getServer().getPlayerExact(name);
    }

    public List<Player> getOnlinePlayers() {
        return new ArrayList<>(plugin.getServer().getOnlinePlayers());
    }

    public Server getServer() {
        return plugin.getServer();
    }

    // ---------------------------------------------------------------- data store

    private void ensureLoaded() {
        if (dataLoaded) return;
        dataLoaded = true;
        if (dataFile.exists()) {
            try (InputStreamReader reader = new InputStreamReader(new FileInputStream(dataFile), StandardCharsets.UTF_8)) {
                data.load(reader);
            } catch (IOException e) {
                warn("Failed to load stored data (" + dataFile.getName() + "): " + e.getMessage());
            }
        }
    }

    public void setData(String key, String value) {
        ensureLoaded();
        data.setProperty(key, value);
        saveData();
    }

    public String getData(String key) {
        return getData(key, null);
    }

    public String getData(String key, String defaultValue) {
        ensureLoaded();
        return data.getProperty(key, defaultValue);
    }

    public void removeData(String key) {
        ensureLoaded();
        data.remove(key);
        saveData();
    }

    public List<String> getDataKeys() {
        ensureLoaded();
        return new ArrayList<>(data.stringPropertyNames());
    }

    public void saveData() {
        ensureLoaded();
        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(dataFile), StandardCharsets.UTF_8)) {
            data.store(writer, "HuHoBot script data for " + scriptName);
        } catch (IOException e) {
            warn("Failed to save stored data: " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------- items / effects

    public void giveItem(Player player, String materialName) {
        giveItem(player, materialName, 1, null, null);
    }

    public void giveItem(Player player, String materialName, int amount) {
        giveItem(player, materialName, amount, null, null);
    }

    public void giveItem(Player player, String materialName, int amount, String displayName) {
        giveItem(player, materialName, amount, displayName, null);
    }

    public void giveItem(Player player, String materialName, int amount, String displayName, List<String> lore) {
        if (player == null) return;
        Material material = Material.matchMaterial(materialName);
        if (material == null) {
            warn("Unknown material: " + materialName);
            return;
        }
        ItemStack stack = new ItemStack(material, Math.max(1, amount));
        if (displayName != null || (lore != null && !lore.isEmpty())) {
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                if (displayName != null) meta.setDisplayName(colorize(displayName));
                if (lore != null && !lore.isEmpty()) {
                    List<String> colored = new ArrayList<>();
                    for (String line : lore) colored.add(colorize(line));
                    meta.setLore(colored);
                }
                stack.setItemMeta(meta);
            }
        }
        player.getInventory().addItem(stack);
    }

    public void playSound(Player player, String soundName) {
        playSound(player, soundName, 1f, 1f);
    }

    public void playSound(Player player, String soundName, float volume, float pitch) {
        if (player == null) return;
        Sound sound = soundByName(soundName);
        if (sound == null) {
            warn("Unknown sound: " + soundName);
            return;
        }
        player.playSound(player.getLocation(), sound, volume, pitch);
    }

    public void broadcastSound(String soundName, float volume, float pitch) {
        Sound sound = soundByName(soundName);
        if (sound == null) {
            warn("Unknown sound: " + soundName);
            return;
        }
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            p.playSound(p.getLocation(), sound, volume, pitch);
        }
    }

    private Sound soundByName(String soundName) {
        try {
            return Sound.valueOf(soundName.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public void spawnParticle(Player player, String particleName, int count) {
        if (player == null) return;
        Particle particle;
        try {
            particle = Particle.valueOf(particleName.toUpperCase());
        } catch (IllegalArgumentException e) {
            warn("Unknown particle: " + particleName);
            return;
        }
        player.getWorld().spawnParticle(particle, player.getLocation(), Math.max(1, count));
    }

    public void teleport(Player player, double x, double y, double z) {
        if (player == null) return;
        player.teleport(new Location(player.getWorld(), x, y, z));
    }

    public void teleport(Player player, double x, double y, double z, String worldName) {
        if (player == null) return;
        World world = plugin.getServer().getWorld(worldName);
        if (world == null) {
            warn("Unknown world: " + worldName);
            return;
        }
        player.teleport(new Location(world, x, y, z));
    }

    public Block getBlock(String worldName, int x, int y, int z) {
        World world = plugin.getServer().getWorld(worldName);
        return world == null ? null : world.getBlockAt(x, y, z);
    }

    public String getBlockType(String worldName, int x, int y, int z) {
        Block block = getBlock(worldName, x, y, z);
        return block == null ? null : block.getType().name();
    }

    public boolean isBlockType(String worldName, int x, int y, int z, String materialName) {
        String type = getBlockType(worldName, x, y, z);
        return type != null && type.equalsIgnoreCase(materialName);
    }

    public boolean setBlockType(String worldName, int x, int y, int z, String materialName) {
        Block block = getBlock(worldName, x, y, z);
        Material material = Material.matchMaterial(materialName);
        if (block == null || material == null) {
            warn("Cannot set block: world=" + worldName + " material=" + materialName);
            return false;
        }
        block.setType(material);
        return true;
    }

    public Block getTargetBlock(Player player) {
        return getTargetBlock(player, 5);
    }

    @SuppressWarnings("deprecation")
    public Block getTargetBlock(Player player, int maxDistance) {
        if (player == null) return null;
        return player.getTargetBlock(null, Math.max(1, maxDistance));
    }

    public Block getBlockPlayerIsOn(Player player) {
        if (player == null) return null;
        return player.getLocation().getBlock().getRelative(BlockFace.DOWN);
    }

    // ---------------------------------------------------------------- cooldown

    public void setCooldown(String key, Player player, double seconds) {
        if (player == null) return;
        cooldowns.put(cooldownKey(key, player), System.currentTimeMillis() + (long) (seconds * 1000));
    }

    public boolean hasCooldown(String key, Player player) {
        return getCooldownRemaining(key, player) > 0;
    }

    public double getCooldownRemaining(String key, Player player) {
        if (player == null) return 0;
        Long until = cooldowns.get(cooldownKey(key, player));
        if (until == null) return 0;
        long left = until - System.currentTimeMillis();
        return left > 0 ? left / 1000.0 : 0;
    }

    public void clearCooldown(String key, Player player) {
        if (player == null) return;
        cooldowns.remove(cooldownKey(key, player));
    }

    private String cooldownKey(String key, Player player) {
        return scriptName + ":" + key + ":" + player.getUniqueId();
    }

    // ---------------------------------------------------------------- misc helpers

    public int random(int min, int max) {
        if (max <= min) return min;
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    public int getOnlineCount() {
        return plugin.getServer().getOnlinePlayers().size();
    }

    public int getMaxPlayers() {
        return plugin.getServer().getMaxPlayers();
    }

    public boolean isOnline(String playerName) {
        return getPlayer(playerName) != null;
    }

    public double getHealth(Player player) {
        return player == null ? 0 : player.getHealth();
    }

    @SuppressWarnings("deprecation")
    public void setHealth(Player player, double amount) {
        if (player == null) return;
        player.setHealth(Math.max(0, Math.min(amount, player.getMaxHealth())));
    }

    public void giveExp(Player player, int amount) {
        if (player != null) player.giveExp(amount);
    }

    public boolean setGameMode(Player player, String mode) {
        if (player == null) return false;
        try {
            player.setGameMode(GameMode.valueOf(mode.toUpperCase()));
            return true;
        } catch (IllegalArgumentException e) {
            warn("Unknown game mode: " + mode);
            return false;
        }
    }

    public String getGameMode(Player player) {
        return player == null ? null : player.getGameMode().name();
    }

    public void kick(Player player, String reason) {
        if (player != null) player.kickPlayer(colorize(reason));
    }

    public double getDistance(Player a, Player b) {
        if (a == null || b == null || a.getWorld() != b.getWorld()) return -1;
        return a.getLocation().distance(b.getLocation());
    }

    public String stripColor(String message) {
        return message == null ? null : ChatColor.stripColor(colorize(message));
    }

    public String formatTime(long totalSeconds) {
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    public List<String> getWorldNames() {
        List<String> names = new ArrayList<>();
        for (World world : plugin.getServer().getWorlds()) names.add(world.getName());
        return names;
    }

    public boolean hasItem(Player player, String materialName, int amount) {
        if (player == null) return false;
        Material material = Material.matchMaterial(materialName);
        if (material == null) return false;
        return player.getInventory().contains(material, Math.max(1, amount));
    }

    public List<String> getPlayerNames() {
        List<String> names = new ArrayList<>();
        for (Player p : plugin.getServer().getOnlinePlayers()) names.add(p.getName());
        return names;
    }

    public int getFood(Player player) {
        return player == null ? 0 : player.getFoodLevel();
    }

    public void setFood(Player player, int level) {
        if (player != null) player.setFoodLevel(Math.max(0, Math.min(20, level)));
    }

    public boolean addPotionEffect(Player player, String effectName, int seconds, int amplifier) {
        if (player == null) return false;
        PotionEffectType type = potionType(effectName);
        if (type == null) return false;
        player.addPotionEffect(new PotionEffect(type, Math.max(1, seconds) * 20, Math.max(0, amplifier)));
        return true;
    }

    public boolean removePotionEffect(Player player, String effectName) {
        if (player == null) return false;
        PotionEffectType type = potionType(effectName);
        if (type == null) return false;
        player.removePotionEffect(type);
        return true;
    }

    public boolean hasPotionEffect(Player player, String effectName) {
        if (player == null) return false;
        PotionEffectType type = potionType(effectName);
        return type != null && player.hasPotionEffect(type);
    }

    private PotionEffectType potionType(String effectName) {
        PotionEffectType type = PotionEffectType.getByName(effectName.toUpperCase());
        if (type == null) warn("Unknown potion effect: " + effectName);
        return type;
    }

    public long getWorldTime(String worldName) {
        World world = plugin.getServer().getWorld(worldName);
        return world == null ? -1 : world.getTime();
    }

    public boolean setWorldTime(String worldName, long time) {
        World world = plugin.getServer().getWorld(worldName);
        if (world == null) {
            warn("Unknown world: " + worldName);
            return false;
        }
        world.setTime(time);
        return true;
    }

    public boolean setWeather(String worldName, boolean storm) {
        World world = plugin.getServer().getWorld(worldName);
        if (world == null) {
            warn("Unknown world: " + worldName);
            return false;
        }
        world.setStorm(storm);
        return true;
    }

    public boolean isStorming(String worldName) {
        World world = plugin.getServer().getWorld(worldName);
        return world != null && world.hasStorm();
    }

    public boolean isNight(String worldName) {
        World world = plugin.getServer().getWorld(worldName);
        if (world == null) return false;
        long time = world.getTime() % 24000;
        return time >= 13000 && time <= 23000;
    }

    public int getPing(Player player) {
        if (player == null) return -1;
        try {
            return (int) player.getClass().getMethod("getPing").invoke(player);
        } catch (ReflectiveOperationException e) {
            return -1;
        }
    }

    public String getPlayerUUID(Player player) {
        return player == null ? null : player.getUniqueId().toString();
    }

    public void setDisplayName(Player player, String name) {
        if (player != null) player.setDisplayName(colorize(name));
    }

    public String getDisplayName(Player player) {
        return player == null ? null : player.getDisplayName();
    }

    public void clearInventory(Player player) {
        if (player != null) player.getInventory().clear();
    }

    @SuppressWarnings("deprecation")
    public String getItemInHand(Player player) {
        if (player == null) return null;
        ItemStack item = player.getInventory().getItemInMainHand();
        return item == null ? "AIR" : item.getType().name();
    }

    public boolean removeItem(Player player, String materialName, int amount) {
        if (player == null) return false;
        Material material = Material.matchMaterial(materialName);
        if (material == null) {
            warn("Unknown material: " + materialName);
            return false;
        }
        int left = Math.max(1, amount);
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length && left > 0; i++) {
            ItemStack stack = contents[i];
            if (stack == null || stack.getType() != material) continue;
            int take = Math.min(left, stack.getAmount());
            left -= take;
            if (stack.getAmount() - take <= 0) {
                contents[i] = null;
            } else {
                stack.setAmount(stack.getAmount() - take);
            }
        }
        if (left > 0) return false;
        player.getInventory().setContents(contents);
        return true;
    }

    public List<Player> getNearbyPlayers(Player player, double radius) {
        List<Player> result = new ArrayList<>();
        if (player == null) return result;
        for (Player other : player.getWorld().getPlayers()) {
            if (other != player && other.getLocation().distanceSquared(player.getLocation()) <= radius * radius) {
                result.add(other);
            }
        }
        return result;
    }

    public void strikeLightning(String worldName, double x, double y, double z) {
        strikeLightning(worldName, x, y, z, false);
    }

    public void strikeLightning(String worldName, double x, double y, double z, boolean visualOnly) {
        World world = plugin.getServer().getWorld(worldName);
        if (world == null) {
            warn("Unknown world: " + worldName);
            return;
        }
        Location loc = new Location(world, x, y, z);
        if (visualOnly) {
            world.strikeLightningEffect(loc);
        } else {
            world.strikeLightning(loc);
        }
    }

    // ---------------------------------------------------------------- custom events

    @SuppressWarnings("unchecked")
    public void on(String eventName, Consumer<Object> callback) {
        Consumer<Object> listener = (Consumer<Object>) adapt(callback, Consumer.class);
        customEventBus.computeIfAbsent(eventName, k -> new CopyOnWriteArrayList<>()).add(listener);
        cleanupTasks.add(() -> {
            CopyOnWriteArrayList<Consumer<Object>> list = customEventBus.get(eventName);
            if (list != null) list.remove(listener);
        });
    }

    public void emit(String eventName, Object data) {
        CopyOnWriteArrayList<Consumer<Object>> list = customEventBus.get(eventName);
        if (list == null) return;
        for (Consumer<Object> c : list) {
            try {
                c.accept(data);
            } catch (Throwable t) {
                warn("Runtime error in custom event handler (" + eventName + "): "
                        + (t.getMessage() != null ? t.getMessage() : t));
                t.printStackTrace();
            }
        }
    }

    // ---------------------------------------------------------------- http

    public void fetch(String url, Consumer<HttpResult> callback) {
        fetchInternal(url, "GET", null, null, callback);
    }

    public void fetch(String url, String method, Consumer<HttpResult> callback) {
        fetchInternal(url, method, null, null, callback);
    }

    public void fetch(String url, String method, String body, Consumer<HttpResult> callback) {
        fetchInternal(url, method, body, null, callback);
    }

    public void fetch(String url, String method, String body, Map<String, String> headers, Consumer<HttpResult> callback) {
        fetchInternal(url, method, body, headers, callback);
    }

    @SuppressWarnings("unchecked")
    private void fetchInternal(String url, String method, String body, Map<String, String> headers, Consumer<HttpResult> callback) {
        Consumer<HttpResult> listener = (Consumer<HttpResult>) adapt(callback, Consumer.class);
        BukkitTask task = plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            HttpResult result;
            try {
                HttpClient client = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(10))
                        .build();
                HttpRequest.Builder builder = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(10));

                boolean hasContentType = false;
                if (headers != null) {
                    for (Map.Entry<String, String> e : headers.entrySet()) {
                        builder.header(e.getKey(), e.getValue());
                        if (e.getKey().equalsIgnoreCase("Content-Type")) hasContentType = true;
                    }
                }

                String m = (method == null || method.isEmpty()) ? "GET" : method.toUpperCase();
                HttpRequest.BodyPublisher publisher = body != null
                        ? HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)
                        : HttpRequest.BodyPublishers.noBody();
                if (body != null && !hasContentType) {
                    builder.header("Content-Type", "application/json");
                }
                builder.method(m, publisher);

                HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
                result = new HttpResult(response.statusCode(), response.body());
            } catch (Exception e) {
                result = new HttpResult(0, "Request failed: " + e.getMessage());
            }

            HttpResult finalResult = result;
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                try {
                    listener.accept(finalResult);
                } catch (Throwable t) {
                    warn("Runtime error in fetch callback: " + (t.getMessage() != null ? t.getMessage() : t));
                    t.printStackTrace();
                }
            });
        });
        trackTask(task);
    }

    public void sendDiscordWebhook(String webhookUrl, String message) {
        sendDiscordWebhook(webhookUrl, message, null, null);
    }

    public void sendDiscordWebhook(String webhookUrl, String message, String username, String avatarUrl) {
        StringBuilder json = new StringBuilder("{\"content\":\"").append(jsonEscape(message)).append("\"");
        if (username != null) json.append(",\"username\":\"").append(jsonEscape(username)).append("\"");
        if (avatarUrl != null) json.append(",\"avatar_url\":\"").append(jsonEscape(avatarUrl)).append("\"");
        json.append("}");
        fetchInternal(webhookUrl, "POST", json.toString(), null, result -> {
            if (!result.ok) {
                warn("Discord webhook failed (" + result.status + "): " + result.body);
            }
        });
    }

    public void sendDiscordEmbed(String webhookUrl, String title, String description, String colorHex) {
        int color = 0x3498db;
        if (colorHex != null) {
            try {
                color = Integer.parseInt(colorHex.replace("#", ""), 16);
            } catch (NumberFormatException ignored) {
            }
        }
        String json = "{\"embeds\":[{\"title\":\"" + jsonEscape(title)
                + "\",\"description\":\"" + jsonEscape(description)
                + "\",\"color\":" + color + "}]}";
        fetchInternal(webhookUrl, "POST", json, null, result -> {
            if (!result.ok) {
                warn("Discord embed webhook failed (" + result.status + "): " + result.body);
            }
        });
    }

    private String jsonEscape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "");
    }

    // ---------------------------------------------------------------- files

    public boolean saveFile(String filename, String content) {
        File target = resolveFile(filename);
        if (target == null) return false;
        try {
            File parent = target.getParentFile();
            if (parent != null) parent.mkdirs();
            try (OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(target), StandardCharsets.UTF_8)) {
                w.write(content != null ? content : "");
            }
            return true;
        } catch (IOException e) {
            warn("Failed to save file \"" + filename + "\": " + e.getMessage());
            return false;
        }
    }

    public String readFile(String filename) {
        File target = resolveFile(filename);
        if (target == null || !target.isFile()) return null;
        try {
            return new String(java.nio.file.Files.readAllBytes(target.toPath()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            warn("Failed to read file \"" + filename + "\": " + e.getMessage());
            return null;
        }
    }

    public boolean fileExists(String filename) {
        File target = resolveFile(filename);
        return target != null && target.isFile();
    }

    public boolean deleteFile(String filename) {
        File target = resolveFile(filename);
        return target != null && target.delete();
    }

    public List<String> listFiles() {
        List<String> result = new ArrayList<>();
        if (filesFolder.isDirectory()) {
            String[] names = filesFolder.list();
            if (names != null) {
                for (String n : names) result.add(n);
            }
        }
        return result;
    }

    private File resolveFile(String filename) {
        if (filename == null || filename.trim().isEmpty()) {
            warn("File name cannot be empty");
            return null;
        }
        int dot = filename.lastIndexOf('.');
        String ext = dot >= 0 ? filename.substring(dot + 1).toLowerCase() : "";
        if (BLOCKED_EXTENSIONS.contains(ext)) {
            warn("Blocked file extension \"." + ext + "\" - not allowed for security reasons: " + filename);
            return null;
        }
        try {
            if (!filesFolder.exists()) filesFolder.mkdirs();
            File target = new File(filesFolder, filename);
            String basePath = filesFolder.getCanonicalPath();
            String targetPath = target.getCanonicalPath();
            if (!targetPath.equals(basePath) && !targetPath.startsWith(basePath + File.separator)) {
                warn("Invalid file path (must stay inside your script's own file folder): " + filename);
                return null;
            }
            return target;
        } catch (IOException e) {
            warn("Invalid file path: " + filename);
            return null;
        }
    }

    // ---------------------------------------------------------------- waits / commands

    public void sleep(double seconds) {
        sleepMillis((long) (seconds * 1000));
    }

    public void waitTicks(int ticks) {
        sleepMillis(ticks * 50L);
    }

    private void sleepMillis(long millis) {
        if (Bukkit.isPrimaryThread()) {
            warn("Bird.sleep()/waitTicks() was called on the main thread - refusing, since it would freeze the whole server. "
                    + "Call it from inside Bird.runTaskAsync(function() { ... }) instead.");
            return;
        }
        try {
            Thread.sleep(Math.max(0, millis));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void runCommand(String command) {
        String cmd = command.startsWith("/") ? command.substring(1) : command;
        plugin.getServer().getScheduler().runTask(plugin, () ->
                plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), cmd));
    }

    public void runCommandAs(Player player, String command) {
        if (player == null) return;
        String cmd = command.startsWith("/") ? command.substring(1) : command;
        plugin.getServer().getScheduler().runTask(plugin, () -> player.performCommand(cmd));
    }

    // ---------------------------------------------------------------- HuHoBot addon bridge

    /**
     * Registers this script as a HuHoBot addon. Called automatically when the
     * script finishes loading; scripts may call it again to override metadata.
     */
    public boolean registerAddon(String name, String version, String description, String author) {
        return plugin.registerAddon(name, version, description, author);
    }

    public boolean registerAddon(String name, String version, String description) {
        return registerAddon(name, version, description, scriptName);
    }

    public boolean registerAddon(String name) {
        return registerAddon(name, "1.0.0", "script addon", scriptName);
    }

    /**
     * Registers a QQ-side bot command owned by an addon. The command template
     * is a server command, same as the Java addon API.
     */
    public boolean registerBotCommand(String addonName, String key, String command, int permission, boolean pushMenu) {
        boolean registered = plugin.registerBotCommand(addonName, key, command, permission, pushMenu);
        if (registered && key != null) {
            botCommandKeys.add(key.trim());
        }
        return registered;
    }

    public boolean registerBotCommand(String addonName, String key, String command) {
        return registerBotCommand(addonName, key, command, 0, true);
    }

    public boolean unregisterBotCommand(String key) {
        if (key != null) botCommandKeys.remove(key.trim());
        return plugin.unregisterBotCommand(key);
    }

    /** Sends plain text to every configured QQ group. */
    public void sendBotText(String text) {
        plugin.sendBotText(text);
    }

    /** Sends markdown to every configured QQ group. */
    public void sendBotMarkdown(String markdown) {
        plugin.sendBotMarkdown(markdown);
    }

    public void unregisterAll() {
        for (Runnable r : cleanupTasks) {
            try {
                r.run();
            } catch (Exception ignored) {
            }
        }
        cleanupTasks.clear();
        for (String key : new ArrayList<>(botCommandKeys)) {
            try {
                plugin.unregisterBotCommand(key);
            } catch (Exception ignored) {
            }
        }
        botCommandKeys.clear();
    }

    private static CommandMap getCommandMap() {
        if (commandMap != null) return commandMap;
        try {
            Field f = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            f.setAccessible(true);
            commandMap = (CommandMap) f.get(Bukkit.getServer());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return commandMap;
    }
}
