# MANA CI repair and validation — 2026-11-25

## Confirmed root causes and corrections

### 1. Invalid Android workflow YAML
GitHub Actions had created failed workflow runs without jobs. A standalone diagnostic workflow installed `actionlint` and identified the exact parser error in `.github/workflows/manaos-android-build.yml`:

```
manaos-android-build.yml:32:0: could not parse as YAML:
yaml: line 32: did not find expected '-' indicator
```

The Android workflow was rewritten with simpler YAML and the dashboard integration logic was moved into `tools/prepare_android_project.py`. This removes the large embedded Python heredoc from the workflow and makes the integration script independently syntax-checkable.

### 2. Gradle cache pointed at the wrong directory
After YAML repair, the workflow reached Java setup but failed because `actions/setup-java` tried to configure Gradle caching in the build repository root, which contains no Gradle project:

```
No file ... matched to [**/*.gradle*, **/gradle.properties, **/gradle-wrapper.properties, ...]
```

The Gradle cache option was removed from Java setup. The Android project is downloaded later, so the build now invokes its own checked-in Gradle Wrapper from the extracted project directory.

### 3. Actionlint caught an invalid action-step property
A follow-up lint run identified that `working-directory` cannot be attached to a step using `uses:`. The Gradle setup action was removed; the project wrapper is invoked directly by a `run:` step with the extracted Android project as its working directory.

## Test coverage added

- The standalone diagnostic workflow runs actionlint over every workflow YAML file.
- Python tooling is byte-compiled and all Python regression tests are executed.
- The Android preparation helper injects a JUnit 4 dependency and `integration/tests/ManaCoreTest.kt` into the extracted core module.
- The Kotlin unit tests cover app identity/module catalog, receptionist defaults, task lifecycle, finance validation, permission consent, update approval transitions, local memory, device compatibility prerequisites, SHA-256 verification, and guardian warning counts.

## Verified result

Verified Android build commit: `bc5c32e8c5f217f552e62f2bb4a19297dc4f65c2`

GitHub Actions run: [45594903357](https://github.com/korumart13-commits/MANA-Genesis-4.0-Icon-Build/actions/runs/45594903357)

- Android Gradle build: **BUILD SUCCESSFUL**.
- Android modules compiled: `:app`, `:core`, `:launcher`, `:settings`, `:systemui`.
- Debug and release unit-test variants: 10 tests each; 20 total executions, 0 failures, 0 errors, 0 skipped.
- Python tooling regression tests: 10 passed.
- Workflow YAML lint: actionlint passed for all three workflow files.
- Static analysis: 7 LOW-severity broad-catch review notes; 0 MEDIUM, 0 HIGH, 0 CRITICAL.
- Build quality gate: `READY_FOR_OWNER_REVIEW`.
- Debug APK artifact: ID `15011854285`, APK size 2,635,392 bytes.
- APK SHA-256: `76bcd3858026d628d23bbce43d090913d92948440d689302297287e98b21930e`.

## Safety and limits

- This is a debug APK, not a production-signed release.
- No realme device installation or hardware compatibility test was performed.
- No Google Play approval, malware certification, or production signing is implied.
- No automatic merge, release publication, APK installation, firmware flashing, partition change, or Android security bypass was performed.
- Play Protect, Verified Boot and SELinux remain respected.
- The quality gate authorizes owner review only; it does not authorize merging, publishing, signing, or installation.
