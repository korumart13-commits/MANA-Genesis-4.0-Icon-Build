package com.mana.os.integration

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persistent Android implementation for the first usable self-development stage.
 * Ideas are stored as inert text; nothing collected is ever executed as code.
 */
class AndroidSelfDevelopment(context: Context) {
    private val preferences: SharedPreferences =
        context.getSharedPreferences("mana_self_development", Context.MODE_PRIVATE)

    private val sources = ManaGenesis.IdeaSource.values().toList()

    @Synchronized
    fun collect(source: ManaGenesis.IdeaSource, summary: String, evidence: String = "") {
        check(isSelfDevelopmentEnabled()) { "Саморазвитие отключено владельцем." }
        require(summary.isNotBlank()) { "Идея не должна быть пустой." }
        require(summary.length <= 20_000 && evidence.length <= 50_000) { "Слишком большой объём текста." }
        val idea = ManaGenesis.IdeaRecord(
            id = "idea-" + java.util.UUID.randomUUID().toString(),
            source = source,
            summary = summary.trim(),
            evidence = evidence.trim()
        )
        val all = readIdeas().toMutableList()
        all.add(idea)
        writeIdeas(all)
    }

    @Synchronized
    fun analyze(): List<ManaGenesis.DevelopmentProposal> {
        if (!isSelfDevelopmentEnabled()) return readProposals()
        val ideas = readIdeas()
        val known = readProposals().associateBy { it.id }.toMutableMap()
        ideas.forEach { idea ->
            val proposalId = "proposal-" + idea.id
            if (proposalId !in known) {
                val proposal = analyzeOne(idea)
                known[proposal.id] = proposal
            }
        }
        writeProposals(known.values.toList())
        return known.values.sortedByDescending { it.id }
    }

    @Synchronized
    fun ideas(): List<ManaGenesis.IdeaRecord> = readIdeas()

    @Synchronized
    fun proposals(): List<ManaGenesis.DevelopmentProposal> = readProposals()

    @Synchronized
    fun setSelfDevelopmentEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun isSelfDevelopmentEnabled(): Boolean = preferences.getBoolean(KEY_ENABLED, true)

    private fun analyzeOne(idea: ManaGenesis.IdeaRecord): ManaGenesis.DevelopmentProposal {
        val text = (idea.summary + " " + idea.evidence).lowercase()
        val affected = linkedSetOf<String>()
        val mapping = listOf(
            "voice" to listOf("голос", "voice", "микрофон", "озвуч"),
            "avatar" to listOf("аватар", "персонаж", "3d", "анимац"),
            "launcher" to listOf("рабочий стол", "launcher", "икон", "виджет"),
            "learning" to listOf("интернет", "обуч", "источник", "знани"),
            "memory" to listOf("памят", "memory", "дневник"),
            "receptionist" to listOf("звон", "вызов", "телефон", "контакт"),
            "game_intelligence" to listOf("игр", "minecraft", "майнкрафт", "сервер"),
            "updates" to listOf("обновлен", "update", "верси"),
            "mana_shield" to listOf("вирус", "malware", "уязвим", "безопас", "вредонос"),
            "system_ui" to listOf("системн", "панель", "уведомлен", "android")
        )
        mapping.forEach { (module, words) ->
            if (words.any(text::contains)) affected += module
        }
        if (affected.isEmpty()) affected += "self_development"

        val security = idea.source == ManaGenesis.IdeaSource.SECURITY_ALERT ||
            listOf("вирус", "malware", "уязвим", "эксплойт", "взлом").any(text::contains)
        val systemWide = listOf("прошив", "firmware", "bootloader", "ядро android", "полная замена").any(text::contains)
        val bug = listOf("ошиб", "баг", "bug", "crash", "вылет", "сбой").any(text::contains)

        val target = when {
            systemWide -> setOf(ManaGenesis.ImprovementTarget.MANA_OS_LAYER, ManaGenesis.ImprovementTarget.MANA_SOURCE_CODE)
            security -> setOf(ManaGenesis.ImprovementTarget.SECURITY)
            bug -> setOf(ManaGenesis.ImprovementTarget.MODULE, ManaGenesis.ImprovementTarget.MANA_SOURCE_CODE)
            else -> setOf(ManaGenesis.ImprovementTarget.MODULE)
        }
        val scope = when {
            systemWide -> ManaGenesis.ChangeScope.FULL_SYSTEM_UPDATE
            bug -> ManaGenesis.ChangeScope.MINOR_PATCH
            else -> ManaGenesis.ChangeScope.FEATURE_ADDITION
        }
        val risk = when {
            security -> ManaGenesis.ChangeRisk.SECURITY_CRITICAL
            systemWide -> ManaGenesis.ChangeRisk.SYSTEM_WIDE
            bug -> ManaGenesis.ChangeRisk.LOW
            else -> ManaGenesis.ChangeRisk.ELEVATED
        }
        val approval = risk != ManaGenesis.ChangeRisk.LOW || scope == ManaGenesis.ChangeScope.FULL_SYSTEM_UPDATE
        return ManaGenesis.DevelopmentProposal(
            id = "proposal-" + idea.id,
            title = if (bug) "Проверить и исправить: ${idea.summary.take(90)}" else "Изучить предложение: ${idea.summary.take(90)}",
            rationale = "Автоматический первичный анализ идеи из источника ${idea.source}. Это гипотеза, а не подтверждённый дефект.",
            affectedModules = affected,
            targets = target,
            scope = scope,
            risk = risk,
            acceptanceCriteria = listOf(
                "Воспроизвести проблему или подтвердить полезность предложения.",
                "Добавить автоматические тесты для затронутого поведения.",
                "Проверить отсутствие регрессий и подготовить откат."
            ),
            basedOnIdeaIds = setOf(idea.id),
            requiresUserApproval = approval,
            status = if (approval) ManaGenesis.ProposalStatus.APPROVAL_REQUIRED
                else ManaGenesis.ProposalStatus.READY_FOR_TESTS
        )
    }

