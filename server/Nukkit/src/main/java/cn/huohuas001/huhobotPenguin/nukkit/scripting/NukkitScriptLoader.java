package cn.huohuas001.huhobotPenguin.nukkit.scripting;

import cn.huohuas001.huhobotPenguin.nukkit.HuHoBotNukkit;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 从插件自己的 {@code addons/} 目录加载**目录形式**的脚本插件（学 AstrBot 的 Star）：
 *
 * <pre>
 * addons/&lt;插件名&gt;/
 * ├── metadata.yaml       元数据（缺省用目录名 / 1.0.0）
 * ├── _conf_schema.json   配置 schema，值持久化到 addons/config/&lt;名字&gt;.json
 * ├── requirements.txt    依赖声明（只预检提示，不自动安装）
 * └── main.lua / main.py / main.js
 * </pre>
 *
 * 每个目录一个插件。Lua 用主 jar 里的 Lua 5.4（LuaJava，NuclearScripting 的
 * {@code on<Event>} 约定）；JavaScript 用 GraalJS、Python 用 GraalPy（Python 3），
 * 这两个引擎不在主 jar，放在 {@code engines/}。加载器永远不去扫服务器的 {@code plugins/}。
 *
 * 脚本环境里注入 {@code config}（schema 驱动的配置）、{@code kv}（插件级键值存储）、
 * {@code DATA_DIR}（大文件目录）。旧版「单 .lua/.py/.js 文件」不再加载，控制台会提示
 * 把文件挪进目录。
 */
public final class NukkitScriptLoader {

    private final HuHoBotNukkit plugin;
    private final File folder;
    private final File enginesFolder;
    private final Map<String, JsScriptEngine> javascript = new LinkedHashMap<>();
    private final Map<String, LuaScriptEngine> lua = new LinkedHashMap<>();
    private final Map<String, GpyScriptEngine> python = new LinkedHashMap<>();
    private volatile ClassLoader engineLoader;
    private volatile String engineLoaderError;

