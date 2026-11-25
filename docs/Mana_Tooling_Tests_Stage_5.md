# MANA Tooling Tests — Stage 5

The Python tooling has a small regression suite covering:
- acceptance of a minimal Kotlin/Java diff under the source allowlist;
- rejection of Android manifest edits;
- rejection of path traversal;
- safe prompt-only behavior when AI credentials/model are absent.

CI runs these tests and Python bytecode compilation before the Android Gradle build. These checks do not compile the Android app and do not validate Kotlin runtime behavior.
