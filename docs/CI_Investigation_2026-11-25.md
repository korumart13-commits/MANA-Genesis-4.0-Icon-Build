# CI investigation — 2026-11-25

## Source issues corrected
Two Kotlin JSON-decoding expressions used `String.ifBlank { null }`:
- `integration/AndroidSelfDevelopment.kt`: `sourceReference`
- `integration/ManaDevelopmentPipeline.kt`: `patchReference`

The default lambda for `ifBlank` must return a non-null `CharSequence`. Both expressions now use `takeIf { it.isNotBlank() }`, which correctly produces a nullable `String`.

A Python regression test scans integration Kotlin sources and rejects reintroduction of `ifBlank { null }`.

## Additional regression coverage
The Python test suite now also exercises the isolated patch gate in temporary directories:
- accepts and applies a change to an allowlisted Kotlin file;
- rejects AndroidManifest edits;
- rejects path traversal;
- checks both old and new paths, preventing a protected file from being renamed into an allowed path;
- rejects a full update unless the exact owner-approval token is supplied.

These tests are committed to the branch, but have not yet been confirmed as executed by a successful CI run.

## GitHub Actions evidence and limits
- Previously observed run 50 (commit `b7d00c1`) and run 51 (commit `b66c244`) were reported as failed.
- The job-list endpoint returned zero jobs for those runs, so there were no step logs available through that endpoint.
- Important limitation: the available commit-workflow-runs connector only returns pull-request-triggered runs; it does not provide a complete listing of push-triggered runs. It returned no PR-triggered run for commit `42c78b5a8381ae48fe1e4260ae1e5de41ec25506`.
- The combined commit-status endpoint also returned no status contexts for that commit.

Therefore the available evidence does not establish whether the failures were caused by workflow validation, repository Actions settings, runner allocation, or an early failure before job creation. It also does not prove that no push-triggered run occurred. The exact root cause remains unconfirmed; attributing the failures to the Kotlin compile issue would be speculation.

## Validation status
- The two Kotlin nullable-default expressions: corrected by source inspection.
- Python regression tests: expanded, execution not confirmed.
- Android Gradle build: not confirmed.
- New APK: not claimed.
- PR merge, release publication, phone installation, and firmware modification: not performed.

## Next diagnostic requirement
A complete Actions run record or a job log is required to identify the workflow-level failure. The repository Actions/Checks UI may expose validation or runner diagnostics that are not available through the current connector endpoints. Do not treat an empty PR-only run listing as proof that push workflows did not run.
