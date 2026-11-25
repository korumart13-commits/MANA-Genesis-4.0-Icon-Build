from __future__ import annotations
import json
import sys
import tempfile
import unittest
from pathlib import Path

TOOLS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(TOOLS))
import mana_ai_code_author as author

class ManaToolTests(unittest.TestCase):
    def test_android_sources_do_not_use_nullable_ifblank_default(self):
        integration = Path(__file__).resolve().parents[2] / "integration"
        for source in integration.glob("*.kt"):
            text = source.read_text(encoding="utf-8")
            self.assertNotIn("ifBlank { null }", text, str(source))

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
                    sys.argv = ["mana_ai_code_author.py", "--report", str(report), "--source", str(source), "--out", str(out)]
                    mana_ai_code_author.main()
                finally:
                    sys.argv = old_argv
            self.assertTrue((out / "MANA_AI_PATCH_REQUEST.md").is_file())
            self.assertTrue((out / "MANA_AI_AUTHOR_REPORT.md").is_file())
            self.assertFalse((out / "MANA_AI_PROPOSED.patch").exists())

if __name__ == "__main__":
    unittest.main()
