package cn.huohuas001.huhobotPenguin.nukkit.manager

import cn.huohuas001.huhobotPenguin.nukkit.HuHoBotNukkit
import com.alibaba.fastjson2.JSON
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * QQ Bot 扫码登录。
 *
 * 首次启动时若 appid/secret 为空，在控制台打印二维码，扫码成功后把凭据写回 config.yml。
 * 与 Spigot 适配器的差别：本流程由 [HuHoBotNukkit.launchQqClient] 放到异步线程执行，
 * 未扫码时不会阻塞服务端启动。
 */
object QrLoginManager {

    private const val CREATE_URL = "https://q.qq.com/lite/create_bind_task"
    private const val POLL_URL = "https://q.qq.com/lite/poll_bind_result"
    private const val CONNECT_URL = "https://q.qq.com/qqbot/openclaw/connect.html"
    private const val POLL_INTERVAL_MS = 2000L
    private const val HTTP_TIMEOUT_MS = 10_000

    private val secureRandom = SecureRandom()

    data class QrCredentials(
        val appId: String,
        val appSecret: String,
        val userOpenid: String?
    )

    /**
     * 执行扫码登录流程，阻塞直到成功（二维码过期会自动刷新）。
     *
     * @return 扫码成功返回凭据；创建任务失败返回 null
     */
    fun doQrLogin(plugin: HuHoBotNukkit): QrCredentials? {
        plugin.log_info("========== QQ Bot 扫码登录 ==========")
        plugin.log_info("首次启动未检测到 bot.app-id / bot.secret")
        plugin.log_info("请使用手机 QQ 扫描以下二维码完成机器人绑定")
        plugin.log_info("")

        while (true) {
            // 用户可能直接在 config.yml 里手填了凭据；此时必须退出扫码循环，
            // 否则会无限刷新二维码刷屏，并一直占着公共线程池的一个 worker。
            if (plugin.hasCredentialsConfigured()) {
                plugin.log_info("检测到 config.yml 已配置 bot.app-id / bot.secret，停止扫码登录")
                return null
            }

            val key = generateBindKey()
            val taskId = try {
                createBindTask(key)
            } catch (error: Exception) {
                plugin.log_error("创建绑定任务失败: ${error.message}")
                return null
            }

            val qrUrl = buildConnectUrl(taskId)
            printQrToConsole(qrUrl, plugin)
            plugin.log_info("扫码链接: $qrUrl")
            plugin.log_info("（扫码后自动继续，过期会自动刷新）")
            plugin.log_info("")

            val result = pollUntilResult(taskId, key, plugin)
            if (result != null) {
                plugin.log_info("")
                plugin.log_info("========== 扫码成功 ==========")
                plugin.log_info("AppID: ${result.appId}")
                plugin.log_info("Secret: ${result.appSecret.take(6)}****")
                writeCredentials(plugin, result)
                return result
            }
            if (plugin.hasCredentialsConfigured()) {
                plugin.log_info("检测到 config.yml 已配置 bot.app-id / bot.secret，停止扫码登录")
                return null
            }

            plugin.log_warning("二维码已过期，正在刷新...")
            plugin.log_info("")
        }
    }

    /** 将扫码获得的凭据写回 config.yml（保留文件中的注释）。 */
    fun writeCredentials(plugin: HuHoBotNukkit, credentials: QrCredentials) {
        try {
            plugin.applyCredentialChanges(credentials.appId, credentials.appSecret)
            plugin.log_info("已将 AppID 和 Secret 写入 config.yml")
        } catch (error: Exception) {
            plugin.log_error("写入 config.yml 失败: ${error.message}")
        }
    }

    // ── 内部实现 ──────────────────────────────────────────

    private fun printQrToConsole(url: String, plugin: HuHoBotNukkit) {
        try {
            val hints = mapOf(
                com.google.zxing.EncodeHintType.MARGIN to 4,
                com.google.zxing.EncodeHintType.ERROR_CORRECTION to com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.M
            )
            val matrix = com.google.zxing.MultiFormatWriter()
                .encode(url, com.google.zxing.BarcodeFormat.QR_CODE, 0, 0, hints)
            // 直接写标准输出：走日志会给每一行加时间戳前缀，破坏二维码的方阵比例。
            for (y in 0 until matrix.height) {
                val line = StringBuilder()
                for (x in 0 until matrix.width) {
                    line.append(if (matrix.get(x, y)) "██" else "  ")
                }
                println(line)
            }
        } catch (error: Exception) {
            plugin.log_warning("无法渲染二维码，请扫描以下链接:")
            plugin.log_info(url)
        }
    }

