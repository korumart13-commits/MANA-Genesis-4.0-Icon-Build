# MANA Developer — Isolated Patch Pipeline

## What this stage provides
The repository now has a controlled CI route for testing a proposed change against the current Android source archive. A patch is a UTF-8 unified diff stored at `patches/proposals/<proposal-id>.patch`.

Run it from GitHub Actions → **ManaOS Isolated Self-Development Patch** → Run workflow. Use a proposal ID containing only letters, digits and hyphens. Select:
- `minor_patch` for a low-risk application-source patch.
- `full_update` for a larger app-project update; this requires the exact owner approval token `APPROVE_FULL_UPDATE`.

The workflow checks the patch, downloads the source archive, integrates the current MANA bootstrap and local idea store, applies the patch in a disposable GitHub-hosted runner, runs Gradle tests, attempts a debug APK build, and uploads a report/artifact.

## Strict patch boundary
Only Kotlin/Java source files under the MANA application/module package paths are allowed. Build scripts, manifests, workflows, signing material, Android security policy, bootloader, kernel, vendor files, binaries and arbitrary paths are denied. A patch is limited to 12 source files and 256 KB.

## What it does NOT do
- It does not execute code or commands contained in ideas, internet pages, games, crash logs, or malware samples.
- It does not commit changes, merge a branch, publish a release, install an APK, or access the owner's phone.
- It does not modify Android firmware, partitions, Verified Boot, SELinux, Play Protect, or device permissions.
- The full-update approval here is for a ManaOS application-project build only; it is not approval to flash firmware.

## Current maturity
This is a CI patch-validation executor, not yet an autonomous AI code author. A trusted analyzer/patch author must produce the unified diff. The workflow validates and tests it. Human review and merging remain separate steps. A successful CI build is not proof of runtime correctness on the realme RMX5566.