    private fun readIdeas(): List<ManaGenesis.IdeaRecord> {
        val array = runCatching { JSONArray(preferences.getString(KEY_IDEAS, "[]")) }.getOrDefault(JSONArray())
        return (0 until array.length()).mapNotNull { index ->
            runCatching {
                val item = array.getJSONObject(index)
                ManaGenesis.IdeaRecord(
                    id = item.getString("id"),
                    source = runCatching { ManaGenesis.IdeaSource.valueOf(item.getString("source")) }.getOrDefault(ManaGenesis.IdeaSource.OTHER),
                    summary = item.getString("summary"),
                    evidence = item.optString("evidence"),
                    sourceReference = item.optString("sourceReference").ifBlank { null }
                )
            }.getOrNull()
        }
    }

    private fun writeIdeas(ideas: List<ManaGenesis.IdeaRecord>) {
        val array = JSONArray()
        ideas.takeLast(500).forEach { idea ->
            array.put(JSONObject()
                .put("id", idea.id)
                .put("source", idea.source.name)
                .put("summary", idea.summary)
                .put("evidence", idea.evidence)
                .put("sourceReference", idea.sourceReference ?: ""))
        }
        preferences.edit().putString(KEY_IDEAS, array.toString()).apply()
    }

    private fun readProposals(): List<ManaGenesis.DevelopmentProposal> {
        val array = runCatching { JSONArray(preferences.getString(KEY_PROPOSALS, "[]")) }.getOrDefault(JSONArray())
        return (0 until array.length()).mapNotNull { index ->
            runCatching {
                val item = array.getJSONObject(index)
                fun strings(key: String): Set<String> {
                    val values = item.optJSONArray(key) ?: JSONArray()
                    return (0 until values.length()).map { values.getString(it) }.toSet()
                }
                fun list(key: String): List<String> {
                    val values = item.optJSONArray(key) ?: JSONArray()
                    return (0 until values.length()).map { values.getString(it) }
                }
                ManaGenesis.DevelopmentProposal(
                    id = item.getString("id"),
                    title = item.getString("title"),
                    rationale = item.getString("rationale"),
                    affectedModules = strings("affectedModules"),
                    targets = strings("targets").mapNotNull { runCatching { ManaGenesis.ImprovementTarget.valueOf(it) }.getOrNull() }.toSet(),
                    scope = ManaGenesis.ChangeScope.valueOf(item.getString("scope")),
                    risk = ManaGenesis.ChangeRisk.valueOf(item.getString("risk")),
                    acceptanceCriteria = list("acceptanceCriteria"),
                    basedOnIdeaIds = strings("basedOnIdeaIds"),
                    requiresUserApproval = item.getBoolean("requiresUserApproval"),
                    status = ManaGenesis.ProposalStatus.valueOf(item.getString("status"))
                )
            }.getOrNull()
        }
    }

    private fun writeProposals(proposals: List<ManaGenesis.DevelopmentProposal>) {
        val array = JSONArray()
        proposals.takeLast(500).forEach { proposal ->
            fun json(values: Iterable<String>) = JSONArray().also { target -> values.forEach(target::put) }
            array.put(JSONObject()
                .put("id", proposal.id)
                .put("title", proposal.title)
                .put("rationale", proposal.rationale)
                .put("affectedModules", json(proposal.affectedModules))
                .put("targets", json(proposal.targets.map { it.name }))
                .put("scope", proposal.scope.name)
                .put("risk", proposal.risk.name)
                .put("acceptanceCriteria", json(proposal.acceptanceCriteria))
                .put("basedOnIdeaIds", json(proposal.basedOnIdeaIds))
                .put("requiresUserApproval", proposal.requiresUserApproval)
                .put("status", proposal.status.name))
        }
        preferences.edit().putString(KEY_PROPOSALS, array.toString()).apply()
    }

    companion object {
        private const val KEY_IDEAS = "ideas_json"
        private const val KEY_PROPOSALS = "proposals_json"
        private const val KEY_ENABLED = "self_development_enabled"
    }
}
