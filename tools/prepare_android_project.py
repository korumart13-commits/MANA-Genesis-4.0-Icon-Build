#!/usr/bin/env python3
"""Integrate reviewed MANA runtime and first usable home surface into an extracted Android project copy."""
from __future__ import annotations

import argparse
import json
import os
import re
import shutil
from pathlib import Path
from urllib.parse import urlsplit


def ensure_internet_permission(project: Path) -> None:
    """Ensure the generated MANA app can fetch its pinned first-run local model."""
    manifest = project / "app/src/main/AndroidManifest.xml"
    if not manifest.is_file():
        raise SystemExit(f"Android app manifest is missing: {manifest}")
    source = manifest.read_text(encoding="utf-8")
    permission = '    <uses-permission android:name="android.permission.INTERNET" />'
    if 'android.permission.INTERNET' in source:
        return
    match = re.search(r"<manifest\b[^>]*>", source)
    if not match:
        raise SystemExit("Could not safely locate the Android manifest root element.")
    source = source[:match.end()] + "\n" + permission + source[match.end():]
    manifest.write_text(source, encoding="utf-8")


def configure_litert_lm(project: Path) -> None:
    """Pin LiteRT-LM 0.16.1 and its minimum compatible Kotlin compiler in the generated app."""
    gradle_files = list(project.rglob("*.gradle")) + list(project.rglob("*.gradle.kts")) + list(project.rglob("libs.versions.toml"))
    gradle_files = [p for p in gradle_files if "build" not in p.parts and ".gradle" not in p.parts[:-1]]
    changed = 0
    for path in gradle_files:
        source = path.read_text(encoding="utf-8")
        count = source.count("2.0.21")
        if count:
            path.write_text(source.replace("2.0.21", "2.2.20"), encoding="utf-8")
            changed += count
    if changed == 0:
        # Do not guess at an unknown Kotlin/Gradle catalog layout.
        combined = "\\n".join(p.read_text(encoding="utf-8") for p in gradle_files)
        if "2.2.20" not in combined and "2.3." not in combined and "2.4." not in combined:
            raise SystemExit("LiteRT-LM requires Kotlin 2.2+; no known Kotlin 2.0.21 version anchor was found. Review the source project's Kotlin plugin before integrating.")
    app_gradle = project / "app/build.gradle.kts"
    if not app_gradle.is_file():
        raise SystemExit(f"App Gradle build file is missing: {app_gradle}")
    app_source = app_gradle.read_text(encoding="utf-8")
    dependency = 'implementation("com.google.ai.edge.litertlm:litertlm-android:0.16.1")'
    if dependency not in app_source:
        app_source += "\n\ndependencies {\n    " + dependency + "\n}\n"
        app_gradle.write_text(app_source, encoding="utf-8")


def replace_once(source: str, old: str, new: str, label: str) -> str:
    count = source.count(old)
    if count == 1:
        return source.replace(old, new, 1)
    if count == 0 and new in source:
        return source
    raise SystemExit(f"Refusing unsafe Android source edit ({label}): expected one anchor, found {count}.")

def ensure_audio_permission(manifest_text: str) -> str:
    """Add MODIFY_AUDIO_SETTINGS with a real newline, exactly once."""
    permission = '    <uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />\\n'
    if "android.permission.MODIFY_AUDIO_SETTINGS" not in manifest_text:
        manifest_text = replace_once(
            manifest_text, "<application", permission + "    <application",
            "MANA audio settings permission"
        )
    return manifest_text


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
        "package com.mana.os.integration\n\n"
        "/** Generated from trusted build variables; blank values intentionally mean offline mode. */\n"
        "object ManaGatewayDeploymentConfig {\n"
        f"    const val ENDPOINT = {json.dumps(endpoint)}\n"
        f"    const val EXPECTED_HOST = {json.dumps(expected_host)}\n"
        "    fun isConfigured(): Boolean = ENDPOINT.isNotBlank() && EXPECTED_HOST.isNotBlank()\n"
        "}\n",
        encoding="utf-8",
    )


