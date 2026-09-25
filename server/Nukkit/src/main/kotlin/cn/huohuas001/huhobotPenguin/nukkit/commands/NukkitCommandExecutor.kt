package cn.huohuas001.huhobotPenguin.nukkit.commands

import cn.huohuas001.bot.provider.HExecution
import cn.huohuas001.huhobotPenguin.nukkit.HuHoBotNukkit
import java.util.concurrent.CompletableFuture

/**
 * Nukkit 侧的命令执行器。
 *
 * 与 Spigot 适配器一致：命令交给**服务端真实控制台**执行（部分命令会做强转/身份判断，
 * 自定义 CommandSender 容易踩坑），输出则通过挂在 log4j2 root logger 上的
 * [CommandOutputAppender] 捕获，固定延迟后回填。
 *
 * [hybrid] 对应 config.yml 的 `command-sender: Hybrid`：除日志外，额外合并命令发送者
 * 维度收集到的输出。两种模式在 Nukkit 上行为接近，保留该开关以对齐配置语义。
 */
class NukkitCommandExecutor(
    private val plugin: HuHoBotNukkit,
    private val hybrid: Boolean
) : HExecution {

    private val sender = NukkitConsoleSender(plugin.server.consoleSender, plugin)
    private val outputAppender by lazy { CommandOutputAppender.getInstance() }

    override fun getRawString(): String {
        val captured = outputAppender.getCaptured()
        return if (captured.isNotEmpty()) captured.joinToString("\n") else sender.getRawString()
    }

    override fun execute(command: String): CompletableFuture<HExecution> {
        val result = CompletableFuture<HExecution>()
        sender.clearMessages()
        outputAppender.startCapture()

        plugin.submit {
            try {
                plugin.server.dispatchCommand(plugin.server.consoleSender, command.removePrefix("/"))
                completeAfterCommandOutput(result)
            } catch (error: Throwable) {
                outputAppender.stopCapture()
                result.completeExceptionally(error)
            }
        }

        return result
    }

    private fun completeAfterCommandOutput(result: CompletableFuture<HExecution>) {
        plugin.submitLater(COMMAND_OUTPUT_DELAY_TICKS) {
            if (hybrid) {
                val senderMessages = sender.getAndClearMessages()
                val loggedMessages = outputAppender.stopCapture()
                (senderMessages + loggedMessages).distinct().forEach(sender::sendMessage)
            } else {
                outputAppender.stopCapture()
            }
            result.complete(this)
        }
    }

    private companion object {
        /** 与服务端 20 TPS 对齐：等待 2 秒再收集输出。 */
        const val COMMAND_OUTPUT_DELAY_TICKS = 40L
    }
}
