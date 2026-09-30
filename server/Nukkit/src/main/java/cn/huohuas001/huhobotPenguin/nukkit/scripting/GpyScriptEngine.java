package cn.huohuas001.huhobotPenguin.nukkit.scripting;

import cn.huohuas001.huhobotPenguin.nukkit.HuHoBotNukkit;
import cn.nukkit.Player;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandMap;
import cn.nukkit.command.CommandSender;
import cn.nukkit.event.Event;
import cn.nukkit.event.EventPriority;
import cn.nukkit.event.HandlerList;
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
 * 一个 GraalPy（Python 3）addon。脚本拿到 {@code api}、{@code server}、{@code plugin}、
 * {@code config}、{@code kv}、{@code DATA_DIR} 等全局对象，并登记为 HuHoBot addon。
 *
 * GraalPy 不在主插件的编译 classpath，也不在主 jar 里。{@link NukkitScriptLoader}
 * 加载 {@code engines/*.jar} 后把那个 {@link ClassLoader} 传进来；所有 polyglot 调用
 * 都通过 {@code cn.huohuas001.huhobot.graalpy.GraalPyBridge} 反射完成，所以主 jar
 * 没有引擎也能编译、能启动。Python 3 语法，因为这是 GraalPy 实现的方言。
 */
public final class GpyScriptEngine {

    private static final String BRIDGE = "cn.huohuas001.huhobot.graalpy.GraalPyBridge";

    private final HuHoBotNukkit plugin;
    private final ScriptPackage pkg;
    private final File file;
    private final String name;
    private final ClassLoader engineLoader;
    private final PyApi api = new PyApi();
    private final List<TaskHandler> tasks = new ArrayList<>();
    private final List<Command> commands = new ArrayList<>();
    private final Set<String> botCommandKeys = ConcurrentHashMap.newKeySet();
    private final Listener listener = new Listener() {};
    private final List<HandlerList> eventHandlers = new ArrayList<>();

    private final Class<?> bridgeClass;
    private final Method canExecute;
    private final Method execute;
    private final Method asString;
    private final Object session;
    private final Method bind;
    private final Method eval;
    private final Method get;
    private final Method evalSource;
    private final Method close;
    private boolean enabled;

    public GpyScriptEngine(HuHoBotNukkit plugin, ScriptPackage pkg, ClassLoader engineLoader) {
        this.plugin = plugin;
        this.pkg = pkg;
        this.file = pkg.entryFile();
        this.name = pkg.name();
        this.engineLoader = engineLoader;
        try {
            this.bridgeClass = Class.forName(BRIDGE, true, engineLoader);
            this.canExecute = bridgeClass.getMethod("canExecute", Object.class);
            this.execute = bridgeClass.getMethod("execute", Object.class, Object[].class);
            this.asString = bridgeClass.getMethod("asString", Object.class);
            this.session = bridgeClass.getMethod("open").invoke(null);
            Class<?> sessionType = session.getClass();
            this.bind = sessionType.getMethod("bind", String.class, Object.class);
            this.eval = sessionType.getMethod("eval", File.class);
            this.get = sessionType.getMethod("get", String.class);
            this.evalSource = sessionType.getMethod("evalSource", String.class);
            this.close = sessionType.getMethod("close");
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("engines 目录里的 jar 不是 GraalPy（需要 HuHoBot-Engine-GraalPy）: "
                    + error.getMessage(), error);
        }
    }

    public String name() {
        return name;
    }

    public boolean load() {
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(engineLoader);
        try {
            bind.invoke(session, "api", api);
            bind.invoke(session, "server", plugin.getServer());
            bind.invoke(session, "plugin", plugin);
            bind.invoke(session, "logger", plugin.getLogger());
            bind.invoke(session, "SCRIPT_NAME", name);
            // AstrBot 式注入：schema 驱动的配置、插件级 KV 存储、数据目录。
            bind.invoke(session, "config", pkg.config());
            bind.invoke(session, "kv", pkg.kv());
            bind.invoke(session, "DATA_DIR", pkg.dataDir().getAbsolutePath());
            precheckRequirements();
            eval.invoke(session, file);

            // 元数据以 metadata.yaml 为准；PLUGIN_* 顶层变量仍可覆盖。
            String fallbackDescription = pkg.manifest().description().isEmpty()
                    ? "Python script addon (GraalPy)" : pkg.manifest().description();
            String version = read("PLUGIN_VERSION", pkg.manifest().version());
            String author = read("PLUGIN_AUTHOR", pkg.manifest().author());
            String description = read("PLUGIN_DESCRIPTION", fallbackDescription);
            plugin.registerAddon(name, version, description, author);

            if (callable("on_enable")) call("on_enable");
            enabled = true;
            plugin.getLogger().info("已加载 Python 脚本扩展: " + name + " v" + version);
            return true;
        } catch (InvocationTargetException error) {
            plugin.getLogger().error("[" + name + "] Python 加载失败: " + firstLine(causeOf(error).getMessage()));
            plugin.unregisterAddon(name);
            closeQuietly();
            return false;
        } catch (Throwable error) {
            plugin.getLogger().error("[" + name + "] 加载失败: " + error.getMessage());
            plugin.unregisterAddon(name);
            closeQuietly();
            return false;
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
        }
    }

    public void unload() {
        for (HandlerList handlers : eventHandlers) {
            try {
                handlers.unregister(listener);
            } catch (Exception ignored) {
            }
        }
        eventHandlers.clear();
        if (enabled && callable("on_disable")) {
            try {
                call("on_disable");
            } catch (Throwable error) {
                plugin.getLogger().warning("[" + name + "] on_disable 失败: " + error.getMessage());
            }
        }
        enabled = false;
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
                // Command.unregister(map) 只会把命令上的 map 引用置空，不会从 knownCommands
                // 里移除；必须用 CommandMap.unregister 才能真正删掉。
                map.unregister(command);
            } catch (Exception ignored) {
            }
        }
        commands.clear();
        for (String key : new ArrayList<>(botCommandKeys)) {
            plugin.unregisterBotCommand(key);
        }
        botCommandKeys.clear();
        plugin.unregisterAddon(name);
        closeQuietly();
    }

    /** requirements.txt 的预检：逐个 import，失败只警告不安装。 */
    private void precheckRequirements() {
        for (String requirement : pkg.requirements()) {
            String module = requirement.replaceAll("[<>=!\\[;].*$", "").trim().replace("'", "");
            if (module.isEmpty()) continue;
            try {
                evalSource.invoke(session, "__import__('" + module + "')");
            } catch (Throwable missing) {
                plugin.getLogger().warning("[" + name + "] 依赖预检: 缺少 " + module
                        + "（requirements.txt），GraalPy 默认不带第三方库，请确认后再重载");
            }
        }
    }

    private void closeQuietly() {
        try {
            close.invoke(session);
        } catch (Exception ignored) {
        }
        try {
            // 无参 open() 建的 Engine 归这个 Session。不关的话每次重载都留一个 Truffle 引擎。
            session.getClass().getMethod("closeEngine").invoke(session);
        } catch (Exception ignored) {
        }
    }

    private Object global(String key) {
        try {
            return get.invoke(session, key);
        } catch (ReflectiveOperationException error) {
            return null;
        }
    }

    private boolean callable(String function) {
        try {
            return (Boolean) canExecute.invoke(null, global(function));
        } catch (ReflectiveOperationException error) {
            return false;
        }
    }

    private void call(String function, Object... args) {
        executePython(global(function), args);
    }

    private void executePython(Object function, Object... args) {
        try {
            execute.invoke(null, function, args);
        } catch (InvocationTargetException error) {
            Throwable cause = causeOf(error);
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            throw new IllegalStateException(cause);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException(error);
        }
    }

    private String read(String variable, String fallback) {
        try {
            String text = (String) asString.invoke(null, global(variable));
            return text == null || text.isEmpty() ? fallback : text;
        } catch (ReflectiveOperationException error) {
            return fallback;
        }
    }

    private boolean canExecuteQuietly(Object value) {
        if (value == null) return false;
        try {
            return (Boolean) canExecute.invoke(null, value);
        } catch (ReflectiveOperationException error) {
            return false;
        }
    }

    private static Throwable causeOf(Throwable error) {
        Throwable cause = error instanceof InvocationTargetException ? error.getCause() : null;
        return cause == null ? error : cause;
    }

    private static String firstLine(String message) {
        if (message == null) return "unknown error";
        int breakAt = message.indexOf('\n');
        return (breakAt < 0 ? message : message.substring(0, breakAt)).trim();
    }

    private void trackEvent(Class<? extends Event> eventClass) {
        try {
            Object handlers = eventClass.getMethod("getHandlers").invoke(null);
            if (handlers instanceof HandlerList) eventHandlers.add((HandlerList) handlers);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    /** 注入给脚本的全局 {@code api}。方法名用下划线，和 Python 习惯一致。 */
    public final class PyApi {

        public String addon_name() {
            return name;
        }

        public void log(String message) {
            plugin.getLogger().info("[" + name + "] " + message);
        }

        public void warn(String message) {
            plugin.getLogger().warning("[" + name + "] " + message);
        }

        public void broadcast(String message) {
            plugin.getServer().broadcastMessage(message == null ? "" : message);
        }

        /** 把一条文本发到已配置的全部 QQ 群。 */
        public void send_bot_text(String text) {
            plugin.sendBotText(text);
        }

        public Player get_player(String playerName) {
            return plugin.getServer().getPlayer(playerName);
        }

        public void tell(Player player, String message) {
            if (player != null) player.sendMessage(message == null ? "" : message);
        }

        public boolean dispatch_command(String command) {
            String line = command == null ? "" : command.startsWith("/") ? command.substring(1) : command;
            return plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), line);
        }

        public boolean register_bot_command(String key, String command) {
            return register_bot_command(key, command, 0);
        }

        public boolean register_bot_command(String key, String command, int permission) {
            boolean registered = plugin.registerBotCommand(name, key, command, permission, true);
            if (registered && key != null) botCommandKeys.add(key.trim());
            return registered;
        }

        /** {@code callback(sender, label, args)}。args 是 Java 的 String[]，下标从 0 开始。 */
        public void register_command(String commandName, Object callback) {
            register_command(commandName, "", callback);
        }

        public void register_command(String commandName, String description, Object callback) {
            if (commandName == null || commandName.trim().isEmpty() || !canExecuteQuietly(callback)) {
                warn("register_command 需要命令名和一个函数");
                return;
            }
            Command command = new Command(commandName, description == null ? "" : description) {
                @Override
                public boolean execute(CommandSender sender, String label, String[] args) {
                    if (!enabled) return true;
                    try {
                        executePython(callback, sender, label, args);
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

        /** 监听一个 Nukkit 事件，{@code callback(event)}。事件名可简单名或全限定名。 */
        public void register_event(String eventName, Object callback) {
            Class<? extends Event> eventClass = ScriptNames.findEventClass(eventName);
            if (eventClass == null) {
                warn("找不到事件: " + eventName);
                return;
            }
            if (!canExecuteQuietly(callback)) {
                warn("register_event 的第二个参数必须是函数");
                return;
            }
            EventExecutor executor = (ignored, event) -> {
                if (!enabled || !eventClass.isInstance(event)) return;
                try {
                    executePython(callback, event);
                } catch (Throwable error) {
                    warn("事件 " + eventClass.getSimpleName() + " 处理失败: " + error.getMessage());
                }
            };
            plugin.getServer().getPluginManager()
                    .registerEvent(eventClass, listener, EventPriority.NORMAL, executor, plugin, false);
            trackEvent(eventClass);
            log("已监听事件: " + eventClass.getSimpleName());
        }

        public int schedule_task(Object callback, int delayTicks) {
            if (!canExecuteQuietly(callback)) {
                warn("schedule_task 的第一个参数必须是函数");
                return -1;
            }
            return track(plugin.getServer().getScheduler()
                    .scheduleDelayedTask(plugin, () -> run(callback), delayTicks));
        }

        public int schedule_repeating_task(Object callback, int periodTicks) {
            if (!canExecuteQuietly(callback)) {
                warn("schedule_repeating_task 的第一个参数必须是函数");
                return -1;
            }
            return track(plugin.getServer().getScheduler()
                    .scheduleRepeatingTask(plugin, () -> run(callback), periodTicks));
        }

        public void cancel_task(int taskId) {
            plugin.getServer().getScheduler().cancelTask(taskId);
        }

        private int track(TaskHandler handler) {
            tasks.add(handler);
            return handler.getTaskId();
        }

        private void run(Object callback) {
            if (!enabled || !canExecuteQuietly(callback)) return;
            try {
                executePython(callback);
            } catch (Throwable error) {
                warn("定时任务失败: " + error.getMessage());
            }
        }
    }
}
