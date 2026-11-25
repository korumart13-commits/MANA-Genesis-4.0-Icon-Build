# MANA Code Intelligence — Stage 1

## Purpose
MANA Code Intelligence performs read-only static triage of Kotlin and Java source files. It produces a Markdown report for people and a JSON report for later MANA analysis.

## Current checks
- TODO/FIXME/XXX markers
- Kotlin non-null assertion operator
- Thread.sleep calls
- Empty and broad catch blocks
- GlobalScope usage
- WebView JavaScript bridge registration
- Dynamic DEX/class loading
- Process execution APIs
- TLS verification override patterns
- Possible hard-coded credential literals

Each finding includes a rule ID, severity, file, line, evidence excerpt and a suggested review. Findings are leads for investigation, not confirmed bugs or vulnerabilities.

## Integration
Both the normal APK build and isolated self-development patch workflow run the analyzer against the extracted Android project. The reports are uploaded as GitHub Actions artifacts named `ManaOS-code-intelligence` (normal build) or included in the isolated patch report bundle.

## Important limits
- The analyzer is read-only and never executes project source.
- It does not write patches, edit files, commit changes, merge branches, publish releases or install APKs.
- Pattern matching can produce false positives and miss real defects.
- It does not replace Kotlin compiler diagnostics, unit/instrumentation tests, dependency scanning, threat modeling or manual review.
- A future AI patch author must cite findings and propose a minimal diff; the existing patch gate and isolated build pipeline remain separate controls.
