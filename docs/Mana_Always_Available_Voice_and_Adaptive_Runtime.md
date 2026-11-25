# ManaOS — Always-Available Voice, Command & Adaptive Runtime Contract
Version: 1.0.0
Status: implementation specification; not proof of implemented features.

## 1. Owner experience
The owner can invoke Mana at any time through supported, user-enabled entry points:
- In-app voice button and text chat.
- Launcher shortcut and configurable gesture.
- User-added Android Quick Settings tile or notification action where supported.
- Optional wake phrase / hotword only if a supported low-power detection service is available, microphone permission and Android background/foreground-service rules are satisfied, and the OS visibly indicates microphone use.
- Hardware button integration only where Android/OEM APIs permit it; never intercept emergency, power, or secure-system actions.
The owner can issue natural-language commands or speak ideas at any time. Mana should acknowledge, clarify ambiguous or risky instructions, show progress, and report completion or failure honestly.

## 2. Voice command and idea capture
- One command router handles conversational requests, device actions, reminders, and future-update proposals.
- Voice capture supports push-to-talk by default; optional wake phrase is separately enabled and easy to disable.
- Voice ideas are transcribed, shown for correction, then saved to the owner's Ideas & Future Updates inbox after confirmation or an explicitly configured auto-save rule.
- Every idea stores original audio only if the owner opts in; default is transcript-only and local encrypted storage.
- Distinguish an instruction to perform an action now from an idea/request for a future release.
- Risk levels: read-only; reversible local change; sensitive action; destructive/system-level action. Ask for explicit confirmation for sensitive, destructive, purchases, communications, permission changes, installs, or external sharing.
- Provide cancel/undo where possible and a visible action history.

## 3. Adaptive runtime and portability
Mana must adapt its own app behavior to the device, not assume one hardware profile:
- At first launch, detect Android API level, ABI/CPU features, available RAM/storage, screen size/density, GPU/graphics support, thermal/battery state where exposed, permissions, vendor restrictions, and installed TTS/STT providers.
- Build a capability profile with confidence/source and refresh it after OS updates or hardware changes.
- Select feature implementations by capability: high/medium/low graphics, local/cloud model, voice provider, storage strategy, animation complexity, and background execution mode.
- Provide a compatibility matrix and explain unavailable capabilities rather than pretending support.
- Keep core chat, settings, idea inbox and a lightweight avatar usable on constrained devices.
- Use modular downloadable packs (avatar model, outfits, motion packs, language/voice packs, optional local models) with size estimates, hashes, signatures, compatibility metadata, pause/resume and removal controls.
- Storage manager can offer cache cleanup, model/asset offloading, compression and user-approved deletion of app-owned data. Never silently delete user files or Android/system files.
- Memory manager uses bounded caches, streaming, paging and low-memory fallbacks. Never attempt to evade Android memory limits or kill other apps without an authorized OS role.
- Thermal/battery adaptation lowers render quality, frame rate, background work and model size; it must not conceal overheating or disable system thermal protection.
- Network adaptation chooses among configured Internet/Wi-Fi/LAN/hotspot/Bluetooth transports only for supported services. Bluetooth is for supported nearby links, not arbitrary Internet routing.

## 4. Self-development lifecycle
Mana may inspect and modify its own source code only within a controlled development pipeline:
1. Owner submits an idea or command.
2. Mana classifies scope, permissions, risks, compatibility and resource requirements.
3. It creates a proposal and isolated source-control branch/workspace.
4. It edits app-owned source or generates a plugin/module in a sandbox; no direct edits to the live installed binary.
5. Run unit tests, integration tests, Android lint, dependency/license review, static security checks, build and target-device compatibility tests.
6. Produce a signed candidate build and human-readable changelog, diff, permissions delta, resource impact and test report.
7. Owner explicitly approves installation. Android's package installer / Play distribution and signing rules remain authoritative.
8. Support backup, staged rollout where available, rollback to last known-good app release, and recovery instructions.
Mana cannot self-approve a release, change its own signing identity, extract signing keys, install silently as an ordinary app, bypass Play Protect, or grant itself new Android privileges.

## 5. Operating-system adaptation boundary
Mana may adapt through documented Android SDK APIs, supported roles, OEM APIs explicitly available to the app, and owner-approved system integration on devices where a legitimate system image/vendor build exists.
An ordinary APK cannot rewrite Android or realme UI, replace the kernel/SystemUI/boot animation, change protected OS files, remove built-in antivirus/biometrics/lock-screen protections, or become Device Owner/system app merely by declaring itself the phone's AI.
If future work targets a custom AOSP-based system image, it must be a separate device-specific engineering project with verified device tree, vendor blobs, kernel, boot/recovery images, AVB/signing, SELinux policy, partition map and tested rollback. No universal “adapt to any OS or hardware” guarantee is possible.
Keep Android's verified boot, SELinux, encryption, biometric APIs, lock screen, Play Protect and vendor thermal/security services intact. Mana can integrate with them, not silently replace or delete them.

## 6. Security and user control
- Owner identity is an in-app role, not a root/system privilege.
- Least privilege; request permissions just in time with purpose and revocation instructions.
- No hidden microphone recording, no unsolicited overlay, no background persistence against user choice.
- No arbitrary shell/root commands, privilege escalation, security bypass, or code downloaded and executed without signature and policy validation.
- Local encrypted storage for private ideas and memory; cloud sync off until separately configured and approved.
- Pairing uses short-lived one-time codes, per-device revocable credentials and encrypted transport.
- All updates and plugins are signed and integrity-checked. No embedded API secrets or private keys.
- User can pause self-development, disable proactive voice, inspect pending actions, revoke permissions, export/delete ideas and roll back app updates.

## 7. Required UX
- Global “Call Mana” entry point in the launcher and app.
- Voice orb/overlay appears only after explicit setup and Android overlay approval; always draggable, dismissible and visibly active.
- A universal command composer accepts text or voice.
- Ideas inbox has “Add by voice”, “Add text”, “Ask Mana to plan”, status and owner approval.
- Adaptive dashboard shows detected device profile, chosen performance mode, storage use, unavailable capabilities and why.
- Self-development dashboard shows proposal, code diff summary, test results, permissions changes, estimated download size and approval controls.
- No fake “one tap grant all” or “one tap replace Android” screen; Android permission/system confirmation screens remain in control.

## 8. Acceptance tests
1. Owner can summon Mana from each enabled entry point and dismiss it.
2. Voice command works with permission; denial gracefully falls back to text.
3. Optional wake phrase clearly reports background microphone behavior and can be disabled.
4. Spoken idea is transcribed, confirmed and saved to the Ideas inbox; it remains available after app restart.
5. Device profile detects capabilities and selects an appropriate avatar/render/model tier.
6. Low-storage and low-memory states trigger safe app-owned cache/offload recommendations, never silent deletion of user/system files.
7. Thermal/battery pressure reduces workload and reports the reason.
8. Self-development creates an isolated candidate, runs checks, shows diff and requires owner approval before update.
9. Invalid signatures, incompatible packages and failed tests are rejected; last known-good version remains recoverable.
10. Confirm Android/realme protections remain enabled and no unauthorized OS modifications occur.

## 9. Explicit non-goals
- Universal compatibility with every phone, RAM size, Android fork, kernel or operating system.
- Unrestricted self-modifying live code or self-granted permissions.
- Deleting/replacing Android security, biometric or lock-screen components.
- Arbitrary movement generation without a compatible rig/animation controller; motions require valid authored, captured or procedurally validated motion assets.
- Always-listening microphone without explicit opt-in and platform-compliant indication.
