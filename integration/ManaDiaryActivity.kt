package com.mana.os.app

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Offline-first personal diary. Entries remain on this device in private app storage. */
class ManaDiaryActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("mana_diary", MODE_PRIVATE) }
    private lateinit var list: LinearLayout
    private val bg = Color.rgb(5, 15, 34)
    private val panel = Color.rgb(12, 32, 62)
    private val accent = Color.rgb(64, 190, 255)
    private val mainText = Color.rgb(235, 245, 255)
    private val muted = Color.rgb(143, 174, 207)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(22), dp(18), dp(18))
            setBackgroundColor(bg)
        }
        root.addView(TextView(this).apply {
            text = "ДНЕВНИК MANA"
            textSize = 23f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(accent)
        })
        root.addView(TextView(this).apply {
            text = "Личные записи хранятся локально на этом устройстве."
            textSize = 12f
            setTextColor(muted)
        }, marginTop(5))
        root.addView(Button(this).apply {
            text = "Новая запись"
            isAllCaps = false
            setOnClickListener { showEditor() }
        }, marginTop(14))
        val scroll = ScrollView(this)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(12) })
        root.addView(Button(this).apply {
            text = "Назад к Мане"
            isAllCaps = false
            setOnClickListener { finish() }
        }, marginTop(8))
        setContentView(root)
        renderEntries()
    }

    private fun showEditor() {
        val title = EditText(this).apply { hint = "Заголовок"; setTextColor(mainText); setHintTextColor(muted) }
        val body = EditText(this).apply {
            hint = "Что хочешь записать?"
            minLines = 5
            gravity = Gravity.TOP
            setTextColor(mainText)
            setHintTextColor(muted)
        }
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(4), dp(12), dp(4))
            addView(title)
            addView(body, marginTop(8))
        }
        AlertDialog.Builder(this)
            .setTitle("Запись в дневник")
            .setView(form)
            .setPositiveButton("Сохранить") { _, _ ->
                val text = body.text.toString().trim()
                if (text.isNotEmpty()) {
                    val entries = readEntries()
                    val entry = JSONObject()
                        .put("id", System.currentTimeMillis().toString())
                        .put("title", title.text.toString().trim().ifBlank { "Без заголовка" })
                        .put("body", text)
                        .put("created", System.currentTimeMillis())
                    entries.put(entry)
                    prefs.edit().putString("entries", entries.toString()).apply()
                    renderEntries()
                } else Toast.makeText(this, "Пустую запись сохранить нельзя.", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun renderEntries() {
        list.removeAllViews()
        val entries = readEntries()
        if (entries.length() == 0) {
            list.addView(TextView(this).apply {
                text = "Пока записей нет. Нажми «Новая запись», чтобы начать."
                textSize = 14f
                setTextColor(muted)
                setPadding(0, dp(14), 0, dp(14))
            })
            return
        }
        for (index in entries.length() - 1 downTo 0) {
            val item = entries.optJSONObject(index) ?: continue
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(12), dp(14), dp(12))
                background = GradientDrawable().apply {
                    setColor(panel)
                    cornerRadius = dp(16).toFloat()
                    setStroke(dp(1), Color.rgb(29, 82, 125))
                }
            }
            card.addView(TextView(this).apply {
                text = item.optString("title", "Запись")
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(mainText)
            })
            val date = SimpleDateFormat("dd.MM.yyyy  HH:mm", Locale.getDefault())
                .format(Date(item.optLong("created")))
            card.addView(TextView(this).apply {
                text = date
                textSize = 11f
                setTextColor(accent)
            }, marginTop(3))
            card.addView(TextView(this).apply {
                text = item.optString("body")
                textSize = 14f
                setTextColor(mainText)
            }, marginTop(7))
            card.addView(Button(this).apply {
                text = "Удалить запись"
                isAllCaps = false
                setOnClickListener { confirmDelete(item.optString("id")) }
            }, marginTop(6))
            list.addView(card, marginTop(8))
        }
    }

    private fun confirmDelete(id: String) {
        AlertDialog.Builder(this)
            .setMessage("Удалить эту запись без возможности восстановления?")
            .setPositiveButton("Удалить") { _, _ ->
                val old = readEntries()
                val updated = JSONArray()
                for (i in 0 until old.length()) {
                    val item = old.optJSONObject(i) ?: continue
                    if (item.optString("id") != id) updated.put(item)
                }
                prefs.edit().putString("entries", updated.toString()).apply()
                renderEntries()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun readEntries(): JSONArray = runCatching {
        JSONArray(prefs.getString("entries", "[]") ?: "[]")
    }.getOrElse { JSONArray() }

    private fun marginTop(value: Int) =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(value) }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
