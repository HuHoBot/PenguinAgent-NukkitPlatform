package cn.huohuas001.huhobotPenguin.nukkit.integration

import cn.huohuas001.huhobotPenguin.nukkit.HuHoBotNukkit
import cn.nukkit.Player
import cn.nukkit.Server
import java.lang.reflect.Method

/**
 * PlaceholderAPI（Nukkit 版）反射接入。
 *
 * 目标插件：https://github.com/Creeperface01/PlaceholderAPI-nukkit
 * API 入口：`com.creeperface.nukkit.placeholderapi.api.PlaceholderAPI`
 * 解析方法：`String translateString(String input, Player visitor)`
 *
 * 与 Spigot 适配器的 `PlaceholderApiSupport` 保持同一结构：**全程反射，不引入编译期依赖**。
 * 这样既能在未安装该插件时照常工作，也不会因为上游快照仓库
 * （`repo.opencollab.dev/maven-snapshots`）的波动而影响本仓库构建。
 */
object PlaceholderApiSupport {
    private const val PLUGIN_NAME = "PlaceholderAPI"
    private const val CLASS_NAME = "com.creeperface.nukkit.placeholderapi.api.PlaceholderAPI"

    @Volatile
    private var api: Any? = null

    @Volatile
    private var translateString: Method? = null

    /** 是否成功接入了可用的占位符解析器。 */
    val available: Boolean get() = api != null && translateString != null

    /** 插件启用与 `/huhobot reload` 后调用，重新探测 PlaceholderAPI。 */
    fun setup(plugin: HuHoBotNukkit) {
        api = null
        translateString = null

        if (!plugin.isPlaceholderApiEnabled()) {
            plugin.log_info("PlaceholderAPI 接入已在配置中关闭，%占位符% 将原样保留")
            return
        }

        val dependency = plugin.server.pluginManager.getPlugin(PLUGIN_NAME)
        if (dependency == null || !dependency.isEnabled) {
            plugin.log_info("未检测到 PlaceholderAPI，配置中的 %占位符% 将原样保留")
            return
        }

        try {
            // ⚠️ 必须用目标插件自己的类加载器：Nukkit 给每个插件分配独立的
            // PluginClassLoader，本插件的加载器能否命中对方类，取决于全局类注册表的兜底行为，
            // 不能依赖。用 plugin 实例的加载器才是确定的。
            val loader = dependency.javaClass.classLoader
            val apiClass = Class.forName(CLASS_NAME, true, loader)

            val instance = apiClass.getMethod("getInstance").invoke(null)
                ?: throw IllegalStateException("PlaceholderAPI.getInstance() 返回 null")

            // translateString 有多个重载，这里锁定 (String, Player) 这一个：
            // 单参重载会以 null 玩家解析，拿不到依赖玩家的占位符。
            val method = apiClass.getMethod(
                "translateString",
                String::class.java,
                Player::class.java
            )

            api = instance
            translateString = method
            val version = runCatching { dependency.description.version }.getOrNull()
            plugin.log_info("已接入 PlaceholderAPI（$PLUGIN_NAME ${version ?: ""}）")
        } catch (error: Throwable) {
            api = null
            translateString = null
            plugin.log_warning("PlaceholderAPI 接入失败，%占位符% 将原样保留: ${error.message}")
        }
    }

    /**
     * 解析文本中的 `%占位符%`。
     *
     * @param playerName 玩家名；为空时按无玩家上下文解析（只能解析全局占位符）
     */
    fun apply(playerName: String?, text: String): String {
        val method = translateString ?: return text
        val instance = api ?: return text
        // 没有任何 % 时直接返回，省掉一次反射调用
        if (text.isEmpty() || !text.contains('%')) return text
        return try {
            val player = playerName?.takeIf { it.isNotBlank() }?.let { Server.getInstance().getPlayerExact(it) }
            method.invoke(instance, text, player) as? String ?: text
        } catch (error: Throwable) {
            // 单个占位符解析失败不应该让整条消息发不出去
            text
        }
    }
}
