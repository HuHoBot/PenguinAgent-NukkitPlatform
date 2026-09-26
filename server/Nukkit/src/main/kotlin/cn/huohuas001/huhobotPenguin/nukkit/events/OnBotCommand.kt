package cn.huohuas001.huhobotPenguin.nukkit.events

import cn.huohuas001.huhobotPenguin.adapter.api.MsgPack
import cn.nukkit.event.Cancellable
import cn.nukkit.event.Event
import cn.nukkit.event.HandlerList
import io.github.kloping.qqbot.entities.ex.Keyboard

/**
 * 命中自定义命令时触发的 Nukkit 事件。
 *
 * 自定义命令（`custom-commands` / `registerBotCommand`）的本职是「在服务器上执行一条命令」。
 * 但扩展往往想自己干活（例如去调外部 API、发张图），这时监听本事件并取消它：
 * 命令模板里的那条服务器命令会被跳过，改由扩展自行回复。
 *
 * `message.commandKey` 是命中的 key，`message.commandArguments` 是后面的参数。
 *
 * ```kotlin
 * class MyListener : Listener {
 *     @EventHandler
 *     fun onCommand(event: OnBotCommand) {
 *         if (event.message.commandKey != "天气") return
 *         event.reply("今天晴")
 *         event.isCancelled = true
 *     }
 * }
 * ```
 */
class OnBotCommand(
    val msgPack: MsgPack,
    private val replyTextAction: (String) -> Boolean,
    private val replyMarkdownAction: (String, Keyboard?) -> Boolean,
    private val replyImageAction: (String, String) -> Boolean
) : Event(), Cancellable {

    /** 仅承载数据、不支持回复的构造（用于测试或只读场景）。 */
    constructor(msgPack: MsgPack) : this(msgPack, { false }, { _, _ -> false }, { _, _ -> false })

    /** Java/Kotlin 插件使用的消息快照别名。 */
    val message: MsgPack get() = msgPack

    /** 回复触发此命令的 QQ 群消息。 */
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
