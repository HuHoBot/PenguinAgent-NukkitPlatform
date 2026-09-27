package cn.huohuas001.huhobotPenguin.spigot.scripting;

import cn.huohuas001.huhobotPenguin.spigot.HuHoBotSpigot;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.CoerceJavaToLua;
import org.luaj.vm2.lib.jse.JsePlatform;

import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads {@code .js} / {@code .py} / {@code .lua} addon scripts from
 * {@code plugins/HuHoBotPenguin/addons}.
 *
 * Lua uses LuaJ, wired the way BirdLibraryApi does
 * (https://github.com/prach1121/birdlibraryapi, Apache-2.0), and that engine ships
 * inside the main jar. JavaScript uses GraalJS through JSR-223 and Python uses
 * GraalPy, but those two engines are NOT inside the main jar: they are loaded from
 * {@code plugins/HuHoBotPenguin/engines/*.jar} (built by {@code :addon-GraalJs} and
 * {@code :addon-GraalPy}). Without the matching jar, scripts of that language fail
 * to load and the plugin keeps running.
 * Each script is also registered as a HuHoBot addon.
 */
public class ScriptAddonLoader {

    private final HuHoBotSpigot plugin;
    private final File scriptsFolder;
    private final File enginesFolder;
    private final Map<String, LoadedScript> loaded = new LinkedHashMap<>();

    /**
     * GraalJS and GraalPy both live in engines/*.jar and share one classloader,
     * parented on this plugin's loader. Cached after the first script that needs it.
     */
    private volatile ClassLoader engineLoader;
    private volatile String engineLoaderError;

    /** GraalPy lives in its own jar. Null until a .py script is loaded, and stays null when the jar is absent. */
    private volatile Object pythonEngine;
    private volatile String pythonEngineError;

    public ScriptAddonLoader(HuHoBotSpigot plugin) {
        this.plugin = plugin;
        this.scriptsFolder = new File(plugin.getDataFolder(), "addons");
        if (!scriptsFolder.exists()) {
            scriptsFolder.mkdirs();
        }
        this.enginesFolder = new File(plugin.getDataFolder(), "engines");
        if (!enginesFolder.exists()) {
            enginesFolder.mkdirs();
        }
    }

    /**
     * Every jar in {@code engines/}, loaded once. Both the GraalJS and GraalPy bridges
     * are resolved against this loader, so one directory serves both languages.
     */
    private ClassLoader engineLoader() {
        ClassLoader cached = engineLoader;
        if (cached != null) return cached;
        synchronized (this) {
            if (engineLoader != null) return engineLoader;
            File[] jars = enginesFolder.listFiles((dir, name) -> name.toLowerCase().endsWith(".jar"));
            if (jars == null || jars.length == 0) {
                engineLoaderError = "未安装脚本引擎。请把 HuHoBot-Engine-GraalJs.jar / HuHoBot-Engine-GraalPy.jar 放到 " + enginesFolder.getPath();
                return null;
            }
            try {
                URL[] urls = new URL[jars.length];
                for (int i = 0; i < jars.length; i++) urls[i] = jars[i].toURI().toURL();
                engineLoader = new URLClassLoader(urls, ScriptAddonLoader.class.getClassLoader());
                plugin.getLogger().info("已从 " + enginesFolder.getPath() + " 加载脚本引擎（" + jars.length + " 个 jar）");
                return engineLoader;
            } catch (Throwable error) {
                engineLoaderError = "脚本引擎加载失败: " + error.getMessage();
                plugin.getLogger().severe(engineLoaderError);
                return null;
            }
        }
    }

    /**
     * Builds the GraalPy engine from the jars in {@code engines/}. The result is cached.
     * The returned object is the engine jar's {@code org.graalvm.polyglot.Engine}; this
     * class never names that type, so the main jar compiles without GraalPy.
     */
    private Object pythonEngine() {
        Object cached = pythonEngine;
        if (cached != null) return cached;
        synchronized (this) {
            if (pythonEngine != null) return pythonEngine;
            ClassLoader loader = engineLoader();
            if (loader == null) {
                pythonEngineError = engineLoaderError;
                return null;
            }
            ClassLoader previous = Thread.currentThread().getContextClassLoader();
            Thread.currentThread().setContextClassLoader(loader);
            try {
                // GraalVM 24.1 从「Engine.newBuilder 执行期间，当前线程的 context classloader」
                // 收集语言，不看调用方的类加载器，也没有接受 ClassLoader 的 build()。
                // 主 jar 里已经没有 polyglot，所以这段时间必须把线程的加载器换成引擎 jar 的，
                // 调用结束后再换回来。语言发现本身由引擎 jar 里的 GraalPyBridge 完成。
                Class<?> bridge = Class.forName("cn.huohuas001.huhobot.graalpy.GraalPyBridge", true, loader);
                Object engine = bridge.getMethod("createEngine").invoke(null);
                boolean provides = (Boolean) bridge.getMethod("providesPython", Object.class).invoke(null, engine);
                if (!provides) {
                    engine.getClass().getMethod("close").invoke(engine);
                    pythonEngineError = "engines 目录里的 jar 没有提供 GraalPy 语言";
                    return null;
                }
                pythonEngine = engine;
                plugin.getLogger().info("已加载 GraalPy 引擎");
                return pythonEngine;
            } catch (Throwable error) {
                pythonEngineError = "GraalPy 引擎加载失败: " + causeOf(error).getMessage();
                plugin.getLogger().severe(pythonEngineError);
                return null;
            } finally {
                Thread.currentThread().setContextClassLoader(previous);
            }
        }
    }

    public List<ScriptLoadResult> loadAll() {
        List<ScriptLoadResult> results = new ArrayList<>();
        try {
            warnStrayScripts();
            File[] dirs = scriptsFolder.listFiles(File::isDirectory);
            if (dirs == null || dirs.length == 0) {
                plugin.getLogger().info("addons 目录没有脚本插件（建一个目录，放 main.lua / main.py / main.js）: "
                        + scriptsFolder.getPath());
                return results;
            }
            Arrays.sort(dirs, Comparator.comparing(File::getName));
            for (File dir : dirs) {
                if ("config".equalsIgnoreCase(dir.getName()) || "data".equalsIgnoreCase(dir.getName())
                        || "files".equalsIgnoreCase(dir.getName())) continue;
                results.add(loadDirectory(dir));
            }
        } catch (Throwable t) {
            plugin.getLogger().severe("Fatal error while scanning the addon scripts folder: " + t);
            t.printStackTrace();
        }
        return results;
    }

    /** 旧结构提示：addons/ 根上直接放 .lua/.py/.js 已不再加载。 */
    private void warnStrayScripts() {
        File[] stray = scriptsFolder.listFiles((dir, name) -> {
            String lower = name.toLowerCase();
            return lower.endsWith(".js") || lower.endsWith(".lua") || lower.endsWith(".py");
        });
        if (stray == null) return;
        for (File file : stray) {
            String base = baseName(file.getName());
            String ext = file.getName().substring(file.getName().lastIndexOf('.'));
            plugin.getLogger().warning("[" + file.getName() + "] 脚本扩展已改为目录插件：请挪到 addons/"
                    + base + "/main" + ext + " 后重载");
        }
    }

    /**
     * 一个目录就是一个插件。入口优先用 metadata.yaml 的 entry，否则按
     * main.lua、main.py、main.js 找第一个存在的。
     */
    public ScriptLoadResult loadDirectory(File dir) {
        ScriptPackage pkg = ScriptPackage.open(dir, scriptsFolder);
        if (pkg == null) {
            String msg = "目录里没有 main.lua / main.py / main.js";
            plugin.getLogger().warning("[" + dir.getName() + "] " + msg + "，已跳过");
            return ScriptLoadResult.error(dir.getName(), msg);
        }
        if (!pkg.enabled()) {
            plugin.getLogger().info("[" + pkg.name() + "] 已在配置里禁用（_enabled=false），跳过加载");
            return ScriptLoadResult.error(pkg.name(), "已禁用");
        }
        if (!pkg.requirements().isEmpty()) {
            plugin.getLogger().warning("[" + pkg.name() + "] 声明了依赖 "
                    + String.join(", ", pkg.requirements()) + "，不会自动安装，请自行确认运行环境已满足");
        }
        String key = dir.getName().toLowerCase();
        if (loaded.containsKey(key)) {
            plugin.getLogger().warning("脚本已加载，跳过: " + key);
            return ScriptLoadResult.error(pkg.name(), "已加载");
        }
        // 脚本在执行期间就会调用 registerBotCommand，而它要求扩展已经登记。
        plugin.registerAddon(pkg.name(), pkg.manifest().version(),
                pkg.manifest().description().isEmpty() ? "script addon" : pkg.manifest().description(),
                pkg.manifest().author());
        ScriptLoadResult result;
        switch (pkg.language()) {
            case ".lua":
                result = loadLuaScript(pkg);
                break;
            case ".py":
                result = loadPythonScript(pkg);
                break;
            default:
                result = loadJsScript(pkg);
        }
        if (result.success()) {
            LoadedScript loadedScript = loaded.get(key);
            if (loadedScript != null) {
                registerAsAddon(loadedScript.api(), pkg);
            }
        }
        return result;
    }

    private static String baseName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? fileName : fileName.substring(0, dot);
    }

    /** 用语言描述覆盖加载前登记的那份元数据。metadata.yaml 里写了描述就用它。 */
    private void registerAsAddon(BirdScriptApi api, ScriptPackage pkg) {
        try {
            String language = ".py".equals(pkg.language()) ? "Python"
                    : ".lua".equals(pkg.language()) ? "Lua" : "JavaScript";
            String description = pkg.manifest().description().isEmpty()
                    ? language + " script addon" : pkg.manifest().description();
            api.registerAddon(pkg.name(), pkg.manifest().version(), description, pkg.manifest().author());
        } catch (Throwable t) {
            plugin.getLogger().warning("[" + pkg.name() + "] Failed to register HuHoBot addon: " + t.getMessage());
        }
    }

    /** 三个引擎都注入同一组全局对象：Bird、Bukkit、server、plugin、config、kv、DATA_DIR。 */
    private void bindCommon(Object session, BirdScriptApi api, ScriptPackage pkg) throws Exception {
        Method bind = session.getClass().getMethod("bind", String.class, Object.class);
        bind.invoke(session, "Bird", api);
        bind.invoke(session, "Bukkit", org.bukkit.Bukkit.class);
        bind.invoke(session, "server", plugin.getServer());
        bind.invoke(session, "plugin", plugin);
        bind.invoke(session, "config", pkg.config());
        bind.invoke(session, "kv", pkg.kv());
        bind.invoke(session, "DATA_DIR", pkg.dataDir().getAbsolutePath());
    }

    private ScriptLoadResult loadLuaScript(ScriptPackage pkg) {
        String name = pkg.name();
        File file = pkg.entryFile();
        try {
            Globals globals = JsePlatform.standardGlobals();

            BirdScriptApi api = new BirdScriptApi(plugin, name, scriptsFolder);
            globals.set("Bird", CoerceJavaToLua.coerce(api));
            globals.set("Bukkit", CoerceJavaToLua.coerce(org.bukkit.Bukkit.class));
            globals.set("server", CoerceJavaToLua.coerce(plugin.getServer()));
            globals.set("plugin", CoerceJavaToLua.coerce(plugin));
            globals.set("config", CoerceJavaToLua.coerce(pkg.config()));
            globals.set("kv", CoerceJavaToLua.coerce(pkg.kv()));
            globals.set("DATA_DIR", pkg.dataDir().getAbsolutePath());

            try (FileInputStream in = new FileInputStream(file)) {
                LuaValue chunk = globals.load(in, file.getName(), "t", globals);
                chunk.call();
            }

            loaded.put(pkg.directory().getName().toLowerCase(), new LoadedScript(name, file, globals, api));
            plugin.getLogger().info("Loaded script addon: " + name);
            return ScriptLoadResult.ok(name);

        } catch (LuaError e) {
            String msg = firstLine(e.getMessage());
            plugin.getLogger().severe("[" + name + "] Lua error: " + msg);
            return ScriptLoadResult.error(name, msg);
        } catch (Throwable t) {
            String msg = firstLine(String.valueOf(t.getMessage() != null ? t.getMessage() : t.toString()));
            plugin.getLogger().severe("[" + name + "] Failed to load: " + msg);
            t.printStackTrace();
            return ScriptLoadResult.error(name, msg);
        }
    }

    private ScriptLoadResult loadJsScript(ScriptPackage pkg) {
        String name = pkg.name();
        File file = pkg.entryFile();
        ClassLoader loader = engineLoader();
        if (loader == null) {
            plugin.getLogger().severe("[" + name + "] " + engineLoaderError);
            return ScriptLoadResult.error(name, engineLoaderError);
        }
        Object session = null;
        // GraalVM 从当前线程的 context classloader 发现 js 语言，主 jar 里已经没有它，
        // 所以建上下文期间必须换成引擎 jar 的加载器。
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(loader);
        try {
            Class<?> bridge = Class.forName("cn.huohuas001.huhobot.graaljs.GraalJsBridge", true, loader);
            session = bridge.getMethod("open").invoke(null);
            Thread.currentThread().setContextClassLoader(previous);
            previous = null;

            BirdScriptApi api = new BirdScriptApi(plugin, name, scriptsFolder);
            bindCommon(session, api, pkg);
            session.getClass().getMethod("eval", File.class).invoke(session, file);

            loaded.put(pkg.directory().getName().toLowerCase(), new LoadedScript(name, file, session, api));
            plugin.getLogger().info("Loaded script addon: " + name);
            return ScriptLoadResult.ok(name);

        } catch (InvocationTargetException error) {
            closeSession(session);
            Throwable cause = causeOf(error);
            String msg = firstLine(String.valueOf(cause.getMessage() != null ? cause.getMessage() : cause.toString()));
            plugin.getLogger().severe("[" + name + "] Failed to load: " + msg);
            cause.printStackTrace();
            return ScriptLoadResult.error(name, msg);
        } catch (Throwable t) {
            closeSession(session);
            String msg = firstLine(String.valueOf(t.getMessage() != null ? t.getMessage() : t.toString()));
            plugin.getLogger().severe("[" + name + "] Failed to load: " + msg);
            t.printStackTrace();
            return ScriptLoadResult.error(name, msg);
        } finally {
            if (previous != null) Thread.currentThread().setContextClassLoader(previous);
        }
    }

    private ScriptLoadResult loadPythonScript(ScriptPackage pkg) {
        String name = pkg.name();
        File file = pkg.entryFile();
        Object engine = pythonEngine();
        if (engine == null) {
            plugin.getLogger().severe("[" + name + "] " + pythonEngineError);
            return ScriptLoadResult.error(name, pythonEngineError);
        }
        Object session = null;
        // PythonLanguage 初始化同样按当前线程的 context classloader 找语言 id，
        // 这里必须还是引擎 jar 的加载器，否则会报 unknown language id python。
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(engine.getClass().getClassLoader());
        try {
            Class<?> bridge = Class.forName(
                    "cn.huohuas001.huhobot.graalpy.GraalPyBridge", true, engine.getClass().getClassLoader());
            session = bridge.getMethod("open", Object.class).invoke(null, engine);
            Thread.currentThread().setContextClassLoader(previous);
            previous = null;

            BirdScriptApi api = new BirdScriptApi(plugin, name, scriptsFolder);
            bindCommon(session, api, pkg);
            precheckPythonRequirements(session, pkg);
            session.getClass().getMethod("eval", File.class).invoke(session, file);

            loaded.put(pkg.directory().getName().toLowerCase(), new LoadedScript(name, file, session, api));
            plugin.getLogger().info("Loaded script addon: " + name);
            return ScriptLoadResult.ok(name);
        } catch (InvocationTargetException error) {
            closeSession(session);
            Throwable cause = causeOf(error);
            plugin.getLogger().severe("[" + name + "] Python 堆栈:\n" + stackOf(cause));
            int line = sourceLine(cause);
            String msg = firstLine(cause.getMessage());
            plugin.getLogger().severe("[" + name + "] Python error: " + msg
                    + (line >= 0 ? " (line " + line + ")" : ""));
            return line >= 0 ? ScriptLoadResult.error(name, msg, line, -1) : ScriptLoadResult.error(name, msg);
        } catch (Throwable t) {
            closeSession(session);
            String msg = firstLine(String.valueOf(t.getMessage() != null ? t.getMessage() : t.toString()));
            plugin.getLogger().severe("[" + name + "] Failed to load: " + msg);
            t.printStackTrace();
            return ScriptLoadResult.error(name, msg);
        } finally {
            if (previous != null) Thread.currentThread().setContextClassLoader(previous);
        }
    }

    /** GraalPy 的脚本错误带着行号，类型是引擎 jar 里的 GraalPyBridge.Failure。 */
    private static int sourceLine(Throwable error) {
        try {
            Method line = error.getClass().getMethod("line");
            Object value = line.invoke(error);
            return value instanceof Integer number ? number : -1;
        } catch (ReflectiveOperationException ignored) {
            return -1;
        }
    }

    private static Throwable causeOf(Throwable error) {
        Throwable cause = error.getCause();
        return cause == null ? error : cause;
    }

    private void closeEngine(Object engine) {
        closeSession(engine);
    }

    private void closeSession(Object session) {
        if (session == null) return;
        try {
            session.getClass().getMethod("close").invoke(session);
        } catch (Exception ignored) {
        }
    }

    private static String stackOf(Throwable error) {
        java.io.StringWriter writer = new java.io.StringWriter();
        error.printStackTrace(new java.io.PrintWriter(writer));
        String text = writer.toString();
        return text.length() > 4000 ? text.substring(0, 4000) : text;
    }

    private String firstLine(String message) {
        if (message == null) return "unknown error";
        String[] parts = message.split("\n");
        return parts[0].trim();
    }

    public void unloadAll() {
        for (LoadedScript s : loaded.values()) {
            try {
                s.api().unregisterAll();
            } catch (Exception e) {
                plugin.getLogger().warning("Problem unloading script " + s.name() + ": " + e.getMessage());
            }
            closeEngine(s.engine());
        }
        loaded.clear();
    }

    public List<ScriptLoadResult> reloadAll() {
        unloadAll();
        return loadAll();
    }

    /** 重载一个目录插件。参数是目录名，大小写不敏感。 */
    public ScriptLoadResult reloadOne(String nameInput) {
        File[] dirs = scriptsFolder.listFiles(File::isDirectory);
        File target = null;
        if (dirs != null) {
            for (File dir : dirs) {
                if (dir.getName().equalsIgnoreCase(nameInput.trim())) {
                    target = dir;
                    break;
                }
            }
        }
        if (target == null) {
            return ScriptLoadResult.notFound(nameInput);
        }

        LoadedScript existing = loaded.remove(target.getName().toLowerCase());
        if (existing != null) {
            try {
                existing.api().unregisterAll();
            } catch (Exception e) {
                plugin.getLogger().warning("Problem unloading previous version of " + nameInput + ": " + e.getMessage());
            }
            closeEngine(existing.engine());
        }

        return loadDirectory(target);
    }

    /** 列出 addons/ 下的插件目录名，给命令补全用。 */
    public List<String> listScriptFileNames() {
        File[] dirs = scriptsFolder.listFiles(File::isDirectory);
        List<String> names = new ArrayList<>();
        if (dirs != null) {
            for (File dir : dirs) {
                if ("config".equalsIgnoreCase(dir.getName()) || "data".equalsIgnoreCase(dir.getName())
                        || "files".equalsIgnoreCase(dir.getName())) continue;
                names.add(dir.getName());
            }
        }
        return names;
    }

    /** requirements.txt 的预检：逐个 import，失败只记警告，不安装。 */
    private void precheckPythonRequirements(Object session, ScriptPackage pkg) {
        for (String requirement : pkg.requirements()) {
            String module = requirement.replaceAll("[<>=!\\[;].*$", "").trim().replace("'", "");
            if (module.isEmpty()) continue;
            try {
                session.getClass().getMethod("evalSource", String.class).invoke(session, "__import__('" + module + "')");
            } catch (Throwable missing) {
                plugin.getLogger().warning("[" + pkg.name() + "] 依赖预检: 缺少 " + module
                        + "（requirements.txt），GraalPy 默认不带第三方库");
            }
        }
    }

    public int getLoadedCount() {
        return loaded.size();
    }

    public File getScriptsFolder() {
        return scriptsFolder;
    }
}
