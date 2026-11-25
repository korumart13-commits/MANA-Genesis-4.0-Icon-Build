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
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.widget.*
import com.mana.os.cloud.ManaCloudClient
import com.mana.os.integration.AndroidSelfDevelopment
import com.mana.os.integration.ManaGenesis
import java.util.Locale

/** First MANA home surface. Cloud status is truthful; no server URL is requested from the owner. */
class ManaHomeActivity : Activity(), TextToSpeech.OnInitListener {
    private var textToSpeech: TextToSpeech? = null
    private var ttsReady = false
    private val prefs by lazy { getSharedPreferences("mana_setup", MODE_PRIVATE) }
    private lateinit var content: LinearLayout
    private lateinit var status: TextView
    private lateinit var chatLog: TextView
    private lateinit var message: EditText
    private var cloudReady = false

    private val bg = Color.rgb(5, 15, 34)
    private val panel = Color.rgb(12, 32, 62)
    private val accent = Color.rgb(64, 190, 255)
    private val primaryText = Color.rgb(235, 245, 255)
    private val muted = Color.rgb(143, 174, 207)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        textToSpeech = TextToSpeech(this, this)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        renderHome()
        checkCloud()
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

        val avatarPanel = card()
        avatarPanel.orientation = LinearLayout.HORIZONTAL
        avatarPanel.gravity = Gravity.CENTER_VERTICAL
        val avatar = TextView(this).apply {
            text = "M"; textSize = 36f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(19, 90, 153), Color.rgb(17, 35, 77))).apply { shape = GradientDrawable.OVAL }
        }
        avatarPanel.addView(avatar, LinearLayout.LayoutParams(dp(76), dp(76)))
        val intro = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), 0, 0, 0) }
        intro.addView(label("Привет. Я Мана.", 20f, primaryText, true))
        intro.addView(label("Твой единый ИИ-помощник и пространство телефона.", 13f, muted))
        intro.addView(label("3D-аватарный движок подключается отдельным этапом — это пока не живая 3D-модель.", 11f, muted))
        avatarPanel.addView(intro, LinearLayout.LayoutParams(0, -2, 1f))
        content.addView(avatarPanel, marginTop(20))

        status = label("Облако: проверка подключения…", 12f, accent)
        content.addView(status, marginTop(14))

        section("ПОГОВОРИТЬ С МАНОЙ")
        val chatCard = card()
        chatLog = label("Напиши сообщение. Если облачный сервер ещё не настроен, я честно сообщу об этом и сохраню локальный режим.", 14f, primaryText)
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
        chatCard.addView(actions, marginTop(8))
        content.addView(chatCard, marginTop(8))

        section("ТВОЁ ПРОСТРАНСТВО")
        val appButton = actionButton("Приложения телефона") { showInstalledApps() }
        content.addView(appButton, marginTop(6))
        val selfButton = actionButton("Саморазвитие MANA") { showSelfDevelopment() }
        content.addView(selfButton, marginTop(8))
        val diaryButton = actionButton("Дневник MANA") {
            startActivity(Intent(this, ManaDiaryActivity::class.java))
        }
        content.addView(diaryButton, marginTop(8))
        val memoryButton = actionButton("Память Маны") { showMemory() }
        content.addView(memoryButton, marginTop(8))
        val settingsButton = actionButton("Настройки и разрешения Android") {
            startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                android.net.Uri.parse("package:$packageName")))
        }
        content.addView(settingsButton, marginTop(8))
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

    private fun checkCloud() {
        val url = prefs.getString("cloud_url", "").orEmpty()
        if (url.isBlank()) {
            status.text = "Облако: не подключено. Адрес сервера не настроен; работаем в локальном режиме."
            cloudReady = false
            return
        }
        ManaCloudClient(url, prefs.getString("cloud_token", null)).checkHealth { ok, detail ->
            runOnUiThread {
                cloudReady = ok
                status.text = if (ok) "Облако: подключено и отвечает." else "Облако: недоступно ($detail). Локальный режим."
            }
        }
    }

    private fun sendMessage() {
        val text = message.text.toString().trim()
        if (text.isEmpty()) return
        val offlineReply = "Сейчас я работаю локально. Облачный чат не подключён, поэтому сообщение не отправлено."
        chatLog.text = "Ты: $text\n\n" + if (!cloudReady) "Мана: $offlineReply" else "Мана: отправляю сообщение…"
        if (!cloudReady) speak(offlineReply)
        message.setText("")
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(message.windowToken, 0)
        val url = prefs.getString("cloud_url", "").orEmpty()
        if (cloudReady && url.isNotBlank()) {
            ManaCloudClient(url, prefs.getString("cloud_token", null)).sendMessage(text) { answer, error ->
                runOnUiThread {
                    chatLog.text = "Ты: $text\n\n" + (answer?.let { "Мана: $it" } ?: "Мана: ошибка облака — $error")
                    answer?.let { speak(it) }
                }
            }
        }
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
        if (requestCode == 703 && resultCode == RESULT_OK) {
            val spoken = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) { message.setText(spoken); sendMessage() }
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
        }
    }

    private fun speak(text: String) {
        if (ttsReady && text.isNotBlank()) {
            textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "mana_reply")
        }
    }

    override fun onDestroy() {
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        super.onDestroy()
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
                        toast("Локальная память очищена.")
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
