package com.mana.os.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.webkit.WebView
import android.webkit.WebViewClient
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.media.MediaPlayer
import android.widget.*
import com.mana.os.integration.AndroidSelfDevelopment
import com.mana.os.integration.ManaGenesis
import java.util.Locale

/** First MANA home surface. Cloud status is truthful; no server URL is requested from the owner. */
class ManaHomeActivity : Activity(), TextToSpeech.OnInitListener {
    private var textToSpeech: TextToSpeech? = null
    private var ttsReady = false
    private var avatarWebView: WebView? = null
    private var voicePlayer: MediaPlayer? = null
    private val voiceClient by lazy { com.mana.os.integration.ManaVoiceGatewayClient(this) }
    private val prefs by lazy { getSharedPreferences("mana_setup", MODE_PRIVATE) }
    private lateinit var content: LinearLayout
    private lateinit var status: TextView
    private lateinit var chatLog: TextView
    private lateinit var message: EditText
    private val conversationId = java.util.UUID.randomUUID().toString()
    private val dialogueState = com.mana.os.integration.ManaDialogueState()
    private val localMemory by lazy { com.mana.os.integration.ManaLocalCognitiveMemory(this) }
    private val gatewayChat by lazy { com.mana.os.integration.ManaGatewayChatService(this, conversationId, dialogueState) }

