package cn.huohuas001.huhobotPenguin.nukkit.commands

import cn.huohuas001.huhobotPenguin.nukkit.HuHoBotNukkit
import cn.nukkit.command.CommandSender
import cn.nukkit.utils.TextFormat

/** `/send <消息>`：把所有消息发到配置的 QQ 群。 */
class SendCommand(private val plugin: HuHoBotNukkit) {

    fun execute(sender: CommandSender, args: Array<String>): Boolean {
        if (args.isEmpty()) {
            sender.sendMessage("用法: /send <消息内容>")
            return true
        }
        plugin.sendText(args.joinToString(" "))
        sender.sendMessage(TextFormat.GREEN.toString() + "已发送到 QQ 群。")
        return true
    }
}
