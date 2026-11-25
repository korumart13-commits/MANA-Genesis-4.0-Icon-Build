#!/usr/bin/env python3
"""Integrate reviewed MANA runtime and first usable home surface into an extracted Android project copy."""
from __future__ import annotations

import argparse
import json
import os
import shutil
from pathlib import Path
from urllib.parse import urlsplit


def replace_once(source: str, old: str, new: str, label: str) -> str:
    count = source.count(old)
    if count == 1:
        return source.replace(old, new, 1)
    if count == 0 and new in source:
        return source
    raise SystemExit(f"Refusing unsafe Android source edit ({label}): expected one anchor, found {count}.")


def write_gateway_deployment_config(project: Path, endpoint: str, expected_host: str) -> None:
    endpoint = endpoint.strip()
    expected_host = expected_host.strip().lower()
    if bool(endpoint) != bool(expected_host):
        raise SystemExit("MANA_GATEWAY_ENDPOINT and MANA_GATEWAY_HOST must be configured together.")
    if endpoint:
        parsed = urlsplit(endpoint)
        if (
            parsed.scheme.lower() != "https"
            or not parsed.hostname
            or parsed.hostname.lower() != expected_host
            or parsed.username is not None
            or parsed.password is not None
            or parsed.query
            or parsed.fragment
            or parsed.path != "/v1/mana/respond"
            or parsed.port not in (None, 443)
        ):
            raise SystemExit("MANA gateway build configuration failed HTTPS host/route validation.")
    output = project / "app/src/main/java/com/mana/os/integration/ManaGatewayDeploymentConfig.kt"
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(
        "package com.mana.os.integration\\n\\n"
        "/** Generated from trusted build variables; blank values intentionally mean offline mode. */\\n"
        "object ManaGatewayDeploymentConfig {\\n"
        f"    const val ENDPOINT = {json.dumps(endpoint)}\\n"
        f"    const val EXPECTED_HOST = {json.dumps(expected_host)}\\n"
        "    fun isConfigured(): Boolean = ENDPOINT.isNotBlank() && EXPECTED_HOST.isNotBlank()\\n"
        "}\\n",
        encoding="utf-8",
    )


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
    write_gateway_deployment_config(project, os.environ.get("MANA_GATEWAY_ENDPOINT", ""), os.environ.get("MANA_GATEWAY_HOST", ""))
    for name in ("ManaGenesis.kt", "AndroidSelfDevelopment.kt", "ManaDevelopmentPipeline.kt", "ManaCognitiveRuntime.kt", "ManaLocalCognitiveMemory.kt", "ManaDialogueState.kt", "ManaDefaultCognitivePolicy.kt", "ManaGatewayReasoningModel.kt"):
        source = integration / name
        if not source.is_file() or source.stat().st_size == 0:
            raise SystemExit(f"Required integration source is missing or empty: {source}")
        shutil.copy2(source, target_dir / name)

    home_source = integration / "ManaHomeActivity.kt"
    if not home_source.is_file() or home_source.stat().st_size == 0:
        raise SystemExit("MANA home activity source is missing or empty.")
    app_source_dir = project / "app/src/main/java/com/mana/os/app"
    app_source_dir.mkdir(parents=True, exist_ok=True)
    shutil.copy2(home_source, app_source_dir / "ManaHomeActivity.kt")
    diary_source = integration / "ManaDiaryActivity.kt"
    if not diary_source.is_file() or diary_source.stat().st_size == 0:
        raise SystemExit("MANA diary activity source is missing or empty.")
    shutil.copy2(diary_source, app_source_dir / "ManaDiaryActivity.kt")

    assets = project / "app/src/main/assets"
    assets.mkdir(parents=True, exist_ok=True)
    shutil.copy2(license_file, assets / "manaos-license.txt")

    test_source = integration / "tests" / "ManaCoreTest.kt"
    if not test_source.is_file() or test_source.stat().st_size == 0:
        raise SystemExit(f"Required Android unit test source is missing or empty: {test_source}")
    test_target = project / "core/src/test/java/com/mana/os/core/ManaCoreTest.kt"
    test_target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(test_source, test_target)

    cognitive_test_source = integration / "tests" / "ManaCognitiveRuntimeTest.kt"
    if not cognitive_test_source.is_file() or cognitive_test_source.stat().st_size == 0:
        raise SystemExit(f"Required cognitive runtime test source is missing or empty: {cognitive_test_source}")
    app_test_target = project / "app/src/test/java/com/mana/os/integration/ManaCognitiveRuntimeTest.kt"
    app_test_target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(cognitive_test_source, app_test_target)

    policy_test_source = integration / "tests" / "ManaDefaultCognitivePolicyTest.kt"
    if not policy_test_source.is_file() or policy_test_source.stat().st_size == 0:
        raise SystemExit(f"Required default policy test source is missing or empty: {policy_test_source}")
    shutil.copy2(policy_test_source, app_test_target.parent / "ManaDefaultCognitivePolicyTest.kt")

    gateway_test_source = integration / "tests" / "ManaGatewayAdapterTest.kt"
    if not gateway_test_source.is_file() or gateway_test_source.stat().st_size == 0:
        raise SystemExit(f"Required gateway adapter test source is missing or empty: {gateway_test_source}")
    shutil.copy2(gateway_test_source, app_test_target.parent / "ManaGatewayAdapterTest.kt")

    junit_dependency = 'testImplementation("junit:junit:4.13.2")'
    app_gradle = project / "app/build.gradle.kts"
    if not app_gradle.is_file():
        raise SystemExit(f"App Gradle build file is missing: {app_gradle}")
    app_gradle_source = app_gradle.read_text(encoding="utf-8")
    if junit_dependency not in app_gradle_source:
        app_gradle_source += (
            "\n\ndependencies {\n"
            f"    {junit_dependency}\n"
            "}\n"
        )
        app_gradle.write_text(app_gradle_source, encoding="utf-8")

    core_gradle = project / "core/build.gradle.kts"
    if not core_gradle.is_file():
        raise SystemExit(f"Core Gradle build file is missing: {core_gradle}")
    gradle_source = core_gradle.read_text(encoding="utf-8")
    if junit_dependency not in gradle_source:
        gradle_source += (
            "\n\ndependencies {\n"
            f"    {junit_dependency}\n"
            "}\n"
        )
        core_gradle.write_text(gradle_source, encoding="utf-8")

    # The extracted legacy onboarding activity is no longer the app entry point.
    # Replace it with a tiny compatibility stub so stale UI code cannot break the new MANA home build.
    legacy_activity = app_source_dir / "MainActivity.kt"
    if legacy_activity.is_file():
        legacy_activity.write_text(
            "package com.mana.os.app\n\n"
            "import android.app.Activity\n\n"
            "/** Legacy manifest compatibility stub; ManaHomeActivity is the actual entry point. */\n"
            "class MainActivity : Activity()\n",
            encoding="utf-8",
        )

    manifest = project / "app/src/main/AndroidManifest.xml"
    manifest_text = manifest.read_text(encoding="utf-8")
    old_launcher_filter = """            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
"""
    manifest_text = replace_once(manifest_text, old_launcher_filter, "", "old app launcher filter")
    old_home_filter = """            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.HOME" />
                <category android:name="android.intent.category.DEFAULT" />
            </intent-filter>
"""
    manifest_text = replace_once(manifest_text, old_home_filter, "", "old app HOME filter")
    home_activity = """        <activity android:name="com.mana.os.app.ManaHomeActivity" android:exported="true" android:label="MANA">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.HOME" />
                <category android:name="android.intent.category.DEFAULT" />
            </intent-filter>
        </activity>
"""
    diary_activity = '        <activity android:name="com.mana.os.app.ManaDiaryActivity" android:exported="false" android:label="Дневник MANA" />\n'
    if "com.mana.os.app.ManaDiaryActivity" not in manifest_text:
        manifest_text = replace_once(
            manifest_text,
            '        <activity android:name="com.mana.os.app.MainActivity" android:exported="true">',
            diary_activity + '        <activity android:name="com.mana.os.app.MainActivity" android:exported="true">',
            "MANA diary activity manifest entry",
        )
    manifest_text = replace_once(
        manifest_text,
        '        <activity android:name="com.mana.os.app.MainActivity" android:exported="true">',
        home_activity + '        <activity android:name="com.mana.os.app.MainActivity" android:exported="true">',
        "MANA home activity manifest entry",
    )
    manifest.write_text(manifest_text, encoding="utf-8")

    print("Integrated MANA home activity, local self-development, license and Android core tests.")
    print("MANA home is the app entry point and HOME-role candidate; Android still controls default-launcher selection.")
    print("Cloud chat only activates when a real HTTPS gateway has already been configured; no endpoint is fabricated.")


if __name__ == "__main__":
    main()
