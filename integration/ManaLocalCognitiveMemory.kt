package com.mana.os.integration

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * Private-app-storage memory adapter for the cognitive runtime.
 * This is local persistence, not a cloud sync service or a claim of app-level encryption.
 */
class ManaLocalCognitiveMemory(context: Context) : ManaCognitiveMemory {
    private val prefs = context.getSharedPreferences("mana_cognitive_memory", Context.MODE_PRIVATE)

    @Synchronized
    override fun recall(query: String, limit: Int): List<ManaCognitiveMemoryEntry> {
        if (limit <= 0) return emptyList()
        val terms = tokens(query)
        return readAll()
            .map { entry -> entry to terms.count { term -> entry.text.lowercase(Locale.ROOT).contains(term) } }
            .filter { (_, score) -> score > 0 }
            .sortedWith(compareByDescending<Pair<ManaCognitiveMemoryEntry, Int>> { it.second }
                .thenByDescending { it.first.timestampMillis })
            .take(limit.coerceAtMost(20))
            .map { it.first }
    }

    @Synchronized
    override fun remember(entry: ManaCognitiveMemoryEntry) {
        require(entry.text.isNotBlank())
        if (looksSensitive(entry.text)) return
        val entries = readAll().toMutableList()
        entries += entry.copy(text = entry.text.take(1500))
        val array = JSONArray()
        entries.takeLast(MAX_ENTRIES).forEach { item ->
            array.put(JSONObject()
                .put("conversationId", item.conversationId)
                .put("kind", item.kind)
                .put("text", item.text)
                .put("timestampMillis", item.timestampMillis))
        }
        prefs.edit().putString(KEY_ENTRIES, array.toString()).apply()
    }

    @Synchronized
    fun clear() {
        prefs.edit().remove(KEY_ENTRIES).apply()
    }

    @Synchronized
    fun count(): Int = readAll().size

    private fun readAll(): List<ManaCognitiveMemoryEntry> {
        val array = runCatching { JSONArray(prefs.getString(KEY_ENTRIES, "[]") ?: "[]") }
            .getOrElse { JSONArray() }
        return (0 until array.length()).mapNotNull { index ->
            runCatching {
                val item = array.getJSONObject(index)
                ManaCognitiveMemoryEntry(
                    conversationId = item.optString("conversationId"),
                    kind = item.optString("kind"),
                    text = item.optString("text"),
                    timestampMillis = item.optLong("timestampMillis")
                )
            }.getOrNull()
        }
    }

    private fun tokens(value: String): Set<String> =
        Regex("[\\p{L}\\p{N}]{3,}").findAll(value.lowercase(Locale.ROOT))
            .map { it.value }.toSet()

    private fun looksSensitive(value: String): Boolean {
        val text = value.lowercase(Locale.ROOT)
        val markers = listOf(
            "password", "passcode", "пароль", "пин-код", "pin code",
            "одноразовый код", "one-time code", "otp", "api_key", "api key",
            "secret key", "секретный ключ", "private key", "приватный ключ"
        )
        return markers.any(text::contains)
    }

    companion object {
        private const val KEY_ENTRIES = "entries_json"
        private const val MAX_ENTRIES = 300
    }
}