    public NukkitScriptLoader(HuHoBotNukkit plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "addons");
        if (!folder.exists()) folder.mkdirs();
        this.enginesFolder = new File(plugin.getDataFolder(), "engines");
        if (!enginesFolder.exists()) enginesFolder.mkdirs();
    }

    /**
     * GraalJS 与 GraalPy 都在 engines/*.jar，共用一个 classloader，父加载器是插件自己的。
     * 第一个需要引擎的脚本触发加载，之后缓存复用。
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
                engineLoader = new URLClassLoader(urls, NukkitScriptLoader.class.getClassLoader());
                plugin.getLogger().info("已从 " + enginesFolder.getPath() + " 加载脚本引擎（" + jars.length + " 个 jar）");
                return engineLoader;
            } catch (Throwable error) {
                engineLoaderError = "脚本引擎加载失败: " + error.getMessage();
                plugin.getLogger().error(engineLoaderError);
                return null;
            }
        }
    }

    public File folder() {
        return folder;
    }

    public int loadedCount() {
        return javascript.size() + lua.size() + python.size();
    }

    public int loadAll() {
        warnStrayScripts();
        File[] dirs = folder.listFiles(File::isDirectory);
        if (dirs == null || dirs.length == 0) {
            plugin.getLogger().info("addons 目录没有脚本插件（想加插件就建一个目录，放 main.lua / main.py / main.js）: " + folder.getPath());
            return 0;
        }
        Arrays.sort(dirs, Comparator.comparing(File::getName));
        int loaded = 0;
        for (File dir : dirs) {
            if ("config".equalsIgnoreCase(dir.getName()) || "data".equalsIgnoreCase(dir.getName())) continue;
            if (loadDirectory(dir)) loaded++;
        }
        return loaded;
    }

    /** 旧结构岔路口提示：addons/ 根上直接放 .lua/.py/.js 已不再加载。 */
    private void warnStrayScripts() {
        File[] stray = folder.listFiles((dir, name) -> ScriptNames.isScript(name));
        if (stray == null) return;
        for (File file : stray) {
            String base = ScriptNames.baseName(file.getName());
            plugin.getLogger().warning("[" + file.getName() + "] 脚本扩展已改为目录插件：请挪到 addons/"
                    + base + "/main" + file.getName().substring(file.getName().lastIndexOf('.')) + " 后重载");
        }
    }

    private boolean loadDirectory(File dir) {
        ScriptPackage pkg = ScriptPackage.open(dir, folder);
        if (pkg == null) {
            plugin.getLogger().warning("[" + dir.getName() + "] 目录里没有 main.lua / main.py / main.js，已跳过");
            return false;
        }
        if (!pkg.enabled()) {
            plugin.getLogger().info("[" + pkg.name() + "] 已在配置里禁用（_enabled=false），跳过加载");
            return false;
        }
        precheckRequirements(pkg);
        return load(pkg);
    }

    /** 只预检不安装：列出 requirements.txt 声明的依赖。Python 插件在引擎里再做 import 检查。 */
    private void precheckRequirements(ScriptPackage pkg) {
        if (pkg.requirements().isEmpty()) return;
        if (".py".equals(pkg.language())) return; // import 检查在 GpyScriptEngine 里做
        plugin.getLogger().warning("[" + pkg.name() + "] 声明了依赖 "
                + String.join(", ", pkg.requirements())
                + "，" + (".js".equals(pkg.language()) ? "JavaScript" : "Lua")
                + " 引擎没有包管理器，请自行确认运行环境已满足");
    }

    public boolean load(ScriptPackage pkg) {
        String key = pkg.directory().getName().toLowerCase();
        if (javascript.containsKey(key) || lua.containsKey(key) || python.containsKey(key)) {
            plugin.getLogger().warning("脚本已加载，跳过: " + key);
            return false;
        }
        if (nameTaken(pkg.name())) {
            plugin.getLogger().error("[" + pkg.directory().getName() + "] 插件名 " + pkg.name()
                    + " 已被另一个目录使用，已跳过");
            return false;
        }
        switch (pkg.language()) {
            case ".lua": {
                LuaScriptEngine engine = new LuaScriptEngine(plugin, pkg);
                if (!engine.load()) return false;
                lua.put(key, engine);
                return true;
            }
            case ".js": {
                ClassLoader loader = engineLoader();
                if (loader == null) {
                    plugin.getLogger().error("[" + pkg.name() + "] " + engineLoaderError);
                    return false;
                }
                JsScriptEngine engine = new JsScriptEngine(plugin, pkg, loader);
                if (!engine.load()) return false;
                javascript.put(key, engine);
                return true;
            }
            case ".py": {
                ClassLoader loader = engineLoader();
                if (loader == null) {
                    plugin.getLogger().error("[" + pkg.name() + "] " + engineLoaderError);
                    return false;
                }
                GpyScriptEngine engine;
                try {
                    engine = new GpyScriptEngine(plugin, pkg, loader);
                } catch (IllegalStateException error) {
                    plugin.getLogger().error("[" + pkg.name() + "] " + error.getMessage());
                    return false;
                }
                if (!engine.load()) return false;
                python.put(key, engine);
                return true;
            }
            default:
                return false;
        }
    }

    public void unloadAll() {
        for (JsScriptEngine engine : new ArrayList<>(javascript.values())) engine.unload();
        for (LuaScriptEngine engine : new ArrayList<>(lua.values())) engine.unload();
        for (GpyScriptEngine engine : new ArrayList<>(python.values())) engine.unload();
        javascript.clear();
        lua.clear();
        python.clear();
    }

    /** 重载全部插件，或者只重载名为 {@code name} 的目录插件（大小写不敏感）。 */
    public String reload(String name) {
        if (name == null || name.trim().isEmpty()) {
            unloadAll();
            int loaded = loadAll();
            return "已重载 " + loaded + " 个脚本插件";
        }
        String wanted = name.trim().toLowerCase();
        File[] dirs = folder.listFiles(File::isDirectory);
        File target = null;
        if (dirs != null) {
            Arrays.sort(dirs, Comparator.comparing(File::getName));
            for (File dir : dirs) {
                if (dir.getName().toLowerCase().equals(wanted)) {
                    target = dir;
                    break;
                }
            }
        }
        if (target == null) return "找不到脚本插件: " + name;
        drop(target.getName().toLowerCase());
        return loadDirectory(target) ? "已重载 " + target.getName() : "重载失败: " + target.getName() + "（见控制台）";
    }

    /** 列出 addons/ 下的插件目录名（命令补全用）。 */
    public List<String> fileNames() {
        File[] dirs = folder.listFiles(File::isDirectory);
        List<String> names = new ArrayList<>();
        if (dirs != null) {
            Arrays.sort(dirs, Comparator.comparing(File::getName));
            for (File dir : dirs) {
                if ("config".equalsIgnoreCase(dir.getName()) || "data".equalsIgnoreCase(dir.getName())) continue;
                names.add(dir.getName());
            }
        }
        return names;
    }

    private boolean nameTaken(String name) {
        for (JsScriptEngine engine : javascript.values()) if (engine.name().equals(name)) return true;
        for (LuaScriptEngine engine : lua.values()) if (engine.name().equals(name)) return true;
        for (GpyScriptEngine engine : python.values()) if (engine.name().equals(name)) return true;
        return false;
    }

    private void drop(String key) {
        JsScriptEngine js = javascript.remove(key);
        if (js != null) js.unload();
        LuaScriptEngine luaScript = lua.remove(key);
        if (luaScript != null) luaScript.unload();
        GpyScriptEngine py = python.remove(key);
        if (py != null) py.unload();
    }
}