    private fun generateBindKey(): String {
        val bytes = ByteArray(32).also(secureRandom::nextBytes)
        return Base64.getEncoder().encodeToString(bytes)
    }

    private fun createBindTask(key: String): String {
        val body = JSON.toJSONString(mapOf("key" to key))
        val response = httpPost(CREATE_URL, body)
        val json = JSON.parseObject(response)
        if (json.getIntValue("retcode") != 0) {
            throw RuntimeException("create_bind_task failed: ${json.getString("msg")}")
        }
        val taskId = json.getJSONObject("data")?.getString("task_id")
        if (taskId.isNullOrBlank()) {
            throw RuntimeException("create_bind_task: missing task_id")
        }
        return taskId
    }

    private fun buildConnectUrl(taskId: String): String {
        val encodedTaskId = URLEncoder.encode(taskId, "UTF-8").replace("+", "%20")
        val encodedSource = URLEncoder.encode("openclaw", "UTF-8").replace("+", "%20")
        return "$CONNECT_URL?task_id=$encodedTaskId&source=$encodedSource&_wv=2"
    }

    private fun pollUntilResult(taskId: String, key: String, plugin: HuHoBotNukkit): QrCredentials? {
        while (true) {
            Thread.sleep(POLL_INTERVAL_MS)
            try {
                val body = JSON.toJSONString(mapOf("task_id" to taskId))
                val json = JSON.parseObject(httpPost(POLL_URL, body))
                if (json.getIntValue("retcode") != 0) {
                    plugin.log_warning("轮询失败: ${json.getString("msg")}，继续重试...")
                    continue
                }
                val data = json.getJSONObject("data") ?: continue
                when (data.getIntValue("status")) {
                    // 2 = COMPLETED
                    2 -> {
                        val appId = data.get("bot_appid")?.toString() ?: ""
                        val encryptedSecret = data.getString("bot_encrypt_secret") ?: ""
                        return QrCredentials(appId, decryptSecret(encryptedSecret, key), data.getString("user_openid"))
                    }
                    // 3 = EXPIRED
                    3 -> return null
                    // 0 = NONE, 1 = PENDING → 继续轮询
                }
            } catch (error: Exception) {
                plugin.log_warning("轮询异常: ${error.message}，继续重试...")
            }
        }
    }

    /**
     * AES-256-GCM 解密 AppSecret。
     * 密文布局：IV(12) || ciphertext || AuthTag(16)
     */
    private fun decryptSecret(encryptedBase64: String, keyBase64: String): String {
        val key = Base64.getDecoder().decode(keyBase64)
        val blob = Base64.getDecoder().decode(encryptedBase64)

        require(key.size == 32) { "key must be 32 bytes (AES-256), got ${key.size}" }
        require(blob.size > 12 + 16) { "ciphertext too short: ${blob.size}" }

        val iv = blob.copyOfRange(0, 12)
        val tag = blob.copyOfRange(blob.size - 16, blob.size)
        val cipherText = blob.copyOfRange(12, blob.size - 16)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(cipherText + tag), StandardCharsets.UTF_8)
    }

    private fun httpPost(url: String, body: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Accept", "application/json")
        connection.connectTimeout = HTTP_TIMEOUT_MS
        connection.readTimeout = HTTP_TIMEOUT_MS
        connection.doOutput = true

        connection.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }

        val statusCode = connection.responseCode
        return if (statusCode in 200..299) {
            connection.inputStream.use { String(it.readBytes(), StandardCharsets.UTF_8) }
        } else {
            val error = connection.errorStream?.use { String(it.readBytes(), StandardCharsets.UTF_8) }
            throw RuntimeException("HTTP $statusCode: $error")
        }
    }
}
