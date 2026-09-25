package cn.huohuas001.huhobotPenguin.nukkit.commands

import cn.huohuas001.huhobotPenguin.nukkit.HuHoBotNukkit
import cn.nukkit.Server
import cn.nukkit.command.CommandSender
import cn.nukkit.lang.CommandOutputContainer
import cn.nukkit.lang.TextContainer
import cn.nukkit.lang.TranslationContainer
import cn.nukkit.permission.Permission
import cn.nukkit.permission.PermissionAttachment
import cn.nukkit.permission.PermissionAttachmentInfo
import cn.nukkit.plugin.Plugin

/**
 * 仅用于**收集**命令输出的 CommandSender。
 *
 * 实际派发使用服务端真实控制台（见 [NukkitCommandExecutor]），这个对象不参与执行，
 * 只把捕获到的日志/输出攒起来，供 `HExecution.getRawString()` 取回后发到 QQ 群。
 */
class NukkitConsoleSender(private val console: CommandSender, private val plugin: HuHoBotNukkit) : CommandSender {

    private val output = StringBuilder()

    fun clearMessages() {
        output.setLength(0)
    }

    fun getAndClearMessages(): List<String> {
        val lines = output.toString().lines().filter(String::isNotEmpty)
        output.setLength(0)
        return lines
    }

    fun getRawString(): String = output.toString()

    override fun sendMessage(message: String) {
        message.lines().filter(String::isNotEmpty).forEach { output.append(it).append('\n') }
    }

    override fun sendMessage(message: TextContainer) = sendMessage(console.server.language.translate(message))

    override fun sendCommandOutput(container: CommandOutputContainer) {
        container.messages.forEach { sendMessage(console.server.language.translate(TranslationContainer(it.messageId, *it.parameters))) }
    }

    override fun getServer(): Server = console.server
    override fun getName(): String = "CONSOLE"
    override fun isPlayer(): Boolean = false
    override fun isPermissionSet(s: String): Boolean = console.isPermissionSet(s)
    override fun isPermissionSet(permission: Permission): Boolean = console.isPermissionSet(permission)
    override fun hasPermission(s: String): Boolean = console.hasPermission(s)
    override fun hasPermission(permission: Permission): Boolean = console.hasPermission(permission)
    override fun addAttachment(plugin: Plugin): PermissionAttachment = console.addAttachment(plugin)
    override fun addAttachment(plugin: Plugin, name: String): PermissionAttachment = console.addAttachment(plugin, name)
    override fun addAttachment(plugin: Plugin, name: String, value: Boolean?): PermissionAttachment =
        console.addAttachment(plugin, name, value)

    override fun removeAttachment(attachment: PermissionAttachment) = console.removeAttachment(attachment)
    override fun recalculatePermissions() = console.recalculatePermissions()
    override fun getEffectivePermissions(): Map<String, PermissionAttachmentInfo> = console.effectivePermissions
    override fun isOp(): Boolean = true
    override fun setOp(value: Boolean) = Unit
}
