package cn.huohuas001.huhobotPenguin.nukkit.scripting;

import cn.huohuas001.huhobotPenguin.nukkit.HuHoBotNukkit;
import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandMap;
import cn.nukkit.command.CommandSender;
import cn.nukkit.event.Event;
import cn.nukkit.event.EventPriority;
import cn.nukkit.event.Listener;
import cn.nukkit.plugin.EventExecutor;
import cn.nukkit.scheduler.TaskHandler;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One GraalJS addon. The engine is the same one AXDA-ScriptEngine uses
 * (https://github.com/Ruokwok/AXDA-ScriptEngine, MIT); the API surface here is
 * the smaller {@code mc} object HuHoBot needs, not the full LSE compatibility
 * layer. Scripts live in the plugin's own {@code addons/} folder.
 *
 * GraalJS is not on this module's compile classpath and not inside the main jar.
 * {@link NukkitScriptLoader} loads {@code engines/*.jar} and passes that
 * {@link ClassLoader} here; every call into the engine goes through
 * {@code cn.huohuas001.huhobot.graaljs.GraalJsBridge} by reflection, so the main
 * jar builds and runs without GraalJS present.
 */
public final class JsScriptEngine {

    private static final String BRIDGE = "cn.huohuas001.huhobot.graaljs.GraalJsBridge";

    private final HuHoBotNukkit plugin;
    private final ScriptPackage pkg;
    private final File file;
    private final String name;
    private final ClassLoader engineLoader;
    private final JsApi api;
    private final List<TaskHandler> tasks = new ArrayList<>();
    private final List<Command> commands = new ArrayList<>();
    private final Set<String> botCommandKeys = ConcurrentHashMap.newKeySet();
    private final Listener listener = new Listener() {};

    private final Class<?> bridgeClass;
    private final Method canExecute;
    private final Method execute;
    private final Method adapt;
    private final Object session;
    private final Method bind;
    private final Method eval;
    private final Method close;
    private boolean loaded;

    public JsScriptEngine(HuHoBotNukkit plugin, ScriptPackage pkg, ClassLoader engineLoader) {
        this.plugin = plugin;
        this.pkg = pkg;
        this.file = pkg.entryFile();
        this.name = pkg.name();
        this.engineLoader = engineLoader;
        this.api = new JsApi();
        try {
            this.bridgeClass = Class.forName(BRIDGE, true, engineLoader);
            this.canExecute = bridgeClass.getMethod("canExecute", Object.class);
            this.execute = bridgeClass.getMethod("execute", Object.class, Object[].class);
            this.adapt = bridgeClass.getMethod("adapt", Object.class, Class.class);
            this.session = bridgeClass.getMethod("open").invoke(null);
            Class<?> sessionType = session.getClass();
            this.bind = sessionType.getMethod("bind", String.class, Object.class);
            this.eval = sessionType.getMethod("eval", File.class);
            this.close = sessionType.getMethod("close");
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("engines 目录里的 jar 不是 GraalJS（需要 HuHoBot-Engine-GraalJs）: " + error.getMessage(), error);
        }
    }

    public String name() {
        return name;
    }

    public boolean load() {
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(engineLoader);
        try {
            bind.invoke(session, "mc", api);
            bind.invoke(session, "server", plugin.getServer());
            bind.invoke(session, "plugin", plugin);
            bind.invoke(session, "logger", plugin.getLogger());
            // AstrBot 式注入：schema 驱动的配置、插件级 KV 存储、数据目录。
            bind.invoke(session, "config", pkg.config());
            bind.invoke(session, "kv", pkg.kv());
            bind.invoke(session, "DATA_DIR", pkg.dataDir().getAbsolutePath());
            // 引擎 classloader 里的适配器。脚本拿它把函数包成 java.base 的类型，
            // 才能传给 HttpClient 这类主插件看不见 Graal Value 的 JDK API。
            bind.invoke(session, "Graal", bridgeClass.getMethod("helper").invoke(null));
            eval.invoke(session, file);
            loaded = true;
            String description = pkg.manifest().description().isEmpty()
                    ? "JavaScript script addon (GraalJS)" : pkg.manifest().description();
            plugin.registerAddon(name, pkg.manifest().version(), description, pkg.manifest().author());
            plugin.getLogger().info("已加载 JS 脚本扩展: " + name);
            return true;
        } catch (InvocationTargetException error) {
            plugin.getLogger().error("[" + name + "] JavaScript 错误: " + firstLine(causeOf(error).getMessage()));
            closeQuietly();
            return false;
        } catch (Throwable error) {
            plugin.getLogger().error("[" + name + "] 加载失败: " + error.getMessage());
            closeQuietly();
            return false;
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
        }
    }

    public void unload() {
        for (TaskHandler task : tasks) {
            try {
                task.cancel();
            } catch (Exception ignored) {
            }
        }
        tasks.clear();
        CommandMap map = plugin.getServer().getCommandMap();
        for (Command command : commands) {
            try {
                // Command.unregister(map) 只会把命令上的 map 引用置空，不会从
                // knownCommands 里移除；必须用 CommandMap.unregister 才能真正删掉，
                // 否则重载后旧命令仍占位并指向已关闭的脚本上下文。
                map.unregister(command);
            } catch (Exception ignored) {
            }
        }
        commands.clear();
        for (String key : new ArrayList<>(botCommandKeys)) {
            plugin.unregisterBotCommand(key);
        }
        botCommandKeys.clear();
        closeQuietly();
        loaded = false;
    }

    private void closeQuietly() {
        try {
            close.invoke(session);
        } catch (Exception ignored) {
        }
    }

    /** Turns a JavaScript function into the functional interface {@code type}. */
    private Object adapt(Object function, Class<?> type) {
        try {
            return adapt.invoke(null, function, type);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException(error);
        }
    }

    private boolean canExecute(Object function) {
        if (function == null) return false;
        try {
            return (Boolean) canExecute.invoke(null, function);
        } catch (ReflectiveOperationException error) {
            return false;
        }
    }

    private void execute(Object function, Object... args) {
        try {
            execute.invoke(null, function, args);
        } catch (InvocationTargetException error) {
            Throwable cause = causeOf(error);
            if (cause instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException(cause);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException(error);
        }
    }

    private static Throwable causeOf(InvocationTargetException error) {
        Throwable cause = error.getCause();
        return cause == null ? error : cause;
    }

    private static String firstLine(String message) {
        if (message == null) return "unknown error";
        int breakAt = message.indexOf('\n');
        return (breakAt < 0 ? message : message.substring(0, breakAt)).trim();
    }

    /** Object exposed to scripts as the global {@code mc}. */
    public final class JsApi {

        public String addonName() {
            return name;
        }

        public void log(Object message) {
            plugin.getLogger().info("[" + name + "] " + message);
        }

        /** 把一条文本发到已配置的全部 QQ 群。 */
        public void sendBotText(String text) {
            plugin.sendBotText(text);
        }

        public void warn(Object message) {
            plugin.getLogger().warning("[" + name + "] " + message);
        }

        public void broadcast(String message) {
            plugin.getServer().broadcastMessage(message == null ? "" : message);
        }

        public Player getPlayer(String playerName) {
            return plugin.getServer().getPlayer(playerName);
        }

        public void tell(Player player, String message) {
            if (player != null) player.sendMessage(message == null ? "" : message);
        }

        public void runCommand(String command) {
            String line = command == null ? "" : command.startsWith("/") ? command.substring(1) : command;
            plugin.getServer().getScheduler().scheduleTask(plugin, () ->
                    plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), line));
        }

        /** Registers a QQ-side custom command owned by this script. */
        public boolean registerBotCommand(String key, String command) {
            boolean registered = plugin.registerBotCommand(name, key, command, 0, true);
            if (registered && key != null) botCommandKeys.add(key.trim());
            return registered;
        }

        public boolean registerBotCommand(String key, String command, int permission) {
            boolean registered = plugin.registerBotCommand(name, key, command, permission, true);
            if (registered && key != null) botCommandKeys.add(key.trim());
            return registered;
        }

        /** Registers a Nukkit command. {@code callback(sender, label, args)} runs on the server thread. */
        public void registerCommand(String commandName, Object callback) {
            CommandCallback adapted = adapt(callback, CommandCallback.class) instanceof CommandCallback command
                    ? command : null;
            if (commandName == null || commandName.isBlank() || adapted == null) {
                warn("registerCommand 需要命令名和一个函数");
                return;
            }
            Command command = new Command(commandName) {
                @Override
                public boolean execute(CommandSender sender, String label, String[] args) {
                    try {
                        adapted.call(sender, label, args);
                    } catch (Throwable error) {
                        sender.sendMessage("§c脚本错误: " + error.getMessage());
                        warn("命令 /" + commandName + " 执行失败: " + error.getMessage());
                    }
                    return true;
                }
            };
            plugin.getServer().getCommandMap().register(plugin.getName().toLowerCase(), command);
            commands.add(command);
            log("已注册命令: /" + commandName);
        }

        /** Listens for a Nukkit event. {@code eventName} is the simple or fully-qualified class name. */
        public void listen(String eventName, Object callback) {
            EventCallback adapted = adapt(callback, EventCallback.class) instanceof EventCallback event ? event : null;
            Class<? extends Event> eventClass = ScriptNames.findEventClass(eventName);
            if (eventClass == null) {
                warn("找不到事件: " + eventName);
                return;
            }
            if (adapted == null) {
                warn("listen 的第二个参数必须是函数");
                return;
            }
            EventExecutor executor = (ignored, event) -> {
                if (!eventClass.isInstance(event)) return;
                try {
                    adapted.call(event);
                } catch (Throwable error) {
                    warn("事件 " + eventClass.getSimpleName() + " 处理失败: " + error.getMessage());
                }
            };
            plugin.getServer().getPluginManager()
                    .registerEvent(eventClass, listener, EventPriority.NORMAL, executor, plugin, false);
            log("已监听事件: " + eventClass.getSimpleName());
        }

        public int setTimeout(Object callback, int delayTicks) {
            Runnable adapted = adapt(callback, Runnable.class) instanceof Runnable task ? task : null;
            if (adapted == null) {
                warn("setTimeout 的第一个参数必须是函数");
                return -1;
            }
            return track(plugin.getServer().getScheduler().scheduleDelayedTask(plugin, () -> run(adapted), delayTicks));
        }

        public int setInterval(Object callback, int periodTicks) {
            Runnable adapted = adapt(callback, Runnable.class) instanceof Runnable task ? task : null;
            if (adapted == null) {
                warn("setInterval 的第一个参数必须是函数");
                return -1;
            }
            return track(plugin.getServer().getScheduler().scheduleRepeatingTask(plugin, () -> run(adapted), periodTicks));
        }

        public void clearInterval(int taskId) {
            plugin.getServer().getScheduler().cancelTask(taskId);
        }

        private int track(TaskHandler handler) {
            tasks.add(handler);
            return handler.getTaskId();
        }

        private void run(Runnable callback) {
            if (!loaded || callback == null) return;
            try {
                callback.run();
            } catch (Throwable error) {
                warn("定时任务失败: " + error.getMessage());
            }
        }

        public Server getServer() {
            return plugin.getServer();
        }
    }

    /** What a script passes to {@code mc.registerCommand}: {@code (sender, label, args)}. */
    public interface CommandCallback {
        void call(CommandSender sender, String label, String[] args);
    }

    /** What a script passes to {@code mc.listen}: {@code (event)}. */
    public interface EventCallback {
        void call(Event event);
    }
}
