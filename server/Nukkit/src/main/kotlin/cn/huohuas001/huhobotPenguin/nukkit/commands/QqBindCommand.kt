package cn.huohuas001.huhobotPenguin.nukkit.commands

import cn.huohuas001.bot.events.commands.BindingCommands
import cn.huohuas001.bot.state.PendingBindingStore
import cn.huohuas001.huhobotPenguin.nukkit.HuHoBotNukkit
import cn.nukkit.command.CommandSender
import cn.nukkit.utils.TextFormat

/** `/qqbind <验证码>`：用 QQ 群里 /绑定 生成的验证码完成游戏账号绑定。 */
class QqBindCommand(private val plugin: HuHoBotNukkit) {

    fun execute(sender: CommandSender, args: Array<String>): Boolean {
        if (!sender.isPlayer) {
            sender.sendMessage(TextFormat.RED.toString() + "此命令仅限游戏内使用")
            return true
        }
        if (args.isEmpty()) {
            sender.sendMessage(TextFormat.RED.toString() + "用法: /qqbind <验证码>")
            sender.sendMessage(TextFormat.GRAY.toString() + "验证码由 QQ 群内 /绑定 命令生成")
            return true
        }

        val pending = PendingBindingStore.consume(args[0].trim())
        if (pending == null) {
            sender.sendMessage(TextFormat.RED.toString() + "验证码无效或已过期，请在 QQ 群中重新使用 /绑定 命令")
            return true
        }

        val playerName = sender.name
        if (!pending.playerName.equals(playerName, ignoreCase = true)) {
            sender.sendMessage(
                TextFormat.RED.toString() + "此验证码绑定的游戏ID为「${pending.playerName}」，与你当前账号不匹配"
            )
            return true
        }

        val success = BindingCommands.completeBind(
            groupId = pending.groupId,
            openId = pending.openId,
            playerName = playerName,
            qqUsername = pending.qqUsername
        )

        if (success) {
            sender.sendMessage(TextFormat.GREEN.toString() + "已绑定QQ账号：" + TextFormat.WHITE + pending.qqUsername)
            // 绑定成功后自动添加白名单。
            val whitelist = plugin.getWhiteList()
            if (whitelist.addCommand.isNotBlank()) {
                val command = whitelist.addCommand.replace("{name}", playerName)
                plugin.submit {
                    plugin.server.dispatchCommand(plugin.server.consoleSender, command.removePrefix("/"))
                }
            }
        } else {
            sender.sendMessage(TextFormat.RED.toString() + "绑定失败：游戏ID「$playerName」可能已被其他账号绑定")
        }
        return true
    }
}