def write_voice_deployment_config(project: Path, endpoint: str, expected_host: str) -> None:
    endpoint = endpoint.strip()
    expected_host = expected_host.strip().lower()
    if bool(endpoint) != bool(expected_host):
        raise SystemExit("MANA_TTS_ENDPOINT and MANA_TTS_HOST must be configured together.")
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
            or parsed.path != "/v1/mana/voice/synthesize"
            or parsed.port not in (None, 443)
        ):
            raise SystemExit("MANA voice build configuration failed HTTPS host/route validation.")
    output = project / "app/src/main/java/com/mana/os/integration/ManaVoiceDeploymentConfig.kt"
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(
        "package com.mana.os.integration\n\n"
        "/** Generated from trusted build variables; blank values intentionally mean system-TTS fallback. */\n"
        "object ManaVoiceDeploymentConfig {\n"
        f"    const val ENDPOINT = {json.dumps(endpoint)}\n"
        f"    const val EXPECTED_HOST = {json.dumps(expected_host)}\n"
        "    fun isConfigured(): Boolean = ENDPOINT.isNotBlank() && EXPECTED_HOST.isNotBlank()\n"
        "}\n",
        encoding="utf-8",
    )


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--project-dir", required=True, type=Path)
    parser.add_argument("--integration-dir", required=True, type=Path)
    parser.add_argument("--license-file", required=True, type=Path)
    parser.add_argument("--avatar-file", type=Path)
    parser.add_argument("--avatar-renderer-dir", type=Path)
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
    write_voice_deployment_config(project, os.environ.get("MANA_TTS_ENDPOINT", ""), os.environ.get("MANA_TTS_HOST", ""))
    ensure_internet_permission(project)
    configure_litert_lm(project)
    for name in ("ManaGenesis.kt", "ManaEngine.kt", "ManaGrowthEngine.kt", "ManaLiteRtLmRuntime.kt", "ManaSystemKernel.kt", "ManaCoreKernel.kt", "AndroidSelfDevelopment.kt", "ManaDevelopmentPipeline.kt", "ManaCognitiveRuntime.kt", "ManaLocalCognitiveMemory.kt", "ManaDialogueState.kt", "ManaDefaultCognitivePolicy.kt", "ManaGatewayReasoningModel.kt", "ManaAndroidGatewayCredentialStore.kt", "ManaGatewayChatService.kt", "ManaVoiceGatewayClient.kt", "ManaReadOnlyToolRegistry.kt", "ManaAndroidReadOnlyTools.kt", "ManaOwnerApprovalBroker.kt", "ManaApprovedActionValidation.kt", "ManaAndroidApprovedActionExecutor.kt"):
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
    avatar_service_source = integration / "ManaAvatarService.kt"
    if not avatar_service_source.is_file() or avatar_service_source.stat().st_size == 0:
        raise SystemExit("MANA persistent avatar service source is missing or empty.")
    shutil.copy2(avatar_service_source, target_dir / "ManaAvatarService.kt")

    diary_source = integration / "ManaDiaryActivity.kt"
    if not diary_source.is_file() or diary_source.stat().st_size == 0:
        raise SystemExit("MANA diary activity source is missing or empty.")
    shutil.copy2(diary_source, app_source_dir / "ManaDiaryActivity.kt")

    life_source = integration / "ManaLifeManagerActivity.kt"
    if not life_source.is_file() or life_source.stat().st_size == 0:
        raise SystemExit("MANA Life Manager activity source is missing or empty.")
    shutil.copy2(life_source, app_source_dir / "ManaLifeManagerActivity.kt")

    assets = project / "app/src/main/assets"
    assets.mkdir(parents=True, exist_ok=True)
    shutil.copy2(license_file, assets / "manaos-license.txt")

    # Bundle the generated original GLB and pinned local renderer; no runtime CDN access.
    if bool(args.avatar_file) != bool(args.avatar_renderer_dir):
        raise SystemExit("Avatar GLB and renderer directory must be supplied together.")
    if args.avatar_file and args.avatar_renderer_dir:
        avatar_file = args.avatar_file.resolve()
        renderer_dir = args.avatar_renderer_dir.resolve()
        if not avatar_file.is_file() or avatar_file.stat().st_size < 4096:
            raise SystemExit("Generated MANA avatar GLB is missing or unexpectedly small.")
        required_renderer_files = (
            renderer_dir / "three.module.js",
            renderer_dir / "three.core.js",
            renderer_dir / "examples/jsm/loaders/GLTFLoader.js",
            renderer_dir / "examples/jsm/utils/BufferGeometryUtils.js",
        )
        for renderer_file in required_renderer_files:
            if not renderer_file.is_file() or renderer_file.stat().st_size < 1024:
                raise SystemExit(f"Required local 3D renderer file is missing: {renderer_file}")
        avatar_assets = assets / "mana-avatar"
        avatar_assets.mkdir(parents=True, exist_ok=True)
        shutil.copy2(avatar_file, avatar_assets / "mana_avatar.glb")
        shutil.copy2(integration / "avatar" / "index.html", avatar_assets / "index.html")
        shutil.copy2(renderer_dir / "three.module.js", avatar_assets / "three.module.js")
        shutil.copy2(renderer_dir / "three.core.js", avatar_assets / "three.core.js")
        for relative in ("examples/jsm/loaders/GLTFLoader.js", "examples/jsm/utils/BufferGeometryUtils.js"):
            target = avatar_assets / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(renderer_dir / relative, target)
        shutil.copy2(integration / "avatar" / "THREE-LICENSE.txt", avatar_assets / "THREE-LICENSE.txt")

    test_source = integration / "tests" / "ManaCoreTest.kt"
    if not test_source.is_file() or test_source.stat().st_size == 0:
        raise SystemExit(f"Required Android unit test source is missing or empty: {test_source}")
    test_target = project / "core/src/test/java/com/mana/os/core/ManaCoreTest.kt"
    test_target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(test_source, test_target)

    app_test_target = project / "app/src/test/java/com/mana/os/integration/ManaCognitiveRuntimeTest.kt"
    app_test_target.parent.mkdir(parents=True, exist_ok=True)

    engine_test_source = integration / "tests" / "ManaEngineTest.kt"
    if not engine_test_source.is_file() or engine_test_source.stat().st_size == 0:
        raise SystemExit(f"Required MANA Engine unit test source is missing or empty: {engine_test_source}")
    shutil.copy2(engine_test_source, app_test_target.parent / "ManaEngineTest.kt")

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

    registry_test_source = integration / "tests" / "ManaReadOnlyToolRegistryTest.kt"
    if not registry_test_source.is_file() or registry_test_source.stat().st_size == 0:
        raise SystemExit(f"Required read-only tool registry test source is missing or empty: {registry_test_source}")
    shutil.copy2(registry_test_source, app_test_target.parent / "ManaReadOnlyToolRegistryTest.kt")

    approval_test_source = integration / "tests" / "ManaOwnerApprovalBrokerTest.kt"
    if not approval_test_source.is_file() or approval_test_source.stat().st_size == 0:
        raise SystemExit(f"Required owner approval broker test source is missing or empty: {approval_test_source}")
    shutil.copy2(approval_test_source, app_test_target.parent / "ManaOwnerApprovalBrokerTest.kt")

    approval_validation_test_source = integration / "tests" / "ManaApprovedActionValidationTest.kt"
    if not approval_validation_test_source.is_file() or approval_validation_test_source.stat().st_size == 0:
        raise SystemExit(f"Required approved action validation test source is missing or empty: {approval_validation_test_source}")
    shutil.copy2(approval_validation_test_source, app_test_target.parent / "ManaApprovedActionValidationTest.kt")

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
'
    for permission in ("android.permission.SYSTEM_ALERT_WINDOW", "android.permission.FOREGROUND_SERVICE", "android.permission.FOREGROUND_SERVICE_SPECIAL_USE", "android.permission.POST_NOTIFICATIONS"):
        if permission not in manifest_text:
            permission_line = '    <uses-permission android:name="' + permission + '" />\n'
            manifest_text = replace_once(manifest_text, "<application", permission_line + "    <application", "MANA permission " + permission)
    manifest_text = ensure_audio_permission(manifest_text)
    if "<queries>" not in manifest_text:
        launcher_visibility = """    <queries>
        <intent>
            <action android:name="android.intent.action.MAIN" />
            <category android:name="android.intent.category.LAUNCHER" />
        </intent>
    </queries>
"""
        manifest_text = replace_once(
            manifest_text, "<application", launcher_visibility + "    <application",
            "MANA launcher-only package visibility"
        )
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
    life_activity = '        <activity android:name="com.mana.os.app.ManaLifeManagerActivity" android:exported="false" android:label="MANA — задачи" />\n'
    if "com.mana.os.app.ManaLifeManagerActivity" not in manifest_text:
        manifest_text = replace_once(
            manifest_text,
            '        <activity android:name="com.mana.os.app.MainActivity" android:exported="true">',
            life_activity + '        <activity android:name="com.mana.os.app.MainActivity" android:exported="true">',
            "MANA Life Manager activity manifest entry",
        )
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
    avatar_service = """        <service android:name="com.mana.os.integration.ManaAvatarService" android:exported="false" android:stopWithTask="false" android:foregroundServiceType="specialUse">\n            <property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE" android:value="Persistent user-requested MANA avatar overlay" />\n        </service>\n"""
    if "com.mana.os.integration.ManaAvatarService" not in manifest_text:
        manifest_text = replace_once(manifest_text, "</application>", avatar_service + "    </application>", "MANA persistent avatar service")
    manifest.write_text(manifest_text, encoding="utf-8")

    print("Integrated MANA home, Google LiteRT-LM local inference adapter, self-development, license and Android core tests.")
    print("Bundled original MANA GLB avatar and local Three.js renderer." if args.avatar_file else "Avatar bundle not supplied; app will show the fallback surface.")
    print("MANA home is the app entry point and HOME-role candidate; Android still controls default-launcher selection.")
    print("Cloud chat only activates when a real HTTPS gateway has already been configured; no endpoint is fabricated.")


if __name__ == "__main__":
    main()
