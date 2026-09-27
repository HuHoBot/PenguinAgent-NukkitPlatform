package cn.huohuas001.huhobotPenguin.nukkit.scripting;

import cn.huohuas001.huhobotPenguin.nukkit.HuHoBotNukkit;
import cn.nukkit.event.Event;
import cn.nukkit.event.EventPriority;
import cn.nukkit.event.Listener;
import cn.nukkit.plugin.EventExecutor;
import party.iroiro.luajava.Lua;
import party.iroiro.luajava.lua54.Lua54;
import party.iroiro.luajava.value.LuaValue;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One Lua 5.4 addon, wired the way NuclearScripting does it
 * (https://www.minebbs.com/resources/nuclearscripting-lua-nukkit.7780/):
 * a function named {@code on<Event>} listens for that Nukkit event, where
 * {@code <Event>} is the simple class name with the trailing {@code Event}
 * removed, and {@code onEnable(instance)} runs after the file loads.
 *
 * The engine is LuaJava's Lua 5.4 (party.iroiro.luajava), so scripts can
 * reach any Nukkit class through the {@code java} library.
 */
public final class LuaScriptEngine {

    private static final String[] EVENT_PACKAGES = {
            "cn.nukkit.event.player.",
            "cn.nukkit.event.block.",
            "cn.nukkit.event.entity.",
            "cn.nukkit.event.inventory.",
            "cn.nukkit.event.level.",
            "cn.nukkit.event.server.",
            "cn.nukkit.event.plugin.",
            "cn.nukkit.event."
    };

    private final HuHoBotNukkit plugin;
    private final ScriptPackage pkg;
    private final File file;
    private final String name;
    private final Lua lua;
    private final Listener listener = new Listener() {};
    private final Set<String> botCommandKeys = ConcurrentHashMap.newKeySet();
    private boolean loaded;

    public LuaScriptEngine(HuHoBotNukkit plugin, ScriptPackage pkg) {
        this.plugin = plugin;
        this.pkg = pkg;
        this.file = pkg.entryFile();
        this.name = pkg.name();
        this.lua = new Lua54();
    }

    public String name() {
        return name;
    }

    public boolean load() {
        try {
            lua.openLibraries();
            lua.set("plugin", plugin);
            lua.set("server", plugin.getServer());
            lua.set("logger", plugin.getLogger());
            lua.set("SCRIPT_NAME", name);
            // AstrBot 式注入：schema 驱动的配置、插件级 KV 存储、数据目录。
            lua.set("config", pkg.config());
            lua.set("kv", pkg.kv());
            lua.set("DATA_DIR", pkg.dataDir().getAbsolutePath());
            // 登记表由 Java 持有：Lua 侧只负责调用，重载时 Java 按这份表反注册。
            lua.set("botCommands", new BotCommandBridge());

            byte[] source = Files.readAllBytes(file.toPath());
            // LuaJava 只接受直接缓冲区，heap buffer 会抛 Expecting a direct buffer。
            ByteBuffer buffer = ByteBuffer.allocateDirect(source.length);
            buffer.put(source).flip();
            lua.run(buffer, file.getName());
            loaded = true;

            // 元数据以 metadata.yaml 为准；PLUGIN_* 全局变量仍可覆盖。
            String version = readString("PLUGIN_VERSION", pkg.manifest().version());
            String author = readString("PLUGIN_AUTHOR", pkg.manifest().author());
            String fallbackDescription = pkg.manifest().description().isEmpty()
                    ? "Lua 5.4 script addon" : pkg.manifest().description();
            String description = readString("PLUGIN_DESCRIPTION", fallbackDescription);
            plugin.registerAddon(name, version, description, author);

            bindEvents();
            callIfPresent("onEnable", plugin);
            plugin.getLogger().info("已加载 Lua 脚本扩展: " + name + " v" + version);
            return true;
        } catch (Throwable error) {
            plugin.getLogger().error("[" + name + "] Lua 加载失败: " + error.getMessage());
            closeQuietly();
            return false;
        }
    }

    public void unload() {
        loaded = false;
        if (callIfPresent("onDisable", plugin)) {
            // hook ran; nothing else to do before tearing the state down
        }
        for (String key : new ArrayList<>(botCommandKeys)) {
            plugin.unregisterBotCommand(key);
        }
        botCommandKeys.clear();
        closeQuietly();
    }

    /**
     * Exposed to Lua as the global {@code botCommands}. Scripts call
     * {@code botCommands:register(key, command)} instead of touching the plugin,
     * so this engine can drop exactly the keys it registered.
     */
    public final class BotCommandBridge {
        public boolean register(String key, String command) {
            boolean registered = plugin.registerBotCommand(name, key, command, 0, true);
            if (registered && key != null) botCommandKeys.add(key.trim());
            return registered;
        }
    }

    /**
     * NuclearScripting's rule: a global function {@code onPlayerJoin} listens for
     * {@code PlayerJoinEvent}. The class is resolved by trying the common Nukkit
     * event packages, so scripts do not spell out the package.
     */
    private void bindEvents() {
        LuaValue globals = lua.eval("return _G")[0];
        for (Object key : new ArrayList<>(globals.keySet())) {
            String functionName = String.valueOf(key);
            if (!functionName.startsWith("on") || functionName.length() < 3) continue;
            if ("onEnable".equals(functionName) || "onDisable".equals(functionName)) continue;
            LuaValue function = globals.get(functionName);
            if (function.type() != Lua.LuaType.FUNCTION) continue;

            String eventSimpleName = functionName.substring(2) + "Event";
            Class<? extends Event> eventClass = findEvent(eventSimpleName);
            if (eventClass == null) continue;

            EventExecutor executor = (ignored, event) -> {
                if (!loaded || !eventClass.isInstance(event)) return;
                try {
                    function.call(event);
                } catch (Throwable error) {
                    plugin.getLogger().warning("[" + name + "] 事件 " + eventClass.getSimpleName()
                            + " 处理失败: " + error.getMessage());
                }
            };
            plugin.getServer().getPluginManager()
                    .registerEvent(eventClass, listener, EventPriority.NORMAL, executor, plugin, false);
            plugin.getLogger().info("[" + name + "] 已监听 " + eventClass.getSimpleName() + " ← " + functionName);
        }
    }

    private Class<? extends Event> findEvent(String simpleName) {
        for (String pkg : EVENT_PACKAGES) {
            Class<? extends Event> found = ScriptNames.findEventClass(pkg + simpleName);
            if (found != null) return found;
        }
        return null;
    }

    private boolean callIfPresent(String functionName, Object argument) {
        try {
            LuaValue value = lua.get(functionName);
            if (value.type() != Lua.LuaType.FUNCTION) return false;
            value.call(argument);
            return true;
        } catch (Throwable error) {
            plugin.getLogger().warning("[" + name + "] " + functionName + " 失败: " + error.getMessage());
            return false;
        }
    }

    private String readString(String variable, String fallback) {
        try {
            LuaValue value = lua.get(variable);
            if (value.type() == Lua.LuaType.NIL) return fallback;
            String text = value.toString();
            return text == null || text.isEmpty() ? fallback : text;
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private void closeQuietly() {
        try {
            lua.close();
        } catch (Throwable ignored) {
        }
    }

}
