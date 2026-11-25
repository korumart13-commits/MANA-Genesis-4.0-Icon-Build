# CI investigation — 2026-11-25

## Source issues corrected
Two Kotlin JSON-decoding expressions used `String.ifBlank { null }`:
- `integration/AndroidSelfDevelopment.kt`: sourceReference
- `integration/ManaDevelopmentPipeline.kt`: patchReference

The default lambda for `ifBlank` must return a non-null CharSequence. Both expressions now use `takeIf { it.isNotBlank() }`, which correctly produces a nullable String.

A Python regression test now scans integration Kotlin sources and rejects reintroduction of `ifBlank { null }`.

## GitHub Actions evidence
The latest visible runs before/around these changes:
- Run 50, commit `b7d00c1`: conclusion failure.
- Run 51, commit `b66c244`: conclusion failure.

The GitHub Actions API returned zero jobs for both runs. Consequently there are no job steps or job logs available to identify whether failure occurred during workflow validation, runner allocation, or before the first step. The failures cannot be attributed conclusively to the Kotlin compile issue.

The later test-only commit `ab2c211` is the current branch head. No subsequent run is visible in the first page of the Actions run listing.

## Validation status
- Source-level Kotlin defects above: corrected by inspection.
- Python regression tests: added to CI, but no successful runner execution is available to confirm them.
- Android Gradle build: not confirmed.
- APK: no new APK is claimed.
- No PR merge, release publication, phone installation, or firmware modification was performed.

## Next diagnostic requirement
A GitHub Actions run with an actual job record and accessible step logs is required. If GitHub continues to report zero jobs, inspect repository Actions enablement and workflow validation in the repository's Actions/Checks UI before further code changes.
