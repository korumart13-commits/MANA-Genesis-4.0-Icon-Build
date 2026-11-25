#!/usr/bin/env python3
"""Integrate the reviewed MANA runtime into an extracted Android project copy."""
from __future__ import annotations

import argparse
import shutil
from pathlib import Path


def replace_once(source: str, old: str, new: str, label: str) -> str:
    count = source.count(old)
    if count == 1:
        return source.replace(old, new, 1)
    if count == 0 and new in source:
        return source
    raise SystemExit(f"Refusing unsafe Android source edit ({label}): expected one anchor, found {count}.")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--project-dir", required=True, type=Path)
    parser.add_argument("--integration-dir", required=True, type=Path)
    parser.add_argument("--license-file", required=True, type=Path)
    args = parser.parse_args()

    project = args.project_dir.resolve()
    integration = args.integration_dir.resolve()
    license_file = args.license_file.resolve()
    if not project.is_dir():
        raise SystemExit(f"Android project directory does not exist: {project}")
    if not (project / "settings.gradle").is_file() and not (project / "settings.gradle.kts").is_file():
        raise SystemExit("Android project has no settings.gradle or settings.gradle.kts.")
    if not integration.is_dir() or not license_file.is_file():
        raise SystemExit("MANA integration sources or license file are missing.")

    target_dir = project / "app/src/main/java/com/mana/os/integration"
    target_dir.mkdir(parents=True, exist_ok=True)
    for name in ("ManaGenesis.kt", "AndroidSelfDevelopment.kt", "ManaDevelopmentPipeline.kt"):
        source = integration / name
        if not source.is_file() or source.stat().st_size == 0:
            raise SystemExit(f"Required integration source is missing or empty: {source}")
        shutil.copy2(source, target_dir / name)

    assets = project / "app/src/main/assets"
    assets.mkdir(parents=True, exist_ok=True)
    shutil.copy2(license_file, assets / "manaos-license.txt")

    # Add a real Android/JUnit test source to the extracted build copy.
    test_source = integration / "tests" / "ManaCoreTest.kt"
    if not test_source.is_file() or test_source.stat().st_size == 0:
        raise SystemExit(f"Required Android unit test source is missing or empty: {test_source}")
    test_target = project / "core/src/test/java/com/mana/os/core/ManaCoreTest.kt"
    test_target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(test_source, test_target)

    core_gradle = project / "core/build.gradle.kts"
    if not core_gradle.is_file():
        raise SystemExit(f"Core Gradle build file is missing: {core_gradle}")
    gradle_source = core_gradle.read_text(encoding="utf-8")
    junit_dependency = 'testImplementation("junit:junit:4.13.2")'
    if junit_dependency not in gradle_source:
        gradle_source += (
            "\\n\\ndependencies {\\n"
            f"    {junit_dependency}\\n"
            "}\\n"
        )
        core_gradle.write_text(gradle_source, encoding="utf-8")

    activity = project / "app/src/main/java/com/mana/os/app/MainActivity.kt"
    if not activity.is_file():
        raise SystemExit(f"Expected MANA dashboard source is missing: {activity}")
    source = activity.read_text(encoding="utf-8")

    source = replace_once(
        source,
        "import com.mana.os.cloud.ManaCloudClient\n",
        "import com.mana.os.cloud.ManaCloudClient\n"
        "import com.mana.os.integration.AndroidSelfDevelopment\n"
        "import com.mana.os.integration.ManaGenesis\n",
        "dashboard imports",
    )

    source = replace_once(
        source,
        '        addButton("Настроить MANA Cloud") { configureCloud() }\n',
        '        addButton("Настроить MANA Cloud") { configureCloud() }\n'
        '        addButton("Саморазвитие MANA") { showSelfDevelopment() }\n',
        "dashboard button",
    )

    method = r'''    private fun showSelfDevelopment() {
        val engine = AndroidSelfDevelopment(this)
        base("MANA • Саморазвитие", "Сбор идей, анализ и планирование улучшений")
        addText("Саморазвитие: ${if (engine.isSelfDevelopmentEnabled()) "ВКЛЮЧЕНО" else "ОТКЛЮЧЕНО"}")
        addText("Идей собрано: ${engine.ideas().size}. Предложений: ${engine.proposals().size}.")
        addText("Источники: владелец, интернет, игры, приложения, баги, предупреждения безопасности и диагностика.")
        addText("Идеи сохраняются локально и анализируются. Автоматическое изменение исходников и установка обновлений не включены.")
        engine.proposals().takeLast(8).asReversed().forEach { proposal ->
            addText("• ${proposal.title}\nРиск: ${proposal.risk}; тип: ${proposal.scope}; подтверждение: ${if (proposal.requiresUserApproval) "нужно" else "не требуется для подготовки к тестам"}.")
        }
        addButton("Добавить идею") {
            val labels = arrayOf("Моё предложение", "Интернет", "Игра", "Приложение", "Обнаруженный баг", "Предупреждение безопасности", "Изученное знание", "Диагностика устройства", "Другой источник")
            val sources = arrayOf(
                ManaGenesis.IdeaSource.OWNER, ManaGenesis.IdeaSource.INTERNET,
                ManaGenesis.IdeaSource.GAME, ManaGenesis.IdeaSource.APP,
                ManaGenesis.IdeaSource.BUG_REPORT, ManaGenesis.IdeaSource.SECURITY_ALERT,
                ManaGenesis.IdeaSource.LEARNED_KNOWLEDGE, ManaGenesis.IdeaSource.DEVICE_DIAGNOSTIC,
                ManaGenesis.IdeaSource.OTHER
            )
            AlertDialog.Builder(this).setTitle("Откуда идея?")
                .setItems(labels) { _, which ->
                    val input = EditText(this).apply {
                        hint = "Опиши идею, ошибку или найденную возможность"
                        minLines = 3
                        setSingleLine(false)
                    }
                    AlertDialog.Builder(this).setTitle("Новая идея").setView(input)
                        .setNegativeButton("Отмена", null)
                        .setPositiveButton("Сохранить") { _, _ ->
                            runCatching { engine.collect(sources[which], input.text.toString()) }
                                .onSuccess { Toast.makeText(this, "Идея сохранена локально", Toast.LENGTH_SHORT).show() }
                                .onFailure { Toast.makeText(this, it.message ?: "Не удалось сохранить", Toast.LENGTH_LONG).show() }
                            showSelfDevelopment()
                        }.show()
                }.show()
        }
        addButton("Проанализировать собранные идеи") {
            if (!engine.isSelfDevelopmentEnabled()) {
                Toast.makeText(this, "Саморазвитие отключено владельцем", Toast.LENGTH_LONG).show()
            } else {
                val count = engine.analyze().size
                Toast.makeText(this, "Анализ завершён. Предложений: $count", Toast.LENGTH_SHORT).show()
            }
            showSelfDevelopment()
        }
        addButton(if (engine.isSelfDevelopmentEnabled()) "Отключить саморазвитие" else "Включить саморазвитие") {
            engine.setSelfDevelopmentEnabled(!engine.isSelfDevelopmentEnabled())
            showSelfDevelopment()
        }
        addButton("Назад") { showDashboard() }
    }

'''
    source = replace_once(
        source,
        "    private fun downloadFromUrl() {\n",
        method + "    private fun downloadFromUrl() {\n",
        "self-development screen method",
    )

    # Repair a known malformed multiline welcome literal in the extracted source copy.
    broken = 'base("MANA", "Первоначальная настройка\nЯ стану твоим главным интерфейсом, сохраняя Android в качестве системной основы.")'
    fixed = 'base("MANA", "Первоначальная настройка. Я стану твоим главным интерфейсом, сохраняя Android в качестве системной основы.")'
    if broken in source:
        source = source.replace(broken, fixed, 1)

    for marker in (
        "import com.mana.os.integration.AndroidSelfDevelopment",
        'addButton("Саморазвитие MANA")',
        "private fun showSelfDevelopment()",
        fixed,
    ):
        # The welcome literal may already be valid in a future source revision.
        if marker == fixed and fixed not in source and broken not in source:
            continue
        if marker not in source:
            raise SystemExit(f"Post-edit verification failed: missing {marker}")

    activity.write_text(source, encoding="utf-8")
    print("Integrated three MANA Kotlin components, dashboard, project license and Android core tests.")
    print("Verified Android source anchors. Changes apply only to the temporary CI source copy.")


if __name__ == "__main__":
    main()
