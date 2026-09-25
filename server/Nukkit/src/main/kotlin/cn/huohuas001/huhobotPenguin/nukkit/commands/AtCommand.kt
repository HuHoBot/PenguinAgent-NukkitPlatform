package cn.huohuas001.huhobotPenguin.nukkit.commands

import cn.huohuas001.bot.NicknameManager
import cn.huohuas001.bot.QClient
import cn.huohuas001.huhobotPenguin.nukkit.HuHoBotNukkit
import cn.nukkit.command.CommandSender
import cn.nukkit.utils.TextFormat

/** `/at <群成员昵称> <消息>`：在游戏内向 QQ 群发送 @消息。 */
class AtCommand(private val plugin: HuHoBotNukkit) {

    fun execute(sender: CommandSender, args: Array<String>): Boolean {
        if (!sender.isPlayer) {
            sender.sendMessage(TextFormat.RED.toString() + "此命令仅限游戏内使用")
            return true
        }
        if (args.size < 2) {
            sender.sendMessage(TextFormat.RED.toString() + "用法: /at <群成员昵称> <消息内容>")
            return true
        }
        val targetName = args[0]
        val message = args.drop(1).joinToString(" ")
        // 昵称能解析出 openid 时使用真正的 QQ @（会触发被 @ 玩家的通知），否则退化成纯文本 @昵称。
        val openId = NicknameManager.getOpenId(targetName)
        val atText = if (openId != null) "<@$openId>" else "@$targetName"
        QClient.sendAtToGroups(sender.name, "$atText $message")
        sender.sendMessage(TextFormat.GREEN.toString() + "已发送 @消息 到 QQ 群")
        return true
    }
}
