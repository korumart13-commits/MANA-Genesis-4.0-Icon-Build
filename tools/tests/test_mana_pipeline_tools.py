from __future__ import annotations

import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

TOOLS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(TOOLS))
import mana_ai_code_author as author

PATCH_GATE = TOOLS / "mana_patch_gate.py"


class ManaToolTests(unittest.TestCase):
    def test_android_sources_do_not_use_nullable_ifblank_default(self):
        integration = Path(__file__).resolve().parents[2] / "integration"
        for source in integration.glob("*.kt"):
            text = source.read_text(encoding="utf-8")
            self.assertNotIn("ifBlank { null }", text, str(source))

    def test_cognitive_runtime_is_bounded_and_has_default_deny_policy_contract(self):
        integration = Path(__file__).resolve().parents[2] / "integration"
        source = (integration / "ManaCognitiveRuntime.kt").read_text(encoding="utf-8")
        self.assertIn("require(maxCycles in 1..8)", source)
        self.assertIn("policy.check(action)", source)
        self.assertIn("ManaActionVerdict.DENY", source)
        self.assertIn("private chain-of-thought", source)
        self.assertIn("unknown tools and sensitive actions", source.lower())

    def test_android_packaging_includes_cognitive_runtime(self):
        prepare = TOOLS / "prepare_android_project.py"
        source = prepare.read_text(encoding="utf-8")
        self.assertIn('"ManaCognitiveRuntime.kt"', source)

    def test_local_memory_is_bounded_and_filters_secret_markers(self):
        integration = Path(__file__).resolve().parents[2] / "integration"
        source = (integration / "ManaLocalCognitiveMemory.kt").read_text(encoding="utf-8")
        self.assertIn("MAX_ENTRIES = 300", source)
        self.assertIn("take(1500)", source)
        self.assertIn("looksSensitive(entry.text)", source)
        self.assertIn("fun clear()", source)
        self.assertNotIn("https://", source)

    def test_dialogue_state_is_bounded_and_packaged(self):
        integration = Path(__file__).resolve().parents[2] / "integration"
        source = (integration / "ManaDialogueState.kt").read_text(encoding="utf-8")
        prepare = (TOOLS / "prepare_android_project.py").read_text(encoding="utf-8")
        self.assertIn("require(maxTurns in 1..100)", source)
        self.assertIn("while (turns.size > maxTurns)", source)
        self.assertIn("take(maxTextLength)", source)
        self.assertIn('"ManaDialogueState.kt"', prepare)

    def test_cognitive_runtime_checks_host_stop_before_decision_and_tool_execution(self):
        integration = Path(__file__).resolve().parents[2] / "integration"
        source = (integration / "ManaCognitiveRuntime.kt").read_text(encoding="utf-8")
        self.assertIn("private val control: ManaRuntimeControl", source)
        self.assertGreaterEqual(source.count("control.isStopRequested()"), 3)
        self.assertIn("Остановлено по команде владельца.", source)
        self.assertIn("interface ManaRuntimeControl", source)

    def run_patch_gate(self, project: Path, patch_text: str, *extra: str):
        patch = project.parent / "proposal.patch"
        patch.write_text(patch_text, encoding="utf-8")
        return subprocess.run(
            [
                sys.executable,
                str(PATCH_GATE),
                "--project-dir", str(project),
                "--patch", str(patch),
                "--scope", "minor_patch",
                *extra,
            ],
            text=True,
            capture_output=True,
            check=False,
        )

    def test_patch_gate_applies_allowlisted_kotlin_change(self):
        with tempfile.TemporaryDirectory() as tmp:
            project = Path(tmp) / "project"
            target = project / "app/src/main/java/com/mana/os/Fix.kt"
            target.parent.mkdir(parents=True)
            target.write_text("val answer = 1\n", encoding="utf-8")
            diff = """diff --git a/app/src/main/java/com/mana/os/Fix.kt b/app/src/main/java/com/mana/os/Fix.kt
--- a/app/src/main/java/com/mana/os/Fix.kt
+++ b/app/src/main/java/com/mana/os/Fix.kt
@@ -1 +1 @@
-val answer = 1
+val answer = 2
"""
            result = self.run_patch_gate(project, diff)
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertEqual(target.read_text(encoding="utf-8"), "val answer = 2\n")

    def test_patch_gate_rejects_manifest_change(self):
        with tempfile.TemporaryDirectory() as tmp:
            project = Path(tmp) / "project"
            project.mkdir()
            diff = """diff --git a/app/src/main/AndroidManifest.xml b/app/src/main/AndroidManifest.xml
--- a/app/src/main/AndroidManifest.xml
+++ b/app/src/main/AndroidManifest.xml
@@ -1 +1 @@
-old
+new
"""
            result = self.run_patch_gate(project, diff)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("Protected path", result.stderr)

    def test_patch_gate_rejects_path_traversal(self):
        with tempfile.TemporaryDirectory() as tmp:
            project = Path(tmp) / "project"
            project.mkdir()
            diff = """diff --git a/app/src/main/java/com/mana/os/../../../../outside.kt b/app/src/main/java/com/mana/os/../../../../outside.kt
--- a/app/src/main/java/com/mana/os/../../../../outside.kt
+++ b/app/src/main/java/com/mana/os/../../../../outside.kt
@@ -1 +1 @@
-old
+new
"""
            result = self.run_patch_gate(project, diff)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("Unsafe patch path", result.stderr)

    def test_patch_gate_checks_both_old_and_new_paths(self):
        with tempfile.TemporaryDirectory() as tmp:
            project = Path(tmp) / "project"
            project.mkdir()
            diff = """diff --git a/app/src/main/AndroidManifest.xml b/app/src/main/java/com/mana/os/Allowed.kt
--- a/app/src/main/AndroidManifest.xml
+++ b/app/src/main/java/com/mana/os/Allowed.kt
@@ -1 +1 @@
-old
+new
"""
            result = self.run_patch_gate(project, diff)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("Protected path", result.stderr)

    def test_full_update_requires_exact_owner_approval(self):
        with tempfile.TemporaryDirectory() as tmp:
            project = Path(tmp) / "project"
            target = project / "app/src/main/java/com/mana/os/Fix.kt"
            target.parent.mkdir(parents=True)
            target.write_text("val answer = 1\n", encoding="utf-8")
            diff = """diff --git a/app/src/main/java/com/mana/os/Fix.kt b/app/src/main/java/com/mana/os/Fix.kt
--- a/app/src/main/java/com/mana/os/Fix.kt
+++ b/app/src/main/java/com/mana/os/Fix.kt
@@ -1 +1 @@
-val answer = 1
+val answer = 2
"""
            patch = project.parent / "proposal.patch"
            patch.write_text(diff, encoding="utf-8")
            command = [
                sys.executable, str(PATCH_GATE),
                "--project-dir", str(project),
                "--patch", str(patch),
                "--scope", "full_update",
            ]
            rejected = subprocess.run(command, text=True, capture_output=True, check=False)
            self.assertNotEqual(rejected.returncode, 0)
            self.assertIn("requires owner_approval", rejected.stderr)
            self.assertEqual(target.read_text(encoding="utf-8"), "val answer = 1\n")


    def test_ai_author_accepts_only_allowlisted_source_diff(self):
        diff = """diff --git a/app/src/main/java/com/mana/os/Fix.kt b/app/src/main/java/com/mana/os/Fix.kt
index 1111111..2222222 100644
--- a/app/src/main/java/com/mana/os/Fix.kt
+++ b/app/src/main/java/com/mana/os/Fix.kt
@@ -1 +1 @@
-val x = value!!
+val x = value?.toString() ?: ""
"""
        author.validate_diff(diff)

    def test_ai_author_rejects_manifest(self):
        diff = """diff --git a/app/src/main/AndroidManifest.xml b/app/src/main/AndroidManifest.xml
--- a/app/src/main/AndroidManifest.xml
+++ b/app/src/main/AndroidManifest.xml
@@ -1 +1 @@
-old
+new
"""
        with self.assertRaises(ValueError):
            author.validate_diff(diff)

    def test_ai_author_rejects_path_traversal(self):
        diff = """diff --git a/app/src/main/java/com/mana/os/../../../../secrets.kt b/app/src/main/java/com/mana/os/../../../../secrets.kt
--- a/app/src/main/java/com/mana/os/../../../../secrets.kt
+++ b/app/src/main/java/com/mana/os/../../../../secrets.kt
@@ -1 +1 @@
-old
+new
"""
        with self.assertRaises(ValueError):
            author.validate_diff(diff)

    def test_no_api_key_emits_prompt_without_patch(self):
        import mana_ai_code_author
        from unittest.mock import patch

        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            source = root / "source"
            source.mkdir()
            (source / "Sample.kt").write_text("class Sample", encoding="utf-8")
            report = root / "report.json"
            report.write_text(json.dumps({"findings": []}), encoding="utf-8")
            out = root / "out"
            with patch.dict("os.environ", {"MANA_AI_API_KEY": "", "MANA_AI_MODEL": ""}):
                old_argv = sys.argv
                try:
                    sys.argv = [
                        "mana_ai_code_author.py", "--report", str(report),
                        "--source", str(source), "--out", str(out),
                    ]
                    mana_ai_code_author.main()
                finally:
                    sys.argv = old_argv
            self.assertTrue((out / "MANA_AI_PATCH_REQUEST.md").is_file())
            self.assertTrue((out / "MANA_AI_AUTHOR_REPORT.md").is_file())
            self.assertFalse((out / "MANA_AI_PROPOSED.patch").exists())


if __name__ == "__main__":
    unittest.main()
