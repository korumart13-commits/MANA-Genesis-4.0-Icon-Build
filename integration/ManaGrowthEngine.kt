package com.mana.os.integration

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * On-device growth ledger. It learns reusable preferences/skills from explicit owner feedback,
 * while ordinary conversations are stored only as bounded experience summaries.
 * It never changes model weights, executes generated code, or treats a model guess as a fact.
 */
class ManaGrowthEngine(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("mana_growth_engine", Context.MODE_PRIVATE)

    data class Skill(val id: String, val title: String, val instruction: String, val confidence: Int, val updatedAt: Long)
    data class Experience(val topic: String, val outcome: String, val timestamp: Long)

    @Synchronized
    fun rememberExperience(topic: String, outcome: String) {
        if (topic.isBlank() || outcome.isBlank()) return
        val all = readExperiences().toMutableList()
        all += Experience(topic.take(180), outcome.take(500), System.currentTimeMillis())
        val json = JSONArray()
        all.takeLast(MAX_EXPERIENCES).forEach {
            json.put(JSONObject().put("topic", it.topic).put("outcome", it.outcome).put("timestamp", it.timestamp))
        }
        prefs.edit().putString(KEY_EXPERIENCES, json.toString()).apply()
    }

    /** A skill enters the reusable library only after an explicit owner confirmation. */
    @Synchronized
    fun addOwnerApprovedSkill(title: String, instruction: String): Skill {
        require(title.isNotBlank() && title.length <= 100)
        require(instruction.isNotBlank() && instruction.length <= 700)
        val safeTitle = title.trim()
        val safeInstruction = instruction.trim()
        val skills = readSkills().toMutableList()
        val existing = skills.indexOfFirst { it.title.equals(safeTitle, ignoreCase = true) }
        val skill = Skill(
            id = if (existing >= 0) skills[existing].id else "skill-" + java.util.UUID.randomUUID(),
            title = safeTitle,
            instruction = safeInstruction,
            confidence = 100,
            updatedAt = System.currentTimeMillis()
        )
        if (existing >= 0) skills[existing] = skill else skills += skill
        saveSkills(skills.takeLast(MAX_SKILLS))
        return skill
    }

    @Synchronized
    fun recallSkills(query: String, limit: Int = 5): List<Skill> {
        val terms = tokens(query)
        return readSkills().map { it to terms.count { term ->
            it.title.lowercase(Locale.ROOT).contains(term) ||
                it.instruction.lowercase(Locale.ROOT).contains(term)
        } }.filter { it.second > 0 }.sortedByDescending { it.second }
            .take(limit.coerceIn(0, MAX_SKILLS)).map { it.first }
    }

    @Synchronized
    fun recentExperiences(limit: Int = 10): List<Experience> =
        readExperiences().takeLast(limit.coerceIn(0, MAX_EXPERIENCES)).asReversed()

    @Synchronized
    fun removeSkill(id: String) {
        saveSkills(readSkills().filterNot { it.id == id })
    }

    private fun readExperiences(): List<Experience> {
        val array = runCatching { JSONArray(prefs.getString(KEY_EXPERIENCES, "[]")) }.getOrDefault(JSONArray())
        return (0 until array.length()).mapNotNull { index -> runCatching {
            val item = array.getJSONObject(index)
            Experience(item.optString("topic"), item.optString("outcome"), item.optLong("timestamp"))
        }.getOrNull() }
    }

    private fun readSkills(): List<Skill> {
        val array = runCatching { JSONArray(prefs.getString(KEY_SKILLS, "[]")) }.getOrDefault(JSONArray())
        return (0 until array.length()).mapNotNull { index -> runCatching {
            val item = array.getJSONObject(index)
            Skill(item.getString("id"), item.getString("title"), item.getString("instruction"),
                item.optInt("confidence", 100), item.optLong("updatedAt"))
        }.getOrNull() }
    }

    private fun saveSkills(skills: List<Skill>) {
        val array = JSONArray()
        skills.takeLast(MAX_SKILLS).forEach { skill ->
            array.put(JSONObject().put("id", skill.id).put("title", skill.title)
                .put("instruction", skill.instruction).put("confidence", skill.confidence)
                .put("updatedAt", skill.updatedAt))
        }
        prefs.edit().putString(KEY_SKILLS, array.toString()).apply()
    }

    private fun tokens(value: String): Set<String> =
        Regex("[\\p{L}\\p{N}]{3,}").findAll(value.lowercase(Locale.ROOT)).map { it.value }.toSet()

    companion object {
        private const val KEY_SKILLS = "approved_skills"
        private const val KEY_EXPERIENCES = "experience_log"
        private const val MAX_SKILLS = 100
        private const val MAX_EXPERIENCES = 300
    }
}
