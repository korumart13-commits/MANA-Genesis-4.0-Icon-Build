package com.mana.os.integration

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Auditable, local state machine for MANA's development proposals.
 * It tracks review gates only; it never executes generated code or installs updates.
 */
class ManaDevelopmentPipeline(context: Context) {
    enum class Stage {
        IDEA, ANALYZED, APPROVAL_REQUIRED, PATCH_DRAFTED, PATCH_VALIDATED,
        TESTS_PASSED, READY_FOR_OWNER_REVIEW, REJECTED, ARCHIVED
    }

    data class Record(
        val id: String,
        val title: String,
        val stage: Stage,
        val risk: String,
        val patchReference: String?,
        val report: String,
        val updatedAt: Long
    )

    private val prefs = context.getSharedPreferences("mana_development_pipeline", Context.MODE_PRIVATE)

    @Synchronized
    fun create(title: String, risk: String): Record {
        require(title.isNotBlank() && title.length <= 240)
        require(risk in setOf("LOW", "MEDIUM", "HIGH", "SECURITY_CRITICAL", "SYSTEM_WIDE"))
        val record = Record(
            id = "mana-dev-" + UUID.randomUUID().toString(),
            title = title.trim(),
            stage = Stage.IDEA,
            risk = risk,
            patchReference = null,
            report = "Предложение зарегистрировано. Исходный код не менялся.",
            updatedAt = System.currentTimeMillis()
        )
        save(records() + record)
        return record
    }

    @Synchronized
    fun records(): List<Record> {
        val raw = prefs.getString(KEY_RECORDS, "[]") ?: "[]"
        val array = runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
        return (0 until array.length()).mapNotNull { index ->
            runCatching {
                val item = array.getJSONObject(index)
                Record(
                    id = item.getString("id"),
                    title = item.getString("title"),
                    stage = Stage.valueOf(item.getString("stage")),
                    risk = item.getString("risk"),
                    patchReference = item.optString("patchReference").takeIf { it.isNotBlank() },
                    report = item.optString("report"),
                    updatedAt = item.optLong("updatedAt")
                )
            }.getOrNull()
        }
    }

    /**
     * ownerApproved is an explicit caller-supplied gate, not inferred from app state.
     * Moving to PATCH_DRAFTED from APPROVAL_REQUIRED requires ownerApproved=true.
     */
    @Synchronized
    fun advance(
        id: String,
        next: Stage,
        report: String,
        patchReference: String? = null,
        ownerApproved: Boolean = false
    ): Record {
        require(report.length <= 8_000)
        val all = records().toMutableList()
        val index = all.indexOfFirst { it.id == id }
        require(index >= 0) { "Предложение не найдено." }
        val current = all[index]
        require(isAllowed(current.stage, next)) {
            "Недопустимый переход: ${current.stage} -> $next"
        }
        if (current.stage == Stage.APPROVAL_REQUIRED && next == Stage.PATCH_DRAFTED) {
            require(ownerApproved) { "Для подготовки патча требуется подтверждение владельца." }
        }
        if (next == Stage.PATCH_DRAFTED) {
            require(!patchReference.isNullOrBlank()) { "Нужна ссылка на сохранённый diff." }
        }
        if (next == Stage.PATCH_VALIDATED) {
            require(!patchReference.isNullOrBlank()) { "Нужна ссылка на проверенный diff." }
        }
        val updated = current.copy(
            stage = next,
            patchReference = patchReference ?: current.patchReference,
            report = report.trim(),
            updatedAt = System.currentTimeMillis()
        )
        all[index] = updated
        save(all)
        appendAudit("${updated.id}: ${current.stage} -> ${next}")
        return updated
    }

    private fun isAllowed(from: Stage, to: Stage): Boolean = when (from) {
        Stage.IDEA -> to == Stage.ANALYZED || to == Stage.REJECTED
        Stage.ANALYZED -> to == Stage.APPROVAL_REQUIRED || to == Stage.PATCH_DRAFTED || to == Stage.REJECTED
        Stage.APPROVAL_REQUIRED -> to == Stage.PATCH_DRAFTED || to == Stage.REJECTED
        Stage.PATCH_DRAFTED -> to == Stage.PATCH_VALIDATED || to == Stage.REJECTED
        Stage.PATCH_VALIDATED -> to == Stage.TESTS_PASSED || to == Stage.REJECTED
        Stage.TESTS_PASSED -> to == Stage.READY_FOR_OWNER_REVIEW || to == Stage.REJECTED
        Stage.READY_FOR_OWNER_REVIEW -> to == Stage.ARCHIVED || to == Stage.REJECTED
        Stage.REJECTED -> to == Stage.ARCHIVED
        Stage.ARCHIVED -> false
    }

    private fun save(records: List<Record>) {
        val array = JSONArray()
        records.takeLast(500).forEach { record ->
            array.put(JSONObject()
                .put("id", record.id)
                .put("title", record.title)
                .put("stage", record.stage.name)
                .put("risk", record.risk)
                .put("patchReference", record.patchReference ?: "")
                .put("report", record.report)
                .put("updatedAt", record.updatedAt))
        }
        prefs.edit().putString(KEY_RECORDS, array.toString()).apply()
    }

    private fun appendAudit(event: String) {
        val old = prefs.getString(KEY_AUDIT, "") ?: ""
        val next = (old.lines().filter { it.isNotBlank() } + "${System.currentTimeMillis()} $event").takeLast(1000)
        prefs.edit().putString(KEY_AUDIT, next.joinToString("\n")).apply()
    }

    companion object {
        private const val KEY_RECORDS = "development_records"
        private const val KEY_AUDIT = "development_audit"
    }
}
