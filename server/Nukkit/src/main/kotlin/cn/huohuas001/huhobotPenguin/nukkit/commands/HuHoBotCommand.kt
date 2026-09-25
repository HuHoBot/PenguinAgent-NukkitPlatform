package cn.huohuas001.huhobotPenguin.nukkit.commands

import cn.huohuas001.bot.web.WebUiPassword
import cn.huohuas001.bot.web.WebUiServer
import cn.huohuas001.huhobotPenguin.nukkit.HuHoBotNukkit
import cn.nukkit.command.CommandSender
import cn.nukkit.utils.TextFormat

/** `/huhobot`（别名 `/hb`）：重载、信息、WebUI 密码与地址。 */
class HuHoBotCommand(private val plugin: HuHoBotNukkit) {

    fun execute(sender: CommandSender, args: Array<String>): Boolean {
        when (args.firstOrNull()?.lowercase()) {
            "reload" -> {
                plugin.fullRestart()
                sender.sendMessage(TextFormat.GOLD.toString() + "HuHoBot 已完整重启。")
            }

            "info" -> sender.sendMessage(
                "平台: ${plugin.getPlatform()}\n" +
                    "版本: ${plugin.getPluginVersion()}\n" +
                    "服务端: ${plugin.getServerVersion()}"
            )

            "password" -> handlePassword(sender, args)

            "webui" -> sender.sendMessage(
                "WebUI 地址: http://127.0.0.1:${plugin.getWebUiPort()}\n" +
                    "WebUI 密码: ${currentPasswordHint()}"
            )

            else -> sendHelp(sender)
        }
        return true
    }

    private fun handlePassword(sender: CommandSender, args: Array<String>) {
        val newPassword = args.getOrNull(1)
        if (newPassword == null) {
            sender.sendMessage("用法: /huhobot password <新密码>")
            return
        }
        if (WebUiPassword.changePassword(newPassword)) {
            WebUiServer.invalidateAllTokens()
            sender.sendMessage(TextFormat.GREEN.toString() + "WebUI 密码已修改。")
        } else {
            sender.sendMessage(TextFormat.RED.toString() + "密码修改失败（密码需至少 6 位）。")
        }
    }

    /** 密码文件不存在时返回「启动时自动生成」，否则提示可用命令修改。 */
    private fun currentPasswordHint(): String =
        if (WebUiPassword.isConfigured()) "已设置（可用 /huhobot password 修改）" else "首次启动时自动生成"

    private fun sendHelp(sender: CommandSender) {
        sender.sendMessage("/huhobot reload - 重载配置并重启 QQ 客户端")
        sender.sendMessage("/huhobot info - 查看适配器信息")
        sender.sendMessage("/huhobot password <新密码> - 修改 WebUI 登录密码")
        sender.sendMessage("/huhobot webui - 查看 WebUI 地址")
        sender.sendMessage("/at <昵称> <消息> - 向 QQ 群发送 @消息")
        sender.sendMessage("/qqbind <验证码> - 绑定 QQ 账号")
        sender.sendMessage("/send <消息> - 向 QQ 群发送消息")
    }
}
