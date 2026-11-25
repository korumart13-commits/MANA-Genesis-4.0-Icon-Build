package com.mana.os.integration

import android.content.Context
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URI
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.UUID
import javax.net.ssl.HttpsURLConnection

/** Calls the separately deployed MANA TTS service. Missing config/credentials fail closed. */
class ManaVoiceGatewayClient(context: Context) {
    private val appContext = context.applicationContext
    @Volatile private var activeConnection: HttpsURLConnection? = null
    @Volatile private var cancelled = false

    fun isConfigured(): Boolean = ManaVoiceDeploymentConfig.isConfigured()

    fun synthesize(
        text: String,
        callback: (audioFile: File?, error: String?) -> Unit
    ): Boolean {
        if (!isConfigured() || text.isBlank()) return false
        cancel()
        cancelled = false
        Thread({
            var output: File? = null
            try {
                val token = ManaAndroidGatewayCredentialStore(appContext).currentBearerToken()
                    ?: throw IllegalStateException("Авторизация голосового сервиса MANA не настроена.")
                require(token.matches(Regex("[A-Za-z0-9._~+/=-]{16,4096}"))) {
                    "Недействительный токен MANA."
                }
                val uri = URI(ManaVoiceDeploymentConfig.ENDPOINT)
                require(uri.scheme.equals("https", true) &&
                    uri.host.equals(ManaVoiceDeploymentConfig.EXPECTED_HOST, true) &&
                    uri.path == "/v1/mana/voice/synthesize" &&
                    uri.userInfo == null && uri.query == null && uri.fragment == null &&
                    (uri.port == -1 || uri.port == 443)) {
                    "Конфигурация голосового шлюза MANA не прошла проверку."
                }
                val connection = URL(uri.toString()).openConnection() as HttpsURLConnection
                activeConnection = connection
                connection.instanceFollowRedirects = false
                connection.requestMethod = "POST"
                connection.connectTimeout = 8_000
                connection.readTimeout = 60_000
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.setRequestProperty("Accept", "audio/wav")
                connection.setRequestProperty("Authorization", "Bearer $token")
                val body = JSONObject().put("text", text.take(1_200)).toString()
                    .toByteArray(StandardCharsets.UTF_8)
                connection.setFixedLengthStreamingMode(body.size)
                connection.outputStream.use { it.write(body) }
                check(!cancelled) { "Запрос голоса остановлен." }
                val status = connection.responseCode
                require(status in 200..299) {
                    when (status) {
                        401, 403 -> "Доступ к голосу MANA не подтверждён."
                        503 -> "Голосовая модель MANA пока не запущена."
                        else -> "Голосовой сервис MANA ответил с ошибкой HTTP $status."
                    }
                }
                require(connection.contentType?.lowercase()?.startsWith("audio/wav") == true) {
                    "Голосовой сервис вернул неизвестный формат аудио."
                }
                val bytes = connection.inputStream.use { input ->
                    val out = ByteArrayOutputStream()
                    val buffer = ByteArray(8_192)
                    var total = 0
                    while (true) {
                        check(!cancelled) { "Запрос голоса остановлен." }
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= 12 * 1024 * 1024) { "Аудио MANA превысило лимит размера." }
                        out.write(buffer, 0, count)
                    }
                    out.toByteArray()
                }
                require(bytes.size > 44) { "Голосовой сервис вернул пустой WAV." }
                output = File(appContext.cacheDir, "mana-voice-${UUID.randomUUID()}.wav")
                output.writeBytes(bytes)
                if (cancelled) {
                    output.delete()
                    output = null
                    throw IllegalStateException("Запрос голоса остановлен.")
                }
                callback(output, null)
                output = null
            } catch (error: Exception) {
                callback(null, error.message?.take(250) ?: "Не удалось получить голос Маны.")
            } finally {
                output?.delete()
                activeConnection?.disconnect()
                activeConnection = null
            }
        }, "mana-voice-synthesis").start()
        return true
    }

    fun cancel() {
        cancelled = true
        activeConnection?.disconnect()
        activeConnection = null
    }
}
