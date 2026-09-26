package cn.huohuas001.huhobotPenguin.nukkit.events

import cn.huohuas001.huhobotPenguin.adapter.api.MsgPack
import cn.nukkit.event.Cancellable
import cn.nukkit.event.Event
import cn.nukkit.event.HandlerList
import io.github.kloping.qqbot.entities.ex.Keyboard

/**
 * QQ 机器人收到群消息时触发的 Nukkit 事件。
 *
 * 第三方插件通过 `pluginManager.registerEvents(listener, plugin)` 监听本事件，
 * 即可在群里实现自己的指令（不局限于「执行服务器命令」那一套自定义命令）。
 *
 * 取消事件（`setCancelled(true)` 或 `isCancelled = true`）会阻止消息继续走
 * 内置指令分发与全量聊天转发 —— 即「这条消息我接管了」。
 *
 * ```kotlin
 * class MyListener : Listener {
 *     @EventHandler
 *     fun onGroupMessage(event: OnBotRecvMsg) {
 *         if (!event.message.content.startsWith("你好")) return
 *         event.reply("你也好")
 *         event.isCancelled = true
 *     }
 * }
 * ```
 */
class OnBotRecvMsg(
    val msgPack: MsgPack,
    private val replyTextAction: (String) -> Boolean,
    private val replyMarkdownAction: (String, Keyboard?) -> Boolean,
    private val replyImageAction: (String, String) -> Boolean
) : Event(), Cancellable {

    /** 仅承载数据、不支持回复的构造（用于测试或只读场景）。 */
    constructor(msgPack: MsgPack) : this(msgPack, { false }, { _, _ -> false }, { _, _ -> false })

    /** Java/Kotlin 插件使用的消息快照别名。 */
    val message: MsgPack get() = msgPack

    /** 回复触发此事件的 QQ 群消息。 */
    fun reply(text: String): Boolean = replyTextAction(text)

    fun replyText(text: String): Boolean = reply(text)

    fun replyMarkdown(markdown: String): Boolean = replyMarkdownAction(markdown, null)

    fun replyMarkdown(markdown: String, keyboard: Keyboard?): Boolean =
        replyMarkdownAction(markdown, keyboard)

    /**
     * 回复文本 + 网络图片。
     *
     * @param text     图前面的说明文字，留空则只发图
     * @param imageUrl 图片直链（QQ 服务端会自己去拉取，所以必须是公网可访问的地址）
     */
    fun replyImage(text: String, imageUrl: String): Boolean = replyImageAction(text, imageUrl)

    companion object {
        private val HANDLERS = HandlerList()

        /** Nukkit 用 `getDeclaredMethod("getHandlers")` 反射取 HandlerList，必须是静态方法。 */
        @JvmStatic
        fun getHandlers(): HandlerList = HANDLERS
    }
}