    private val bg = Color.rgb(5, 15, 34)
    private val panel = Color.rgb(12, 32, 62)
    private val accent = Color.rgb(64, 190, 255)
    private val primaryText = Color.rgb(235, 245, 255)
    private val muted = Color.rgb(143, 174, 207)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        textToSpeech = TextToSpeech(this, this)
        restoreDialogueFromLocalMemory()
        window.statusBarColor = bg
        window.navigationBarColor = bg
        renderHome()
    }

    private fun renderHome() {
        val scroll = ScrollView(this).apply { setBackgroundColor(bg); isFillViewport = true }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(26), dp(20), dp(28))
        }
        scroll.addView(content)
        setContentView(scroll)

        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; orientation = LinearLayout.HORIZONTAL }
        val brand = TextView(this).apply {
            text = "MANA"; textSize = 29f; typeface = Typeface.DEFAULT_BOLD; setTextColor(accent)
        }
        top.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(TextView(this).apply {
            text = "GENESIS 4.0"; textSize = 11f; letterSpacing = .12f; setTextColor(muted)
        })
        content.addView(top)
        content.addView(label("ГЛАВНЫЙ ЭКРАН · MANA HOME", 11f, muted, true), marginTop(18))
        addLauncherGrid()

        val avatarPanel = card()
        avatarPanel.orientation = LinearLayout.HORIZONTAL
        avatarPanel.gravity = Gravity.CENTER_VERTICAL
        val avatar = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = false
            settings.allowFileAccess = true
            settings.allowContentAccess = false
            settings.allowFileAccessFromFileURLs = true
            settings.allowUniversalAccessFromFileURLs = false
            webViewClient = WebViewClient()
            setBackgroundColor(Color.TRANSPARENT)
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            loadUrl("file:///android_asset/mana-avatar/index.html")
        }
        avatarWebView = avatar
        avatarPanel.addView(avatar, LinearLayout.LayoutParams(dp(112), dp(142)))
        val intro = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), 0, 0, 0) }
        intro.addView(label("Привет. Я Мана.", 20f, primaryText, true))
        intro.addView(label("Я — Мана. Мой аватар всегда рядом, когда ты этого хочешь.", 13f, muted))
        intro.addView(label("Оригинальный 3D-аватар MANA. Голос пока использует установленный русский движок Android.", 11f, muted))
        avatarPanel.addView(intro, LinearLayout.LayoutParams(0, -2, 1f))
        content.addView(avatarPanel, marginTop(20))

        status = label("Локальное ядро MANA · проверка готовности…", 12f, accent)
        content.addView(status, marginTop(14))

        section("ПОГОВОРИТЬ С МАНОЙ")
        val chatCard = card()
        chatLog = label("Напиши или скажи мне что-нибудь. Я использую собственное локальное ядро MANA.", 14f, primaryText)
        chatCard.addView(chatLog)
        message = EditText(this).apply {
            hint = "Сообщение для Маны…"; setHintTextColor(muted); setTextColor(primaryText)
            textSize = 15f; minLines = 1; maxLines = 4
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = rounded(Color.rgb(7, 22, 45), 14)
        }
        chatCard.addView(message, marginTop(12))
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(actionButton("Отправить") { sendMessage() }, LinearLayout.LayoutParams(0, dp(48), 1f))
        actions.addView(actionButton("Голос") { startVoiceInput() }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { leftMargin = dp(8) })
        actions.addView(actionButton("Стоп") {
            if (!gatewayChat.stop()) toast("Сейчас нет активного запроса.")
        }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { leftMargin = dp(8) })
        chatCard.addView(actions, marginTop(8))
        content.addView(chatCard, marginTop(8))

        section("ТВОЁ ПРОСТРАНСТВО")
        val appButton = actionButton("Приложения телефона") { showInstalledApps() }
        content.addView(appButton, marginTop(6))
        val selfButton = actionButton("Саморазвитие MANA") { showSelfDevelopment() }
        content.addView(selfButton, marginTop(8))
        content.addView(actionButton("✦ Оживи MANA · фоновый аватар") { awakenMana() }, marginTop(8))
        content.addView(actionButton("Скрыть фонового аватара") { stopManaAvatar() }, marginTop(8))
        val diaryButton = actionButton("Дневник MANA") {
            startActivity(Intent(this, ManaDiaryActivity::class.java))
        }
        content.addView(diaryButton, marginTop(8))
        val lifeManagerButton = actionButton("Задачи и планы MANA") {
            startActivity(Intent(this, ManaLifeManagerActivity::class.java))
        }
        content.addView(lifeManagerButton, marginTop(8))
        val modelButton = actionButton("Подготовить локальный ИИ MANA · 347 МБ") {
            status.text = "MANA загружает и проверяет локальную модель Qwen3…"
            gatewayChat.prepareLocalModel { _, detail ->
                runOnUiThread { status.text = detail; toast(detail) }
            }
        }
        content.addView(modelButton, marginTop(8))
        val memoryButton = actionButton("Память Маны") { showMemory() }
        content.addView(memoryButton, marginTop(8))
        val settingsButton = actionButton("Настройки и разрешения Android") {
            startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                android.net.Uri.parse("package:$packageName")))
        }
        content.addView(settingsButton, marginTop(8))
        val permissionCenterButton = actionButton("Центр разрешений MANA") { showPermissionCenter() }
        content.addView(permissionCenterButton, marginTop(8))
        val homeButton = actionButton("Сделать MANA главным экраном") {
            startActivity(Intent(android.provider.Settings.ACTION_HOME_SETTINGS))
        }
        content.addView(homeButton, marginTop(8))

        section("МОДУЛИ MANA")
        val modules = listOf(
            "Mana Brain" to "Единый интеллект и память — базовая интеграция",
            "Diary и Life Manager" to "Следующий этап разработки",
            "Smart Receptionist" to "Требует роли приложения телефона и разрешений",
            "Minecraft и Game Intelligence" to "Требуется отдельный совместимый игровой бот",
            "Mana Developer" to "Сбор идей и локальный анализ доступны",
            "Mana Shield" to "Сохраняем штатную защиту Android"
        )
        modules.forEach { (name, detail) ->
            val item = card()
            item.addView(label(name, 15f, primaryText, true))
            item.addView(label(detail, 12f, muted))
            content.addView(item, marginTop(7))
        }
        content.addView(label("MANA не отключает Play Protect, SELinux или Verified Boot. Действия с системными приложениями ограничиваются Android и требуют подтверждения.", 11f, muted), marginTop(16))
    }

    private fun sendMessage() {
        val text = message.text.toString().trim()
        if (text.isEmpty()) return
        chatLog.text = "Ты: $text\n\nМана: подготавливаю локальное ядро MANA…"
        message.setText("")
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(message.windowToken, 0)
        val started = gatewayChat.send(text) { answer, error, approvals ->
            runOnUiThread {
                val reply = answer ?: "Не удалось получить ответ: ${error ?: "локальное ядро недоступно"}"
                chatLog.text = "Ты: $text\n\nМана: $reply"
                if (answer != null) {
                    speak(answer)
                    recordConversation(text, answer)
                }
                if (approvals.isNotEmpty()) showOwnerApprovalDialogs(approvals)
            }
        }
        if (!started) toast("Локальное ядро MANA сейчас занято. Попробуй немного позже.")
    }

    /** Restore only recent chat exchanges from app-private local memory into bounded dialogue context. */
    private fun restoreDialogueFromLocalMemory() {
        localMemory.recentEntries(30).asReversed()
            .filter { it.kind == "chat_exchange" }
            .forEach { entry ->
                val userStart = entry.text.indexOf("Ты: ")
                val assistantStart = entry.text.indexOf("\nМана: ")
                if (userStart >= 0 && assistantStart > userStart) {
                    val userText = entry.text.substring(userStart + 4, assistantStart).trim()
                    val assistantText = entry.text.substring(assistantStart + 7).trim()
                    if (userText.isNotBlank() && assistantText.isNotBlank()) {
                        dialogueState.add(
                            com.mana.os.integration.ManaDialogueTurn(
                                topic = userText.take(80),
                                userSummary = userText.take(500),
                                assistantSummary = assistantText.take(500),
                                tone = com.mana.os.integration.ManaDialogueTone.NEUTRAL,
                                timestampMillis = entry.timestampMillis
                            )
                        )
                    }
                }
            }
    }

    /** Save a concise, bounded local conversation summary; this is not sent to any server. */
    private fun recordConversation(userText: String, assistantText: String) {
        dialogueState.add(
            com.mana.os.integration.ManaDialogueTurn(
                topic = userText.take(80),
                userSummary = userText.take(500),
                assistantSummary = assistantText.take(500),
                tone = com.mana.os.integration.ManaDialogueTone.NEUTRAL
            )
        )
        localMemory.remember(
            com.mana.os.integration.ManaCognitiveMemoryEntry(
                conversationId = conversationId,
                kind = "chat_exchange",
                text = "Ты: ${userText.take(500)}\nМана: ${assistantText.take(500)}"
            )
        )
    }

    private fun awakenMana() {
        val engine = AndroidSelfDevelopment(this)
        if (!engine.isSelfDevelopmentEnabled()) { toast("Саморазвитие выключено в настройках MANA."); return }
        if (!android.provider.Settings.canDrawOverlays(this)) {
            AlertDialog.Builder(this).setTitle("Разрешить фоновый аватар?")
                .setMessage("Чтобы Мана отображалась поверх других приложений, Android должен разрешить показ поверх окон. Микрофон постоянно не включается.")
                .setPositiveButton("Открыть настройки") { _, _ ->
                    startActivityForResult(Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:$packageName")), 704)
                }.setNegativeButton("Не сейчас", null).show()
            return
        }
        val proposals = runCatching { engine.analyze() }.getOrDefault(emptyList())
        val service = Intent(this, com.mana.os.integration.ManaAvatarService::class.java)
        try {
            if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(service) else startService(service)
            toast("Мана оживает. Предложений саморазвития: " + proposals.size)
        } catch (error: Exception) { toast("Не удалось запустить аватар: " + (error.message ?: "ошибка Android")) }
    }

    private fun stopManaAvatar() {
        stopService(Intent(this, com.mana.os.integration.ManaAvatarService::class.java))
        toast("Фоновый аватар остановлен.")
    }

    private fun startVoiceInput() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 702)
            return
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Говори, я слушаю")
        }
        try { startActivityForResult(intent, 703) }
        catch (_: Exception) { toast("На устройстве нет доступного распознавания речи.") }
    }

    @Deprecated("Android activity result compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 704) {
            if (android.provider.Settings.canDrawOverlays(this)) awakenMana()
            else toast("Разрешение поверх окон не выдано.")
        }
        if (requestCode == 705 && resultCode == RESULT_OK) {
            val uri = data?.data
            if (uri != null) {
                Thread({
                    try {
                        val modelDir = java.io.File(filesDir, com.mana.os.integration.ManaLiteRtLmRuntime.MODEL_DIRECTORY)
                        if (!modelDir.exists() && !modelDir.mkdirs()) error("Не удалось создать каталог модели.")
                        val target = java.io.File(modelDir, com.mana.os.integration.ManaLiteRtLmRuntime.MODEL_FILE_NAME)
                        val temp = java.io.File(modelDir, "mana-local.litertlm.part")
                        contentResolver.openInputStream(uri).use { input ->
                            requireNotNull(input) { "Не удалось прочитать выбранный файл." }
                            temp.outputStream().use { output -> input.copyTo(output) }
                        }
                        require(temp.length() >= 1_000_000L) { "Файл слишком мал для языковой модели." }
                        if (target.exists() && !target.delete()) error("Не удалось заменить предыдущую модель.")
                        if (!temp.renameTo(target)) error("Не удалось завершить установку модели.")
                        runOnUiThread { toast("Файл модели сохранён. Перезапусти MANA, чтобы загрузить локальный движок.") }
                    } catch (error: Exception) {
                        runOnUiThread { toast("Не удалось установить модель: ${error.message}") }
                    }
                }, "mana-model-import").start()
            }
        }
        if (requestCode == 703 && resultCode == RESULT_OK) {
            val spoken = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                if (spoken.trim().equals("оживи", ignoreCase = true) || spoken.trim().startsWith("оживи ")) awakenMana()
                else { message.setText(spoken); sendMessage() }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 702 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) startVoiceInput()
        else if (requestCode == 702) toast("Доступ к микрофону не выдан. Можно продолжить текстом.")
    }


    override fun onInit(status: Int) {
        ttsReady = status == TextToSpeech.SUCCESS
        if (ttsReady) {
            val result = textToSpeech?.setLanguage(Locale("ru", "RU"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                ttsReady = false
            }
            textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) { setAvatarSpeaking(true) }
                override fun onDone(utteranceId: String?) { setAvatarSpeaking(false) }
                override fun onError(utteranceId: String?) { setAvatarSpeaking(false) }
            })
        }
    }

    private fun speak(text: String) {
        if (text.isBlank()) return
        if (voiceClient.isConfigured() &&
            com.mana.os.integration.ManaAndroidGatewayCredentialStore(this).currentBearerToken() != null) {
            val started = voiceClient.synthesize(text) { audioFile, error ->
                runOnUiThread {
                    if (isFinishing || isDestroyed) {
                        audioFile?.delete()
                        return@runOnUiThread
                    }
                    if (audioFile == null) {
                        speakWithSystemTts(text)
                        if (!error.isNullOrBlank()) toast("Голос MANA недоступен. Использую голос Android.")
                    } else {
                        try {
                            voicePlayer?.release()
                            val player = MediaPlayer()
                            voicePlayer = player
                            player.setDataSource(audioFile.absolutePath)
                            player.setOnPreparedListener {
                                setAvatarSpeaking(true)
                                it.start()
                            }
                            player.setOnCompletionListener {
                                setAvatarSpeaking(false)
                                it.release()
                                if (voicePlayer === it) voicePlayer = null
                                audioFile.delete()
                            }
                            player.setOnErrorListener { failed, _, _ ->
                                setAvatarSpeaking(false)
                                failed.release()
                                if (voicePlayer === failed) voicePlayer = null
                                audioFile.delete()
                                speakWithSystemTts(text)
                                true
                            }
                            player.prepareAsync()
                        } catch (_: Exception) {
                            audioFile.delete()
                            voicePlayer?.release()
                            voicePlayer = null
                            speakWithSystemTts(text)
                        }
                    }
                }
            }
            if (started) return
        }
        speakWithSystemTts(text)
    }

    private fun speakWithSystemTts(text: String) {
        if (ttsReady && text.isNotBlank()) {
            textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "mana_reply")
        }
    }

    private fun setAvatarSpeaking(speaking: Boolean) {
        runOnUiThread {
            avatarWebView?.evaluateJavascript(
                "window.ManaAvatar && window.ManaAvatar.setSpeaking($speaking);", null
            )
        }
    }

    override fun onDestroy() {
        gatewayChat.close()
        voiceClient.cancel()
        runCatching { voicePlayer?.stop() }
        voicePlayer?.release()
        voicePlayer = null
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        avatarWebView?.stopLoading()
        avatarWebView?.destroy()
        avatarWebView = null
        textToSpeech = null
        super.onDestroy()
    }

    /** Read-only view of MANA approval state and bounded in-memory audit events. */
    private fun showPermissionCenter() {
        val pending = gatewayChat.pendingApprovalRequests()
        val events = gatewayChat.approvalAuditEvents().asReversed().take(20)
        val details = buildString {
            append("Ожидают решения: ").append(pending.size)
            append("\n\nОдобрение MANA не равно системному разрешению Android.")
            append("\nНикакие разрешения Android не выдаются автоматически.")
            append("\nДоступно с подтверждением: запуск приложения, найденного Android.")
            append("\nОстальные действия пока заблокированы и не исполняются.")
            append("\n\nПоследние события:")
            if (events.isEmpty()) append("\nПока событий нет.")
            events.forEach { event ->
                val time = java.text.DateFormat.getTimeInstance().format(java.util.Date(event.timestampMillis))
                append("\n• ").append(time).append(" · ").append(event.actionName)
                    .append(" · ").append(event.event)
            }
            append("\n\nЖурнал хранится только в памяти текущего процесса и очищается после закрытия приложения.")
        }
        AlertDialog.Builder(this)
            .setTitle("Центр разрешений MANA")
            .setMessage(details)
            .setPositiveButton("Настройки Android") { _, _ ->
                startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.parse("package:$packageName")))
            }
            .setNegativeButton("Закрыть", null)
            .show()
    }

    /** Show each exact proposed action and its arguments before the owner decides. */
    private fun showOwnerApprovalDialogs(requests: List<com.mana.os.integration.ManaApprovalRequest>) {
        fun showAt(index: Int) {
            if (index >= requests.size || isFinishing || isDestroyed) return
            val request = requests[index]
            val launchLabel = gatewayChat.previewApproval(request)
            val supportedLaunch = request.actionName == "apps.launch" && launchLabel != null
            val supportedSettingsPage = request.actionName == "settings.open_page" && launchLabel != null
            val supportedDnd = request.actionName == "device.set_dnd_filter" && launchLabel != null
            val supportedAudio = request.actionName == "device.set_audio_volume" && launchLabel != null
            val supportedAction = supportedLaunch || supportedSettingsPage || supportedDnd || supportedAudio
            val details = buildString {
                append(request.summary)
                append("\n\nДействие: ").append(request.actionName)
                if (supportedLaunch) {
                    append("\n\nПриложение, найденное Android: ").append(launchLabel)
                    append("\nПакет: ").append(request.arguments["package_name"])
                    append("\n\nПосле подтверждения Android получит команду открыть это приложение.")
                } else if (supportedSettingsPage) {
                    append("\n\nРаздел настроек Android: ").append(launchLabel)
                    append("\n\nПосле подтверждения откроется штатная страница Android. MANA не будет менять переключатели или параметры автоматически.")
                } else if (supportedDnd) {
                    append("\n\nРежим телефона: ").append(launchLabel)
                    append("\n\nПосле подтверждения Android изменит режим «Не беспокоить». Режим останется включённым, пока ты не изменишь его вручную.")
                } else if (supportedAudio) {
                    append("\n\nНовая громкость: ").append(launchLabel)
                    append("\n\nПосле подтверждения MANA изменит только выбранную громкость звонка или мультимедиа. Текущая громкость будет заменена.")
                } else {
                    append("\n\nПараметры:")
                    request.arguments.forEach { (key, value) ->
                        append("\n• ").append(key).append(": ").append(value)
                    }
                    append("\n\nИсполнитель этого действия ещё не подключён. Оно не будет выполнено.")
                }
                val seconds = ((request.expiresAtMillis - System.currentTimeMillis()).coerceAtLeast(0L) / 1000L)
                append("\n\nЗапрос действует ещё примерно ").append(seconds).append(" сек.")
            }
            AlertDialog.Builder(this)
                .setTitle("Запрос разрешения MANA")
                .setMessage(details)
                .setPositiveButton(when {
                    supportedLaunch -> "Подтвердить запуск"
                    supportedSettingsPage -> "Открыть настройки"
                    supportedDnd -> "Применить режим"
                    supportedAudio -> "Изменить громкость"
                    else -> "Закрыть без выполнения"
                }) { _, _ ->
                    if (supportedAction) {
                        val result = gatewayChat.confirmApproval(request.requestId)
                        toast(result.message)
                    } else {
                        gatewayChat.denyApproval(request.requestId)
                        toast("Действие пока недоступно. Ничего не изменено.")
                    }
                    showAt(index + 1)
                }
                .setNegativeButton("Отклонить") { _, _ ->
                    gatewayChat.denyApproval(request.requestId)
                    toast("Запрос отклонён. Ничего не изменено.")
                    showAt(index + 1)
                }
                .setOnCancelListener {
                    gatewayChat.denyApproval(request.requestId)
                    showAt(index + 1)
                }
                .show()
        }
        showAt(0)
    }

    /** Native app grid for the MANA HOME surface; launches apps through Android's package manager. */
    private fun addLauncherGrid() {
        val pm = packageManager
        val apps = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
            PackageManager.MATCH_DEFAULT_ONLY
        ).distinctBy { it.activityInfo.packageName }
            .sortedBy { it.loadLabel(pm).toString().lowercase() }
            .take(12)
        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        apps.chunked(4).forEach { group ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
            group.forEach { info ->
                val tile = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setPadding(dp(5), dp(10), dp(5), dp(10))
                    isClickable = true
                    isFocusable = true
                    background = rounded(Color.rgb(10, 30, 56), 16)
                    setOnClickListener {
                        packageManager.getLaunchIntentForPackage(info.activityInfo.packageName)?.let { launch ->
                            runCatching { startActivity(launch) }.onFailure { toast("Не удалось открыть приложение.") }
                        }
                    }
                }
                val icon = ImageView(this).apply {
                    setImageDrawable(info.loadIcon(pm))
                    scaleType = ImageView.ScaleType.FIT_CENTER
                }
                tile.addView(icon, LinearLayout.LayoutParams(dp(42), dp(42)))
                tile.addView(TextView(this).apply {
                    text = info.loadLabel(pm).toString()
                    textSize = 10f
                    maxLines = 2
                    gravity = Gravity.CENTER
                    setTextColor(primaryText)
                }, LinearLayout.LayoutParams(-1, dp(34)))
                row.addView(tile, LinearLayout.LayoutParams(0, dp(92), 1f).apply {
                    setMargins(dp(3), dp(3), dp(3), dp(3))
                })
            }
            repeat(4 - group.size) {
                row.addView(View(this), LinearLayout.LayoutParams(0, dp(92), 1f))
            }
            grid.addView(row)
        }
        if (apps.isEmpty()) grid.addView(label("Приложения появятся здесь после установки.", 12f, muted))
        content.addView(grid, marginTop(8))
        content.addView(actionButton("Все приложения") { showInstalledApps() }, marginTop(6))
    }

    private fun showInstalledApps() {
        val pm = packageManager
        val apps = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), PackageManager.MATCH_DEFAULT_ONLY)
            .distinctBy { it.activityInfo.packageName }.sortedBy { it.loadLabel(pm).toString().lowercase() }
        val names = apps.map { it.loadLabel(pm).toString() }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Приложения телефона").setItems(names) { _, which ->
            pm.getLaunchIntentForPackage(apps[which].activityInfo.packageName)?.let { startActivity(it) }
        }.setNegativeButton("Закрыть", null).show()
    }

    private fun chooseLocalModel() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        startActivityForResult(intent, 705)
    }

    private fun showMemory() {
        val memory = com.mana.os.integration.ManaLocalCognitiveMemory(this)
        val entries = memory.recentEntries(20)
        val summary = if (entries.isEmpty()) {
            "Локальная память пока пуста."
        } else {
            entries.joinToString("\n\n") { entry ->
                val date = java.text.DateFormat.getDateTimeInstance().format(java.util.Date(entry.timestampMillis))
                "[$date] ${entry.text.take(350)}"
            }
        }
        AlertDialog.Builder(this)
            .setTitle("Память Маны · ${memory.count()} записей")
            .setMessage(summary)
            .setPositiveButton("Очистить память") { _, _ ->
                AlertDialog.Builder(this)
                    .setTitle("Очистить локальную память?")
                    .setMessage("Все сохранённые записи памяти MANA на этом телефоне будут удалены. Это действие нельзя отменить.")
                    .setPositiveButton("Очистить") { _, _ ->
                        memory.clear()
                        dialogueState.clear()
                        toast("Локальная память и текущий контекст диалога очищены.")
                    }
                    .setNegativeButton("Отмена", null)
                    .show()
            }
            .setNegativeButton("Закрыть", null)
            .show()
    }

    private fun showSelfDevelopment() {
        val engine = AndroidSelfDevelopment(this)
        val info = "Собрано идей: ${engine.ideas().size}\nПредложений: ${engine.proposals().size}\n\nСейчас доступны локальный сбор идей и анализ. Изменение исходников, сборка обновлений и установка требуют отдельного проверяемого конвейера и подтверждения владельца."
        val input = EditText(this).apply { hint = "Идея или задача для развития Маны"; minLines = 3 }
        AlertDialog.Builder(this).setTitle("Саморазвитие MANA").setMessage(info).setView(input)
            .setNeutralButton("Анализировать") { _, _ ->
                if (engine.isSelfDevelopmentEnabled()) {
                    val count = engine.analyze().size
                    toast("Анализ завершён. Предложений: $count")
                } else toast("Саморазвитие выключено.")
            }
            .setPositiveButton("Сохранить идею") { _, _ ->
                runCatching { engine.collect(ManaGenesis.IdeaSource.OWNER, input.text.toString()) }
                    .onSuccess { toast("Идея сохранена локально.") }
                    .onFailure { toast(it.message ?: "Не удалось сохранить.") }
            }.setNegativeButton("Закрыть", null).show()
    }

    private fun section(text: String) {
        content.addView(label(text, 11f, accent, true), marginTop(20))
    }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(15), dp(15), dp(15), dp(15))
        background = rounded(panel, 18)
    }

    private fun label(text: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        this.text = text; textSize = size; setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
        setPadding(0, dp(3), 0, dp(3))
    }

    private fun actionButton(text: String, action: () -> Unit) = Button(this).apply {
        this.text = text; isAllCaps = false; textSize = 14f
        setTextColor(Color.WHITE); background = rounded(Color.rgb(19, 82, 137), 14)
        setOnClickListener { action() }
    }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
        setStroke(dp(1), Color.rgb(29, 82, 125))
    }

    private fun marginTop(value: Int) = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(value) }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun toast(value: String) = Toast.makeText(this, value, Toast.LENGTH_LONG).show()
}
