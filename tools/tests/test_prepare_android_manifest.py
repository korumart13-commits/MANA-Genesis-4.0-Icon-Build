import sys
import unittest
from pathlib import Path

TOOLS_DIR = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(TOOLS_DIR))

from prepare_android_project import ensure_audio_permission


class ManifestPermissionTests(unittest.TestCase):
    def test_audio_permission_is_inserted_with_real_newline(self):
        source = '<manifest xmlns:android="http://schemas.android.com/apk/res/android">\n    <application />\n</manifest>\n'
        result = ensure_audio_permission(source)
        self.assertIn('android.permission.MODIFY_AUDIO_SETTINGS" />\n    <application', result)
        self.assertNotIn('MODIFY_AUDIO_SETTINGS" />\\\\n', result)
        self.assertEqual(result.count("android.permission.MODIFY_AUDIO_SETTINGS"), 1)

    def test_existing_audio_permission_is_not_duplicated(self):
        source = '<manifest>\n    <uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />\n    <application />\n</manifest>\n'
        result = ensure_audio_permission(source)
        self.assertEqual(result.count("android.permission.MODIFY_AUDIO_SETTINGS"), 1)
        self.assertEqual(result, source)


if __name__ == "__main__":
    unittest.main()
