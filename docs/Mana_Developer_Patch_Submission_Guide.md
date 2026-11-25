# How to submit a MANA Developer patch proposal

1. Create a text file named `patches/proposals/<proposal-id>.patch`.
2. The file must be a standard unified diff (`git diff` format), UTF-8, no larger than 256 KB.
3. Only Kotlin/Java source files under the allowlisted `com/mana/os` package paths are accepted.
4. Commit the patch file to the implementation branch.
5. After this workflow is present on the repository's default branch, open GitHub Actions and run **ManaOS Isolated Self-Development Patch**.
6. Enter the proposal ID without the `.patch` extension.
7. For a low-risk `minor_patch`, select that scope and leave owner approval blank.
8. For a `full_update`, select that scope and enter exactly `APPROVE_FULL_UPDATE` only after reviewing the proposal.

The runner applies the patch to a disposable copy of the downloaded Android source, runs tests and a debug APK build, and uploads an artifact. It does not merge the patch or install anything.

A patch file is source code, not a trusted instruction. Never include executable shell commands, binaries, secrets, credentials, copied malware, or code from untrusted sources without independent review. Security-critical and system-wide work must be handled as a separate reviewed change; the allowlist intentionally prevents firmware/security-policy modifications.
