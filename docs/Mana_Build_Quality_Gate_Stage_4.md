# MANA Build Quality Gate — Stage 4

This stage collects evidence after Gradle tests and debug APK assembly.

## Checks
- Finds and parses Gradle JUnit XML test reports.
- Counts tests, failures, errors and skipped tests.
- Reads the static Code Intelligence JSON report.
- Flags HIGH/CRITICAL static findings for owner review.
- Emits machine-readable JSON and a Markdown report.

## Readiness rule
`READY_FOR_OWNER_REVIEW` requires at least one parseable test report, zero test failures/errors, no XML parse errors and no HIGH/CRITICAL static findings. Otherwise status is `NEEDS_REVIEW`.

This is an evidence gate, not a release gate. It never signs, publishes, merges, installs, changes firmware or claims Google Play approval. Debug APKs remain test artifacts only.
