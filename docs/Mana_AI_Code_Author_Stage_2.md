# MANA AI Code Author — Stage 2

## Purpose
Generate a bounded, review-only unified diff from MANA Code Intelligence findings. This is an optional model-assisted author, not an autonomous self-modifying app.

## Inputs
- JSON report from the read-only static analyzer.
- Up to 20 findings and bounded source excerpts (maximum 18,000 context characters).
- Optional OpenAI-compatible chat-completions endpoint.

## Configuration
- Store `MANA_AI_API_KEY` only as a protected GitHub Actions secret.
- Store `MANA_AI_MODEL` as a repository variable.
- Optional `MANA_AI_API_URL` can point to a compatible endpoint.
- Never put credentials in source files, commits, prompts, or APK assets.

Without a key/model, the tool writes a prompt artifact and clearly reports that no AI patch was generated.

## Safety
- AI output is treated as untrusted text.
- Only Kotlin/Java files under the explicit `com/mana/os` source allowlist are accepted.
- Manifests, Gradle, workflows, signing, firmware, kernel, bootloader, vendor, AVB and SELinux are excluded.
- Maximum 12 files and 256 KB per proposed patch.
- The author never applies the patch, executes generated code, commits, merges, publishes or installs anything.
- Every patch must pass the separate MANA Patch Gate and isolated Gradle tests.
- AI claims are not evidence that tests passed. CI is the evidence.

## Maturity
Stage 2 provides optional AI-assisted patch drafting. It is not continuous autonomous coding: credentials, endpoint/model configuration, human review and the independent patch gate remain necessary.
