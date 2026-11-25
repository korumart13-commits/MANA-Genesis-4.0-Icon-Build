# MANA Genesis 4.0 — Self-Development Runtime (Stage 1)

## Purpose
The Android application now has a first usable, local self-development workspace. It collects ideas as data, stores them on-device, and performs a transparent first-pass classification into improvement proposals.

## Accepted idea sources
- Owner suggestions
- Internet research and learned knowledge
- Games and permitted game integrations
- Apps and observed defects
- Bug reports and crash/diagnostic reports
- Security alerts
- Device diagnostics
- Other explicitly attributed sources

Collected material is inert text and metadata. It is never interpreted as executable code, shell commands, scripts, APKs, or instructions to bypass platform controls.

## Pipeline
1. Collect and persist an attributed idea.
2. Analyze its subject and suggest affected ManaOS modules.
3. Classify the target, scope, and risk.
4. Prepare testable acceptance criteria.
5. Keep elevated-risk, security-critical, and system-wide proposals awaiting owner approval.

## Owner controls
- Self-development can be disabled from the MANA self-development screen.
- When disabled, new collection and analysis are blocked.
- The initial runtime does not execute or install code.
- Full system updates always require explicit owner approval.

## Current implementation boundary
Implemented in Stage 1:
- SharedPreferences-backed local idea and proposal storage.
- Idea-source attribution.
- Rule-based first-pass module/risk classification.
- MANA dashboard entry and self-development screen.
- Owner enable/disable switch.

Not yet implemented:
- Continuous background ingestion from each integration.
- AI-assisted source verification and deeper analysis.
- A trusted isolated source-code patch executor.
- Automated test execution for generated code.
- Signed update packaging, staged rollout, and rollback.
- Firmware replacement or Android system partition changes.

## Safety and release requirements
Any future autonomous minor patch executor must use an isolated branch/worktree, allowlisted source paths, deterministic tests, static/security checks, a signed build artifact, and rollback. It must not modify boot-chain/security policy, grant itself permissions, or run payloads collected from untrusted sources. A full system update must remain blocked until an explicit owner approval event is recorded.
