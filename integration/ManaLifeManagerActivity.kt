package com.mana.os.app

import android.app.Activity
import android.app.DatePickerDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Offline-first MANA Life Manager.
 * Tasks are stored only in this app's private SharedPreferences; no account or cloud is required.
 */
class ManaLifeManagerActivity : Activity() {
    private data class Task(val id: Long, val title: String, val dueAt: Long?, val done: Boolean)

    private val bg = Color.rgb(5, 15, 34)
    private val panel = Color.rgb(13, 34, 66)
    private val accent = Color.rgb(75, 190, 245)
    private val primary = Color.rgb(235, 245, 255)
    private val muted = Color.rgb(150, 175, 205)
    private val tasks = mutableListOf<Task>()
    private lateinit var list: LinearLayout
    private lateinit var input: EditText
    private var selectedDueAt: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadTasks()
        renderScreen()
        renderTasks()
    }

    private fun renderScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(22), dp(18), dp(24))
            setBackgroundColor(bg)
        }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(this).apply {
            text = "MANA"; textSize = 26f; typeface = Typeface.DEFAULT_BOLD; setTextColor(accent)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(TextView(this).apply {
            text = "LIFE MANAGER"; textSize = 10f; setTextColor(muted)
        })
        root.addView(header)
        root.addView(TextView(this).apply {
            text = "Твои задачи остаются на устройстве. Облачная синхронизация не используется."
            textSize = 12f; setTextColor(muted)
        }, marginTop(8))

        input = EditText(this).apply {
            hint = "Новая задача…"; setHintTextColor(muted); setTextColor(primary)
            textSize = 15f; setPadding(dp(12), dp(10), dp(12), dp(10))
            background = rounded(panel)
        }
        root.addView(input, marginTop(18))

        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        controls.addView(action("Срок") { chooseDate() }, LinearLayout.LayoutParams(0, dp(46), 1f))
        controls.addView(action("Добавить") { addTask() }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { leftMargin = dp(8) })
        root.addView(controls, marginTop(8))
        root.addView(TextView(this).apply {
            text = "Нажми на задачу, чтобы отметить её выполненной. Удаление доступно отдельной кнопкой."
            textSize = 11f; setTextColor(muted)
        }, marginTop(10))

        val scroll = ScrollView(this)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(12) })
        setContentView(root)
    }

    private fun chooseDate() {
        val now = Calendar.getInstance()
        DatePickerDialog(this, { _, year, month, day ->
            val due = Calendar.getInstance().apply {
                set(year, month, day, 9, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }
            selectedDueAt = due.timeInMillis
            toast("Срок: ${SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(due.timeInMillis))}")
        }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun addTask() {
        val title = input.text.toString().trim().take(180)
        if (title.isBlank()) { toast("Напиши название задачи."); return }
        tasks.add(Task(System.currentTimeMillis(), title, selectedDueAt, false))
        selectedDueAt = null
        input.setText("")
        saveTasks()
        renderTasks()
    }

    private fun renderTasks() {
        list.removeAllViews()
        if (tasks.isEmpty()) {
            list.addView(TextView(this).apply {
                text = "Пока нет задач. Добавь первую — Мана сохранит её локально."
                textSize = 14f; setTextColor(muted); setPadding(0, dp(18), 0, dp(18))
            })
            return
        }
        tasks.sortedWith(compareBy<Task> { it.done }.thenBy { it.dueAt ?: Long.MAX_VALUE }.thenByDescending { it.id })
            .forEach { task ->
                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(13), dp(11), dp(13), dp(11))
                    background = rounded(panel)
                }
                val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
                val title = TextView(this).apply {
                    text = if (task.done) "✓ ${task.title}" else task.title
                    textSize = 15f; setTextColor(if (task.done) muted else primary)
                }
                row.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
                row.addView(action(if (task.done) "Вернуть" else "Готово") {
                    val index = tasks.indexOfFirst { it.id == task.id }
                    if (index >= 0) tasks[index] = tasks[index].copy(done = !tasks[index].done)
                    saveTasks(); renderTasks()
                })
                card.addView(row)
                task.dueAt?.let { due ->
                    card.addView(TextView(this).apply {
                        text = "Срок: ${SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(due))}"
                        textSize = 11f; setTextColor(muted)
                    }, marginTop(5))
                }
                card.addView(action("Удалить") {
                    tasks.removeAll { it.id == task.id }
                    saveTasks(); renderTasks()
                }, marginTop(7))
                list.addView(card, marginTop(7))
            }
    }

    private fun loadTasks() {
        val raw = getSharedPreferences("mana_life_manager", MODE_PRIVATE).getString("tasks", "[]") ?: "[]"
        runCatching {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                tasks.add(Task(item.getLong("id"), item.getString("title"),
                    if (item.isNull("dueAt")) null else item.getLong("dueAt"), item.optBoolean("done")))
            }
        }.onFailure { tasks.clear() }
    }

    private fun saveTasks() {
        val array = JSONArray()
        tasks.takeLast(250).forEach { task ->
            array.put(JSONObject().apply {
                put("id", task.id); put("title", task.title)
                put("dueAt", task.dueAt ?: JSONObject.NULL); put("done", task.done)
            })
        }
        getSharedPreferences("mana_life_manager", MODE_PRIVATE).edit().putString("tasks", array.toString()).apply()
    }

    private fun action(text: String, onClick: () -> Unit): Button = Button(this).apply {
        this.text = text; textSize = 11f; isAllCaps = false; setTextColor(primary)
        background = rounded(Color.rgb(22, 75, 125))
        setOnClickListener { onClick() }
    }

    private fun rounded(color: Int) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(13).toFloat()
    }

    private fun marginTop(value: Int) = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(value) }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun toast(message: String) = android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
}
