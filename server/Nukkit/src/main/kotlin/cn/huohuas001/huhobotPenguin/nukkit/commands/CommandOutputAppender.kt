package cn.huohuas001.huhobotPenguin.nukkit.commands

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.Logger
import org.apache.logging.log4j.core.appender.AbstractAppender
import org.apache.logging.log4j.core.config.Property
import org.apache.logging.log4j.core.layout.PatternLayout
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 挂在 log4j2 root logger 上的命令输出捕获器。
 *
 * Nukkit-MOT 的 MainLogger 由 Lombok 的 `@Log4j2` 生成，服务端日志本身就走在 log4j2 上，
 * 因此这里可以沿用与 Spigot 适配器完全一致的捕获方案：命令执行期间打开捕获，
 * 2 秒后取走这段时间内服务端产生的全部日志文本。
 */
class CommandOutputAppender private constructor() : AbstractAppender(
    "CommandOutputAppender",
    null,
    PatternLayout.createDefaultLayout(),
    true,
    Property.EMPTY_ARRAY
) {
    private val messages = CopyOnWriteArrayList<String>()

    @Volatile
    private var capturing = false

    init {
        start()
    }

    override fun append(event: LogEvent) {
        if (capturing) {
            messages.add(event.message.formattedMessage)
        }
    }

    fun startCapture() {
        messages.clear()
        capturing = true
    }

    fun stopCapture(): List<String> {
        capturing = false
        return messages.toList()
    }

    /** 返回当前已捕获的日志内容（不会停止捕获，也不会清空）。 */
    fun getCaptured(): List<String> = messages.toList()

    companion object {
        private var instance: CommandOutputAppender? = null

        fun getInstance(): CommandOutputAppender {
            val currentInstance = instance
            if (currentInstance != null) {
                return currentInstance
            }

            return CommandOutputAppender().also { appender ->
                val rootLogger = LogManager.getRootLogger() as Logger
                rootLogger.addAppender(appender)
                instance = appender
            }
        }

        fun removeInstance() {
            instance?.let { appender ->
                val rootLogger = LogManager.getRootLogger() as Logger
                rootLogger.removeAppender(appender)
                appender.stop()
            }
            instance = null
        }
    }
}
