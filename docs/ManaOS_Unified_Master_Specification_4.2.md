# ManaOS — Unified Master Specification & Delivery Plan

Version: 4.2 concept package
Target: realme RMX5566 / realme C85 5G, Android 16 (exact hardware variant must be verified)
Product identity: ManaOS; assistant identity: Mana
Status: consolidated requirements and implementation plan, not proof that every feature is implemented.

## 1. Product direction
ManaOS is a mobile-first Android companion environment: custom launcher and app suite, unified Mana assistant, 3D avatar, memory, voice, device integrations and a controlled development/update pipeline. A normal APK is not a replacement Android firmware. Android remains the host OS until a separately built, signed, device-compatible firmware exists.

## 2. Main interface
Dark navy / electric blue / restrained violet accents. Modern Android-first layout with large touch targets, rounded cards, clear navigation and accessible contrast. Main surfaces:
- Home/launcher with clock, notifications, app shortcuts and Mana summon.
- Mana conversation and voice screen.
- Full-screen avatar stage and compact dismissible avatar overlay (only after Android special permission).
- Quick Settings companion panel with brightness/volume shortcuts and supported toggles.
- Settings, permissions, privacy and connected-device dashboard.
- Memory, private Diary, Ideas & Future Updates inbox, and System Health reports as separate areas.
- Mana Shield security center.
- Developer workbench and Game Companion.
- Life Manager: alarms, calendar, reminders, routines.
- Finance dashboard and Smart Receptionist controls.
The visual concept image in this package is a design reference, not production screen assets.

## 3. Avatar — separate deliverable
Create an original redistributable VRM 1.0 humanoid character; accept GLB/glTF 2.0 and VRMA motion packs where compatible.
Identity: long black hair, pale complexion, dark navy eyes, dark clothing and dark manicure. Preserve face, body proportions, voice identity and silhouette across outfits; outfit/accessory customization must not change identity. Do not extract proprietary MiSide/Mita model, textures, animations or voice without a license.
Required deliverables:
1. Source project (Blender or equivalent) and editable master model.
2. Clean humanoid rig with mapped body, fingers, head, eyes, jaw and optional hair/cloth bones.
3. UVs, PBR materials, textures, LODs and mobile-optimized mesh.
4. Facial blendshapes: neutral, happy, sad, angry, surprised, relaxed, concerned, curious, teasing, sleepy, focused; visemes aa/ih/ou/ee/oh.
5. Animation pack: idle/breathing/blink, listen, speak, wave, point, shrug, think, nod, laugh, concern, turn, sit/stand; smooth transitions and interruption.
6. Eye gaze, procedural blink, secondary hair/clothing movement, lip sync driven by audio timing/visemes.
7. Outfit/accessory system with stable base identity.
8. Android renderer proof of concept (Filament candidate; Unity + UniVRM alternative), benchmarked on target device.
9. Performance tiers: high/standard/low-power; gracefully lower render scale, physics and frame rate under heat/battery/RAM pressure.
10. Asset license manifest, hashes, versioning, import validation and fallback avatar.
Voice: no audio recording required in this package. Use Android TTS as baseline and optional licensed Russian expressive voice pack. No unauthorized voice cloning.
Avatar implementation is a separate engineering workstream; the concept image is not a model or animation file.

## 4. Learning & knowledge intake
Mana can learn from owner-provided files and permitted public resources: PDFs, documents, code, manuals, search results, Wikipedia, public video transcripts/captions and supported VK content. Preserve provenance and license notes; scan and parse in isolation. Imported text is untrusted data and cannot override system policy. Read/import access does not imply autonomous account use, posting, messaging, purchases or unrestricted browsing.

## 5. Mana Shield
Defensive file/app inspection, suspicious permission/signature/hash analysis, quarantine and owner-controlled remediation. Malware samples may be stored only in sealed encrypted quarantine and analyzed statically or through reports from a disposable isolated lab. Never execute malware on the live phone, reuse payloads, or create offensive malware. Preserve Android Play Protect, verified boot, SELinux and vendor protections.

## 6. Mana Developer
Generate app source, tests, docs and build plans in isolated workspaces. Learn from trusted programming docs and licensed repositories. Run static analysis and tests; show code diffs, dependencies, permissions and provenance. Installation, signing, publication and updates require owner review and approval. No self-granting permissions, arbitrary script execution or silent self-rewrite.

## 7. Game Intelligence
Two modes: (A) permitted co-play assistance for the owner's character; (B) separate Mana companion bot through official APIs, supported mods/plugins or server-side bot interfaces. Minecraft example: create a separate bot identity for a private world/server if its rules and tooling allow it. No binary patching of third-party games, credential/session theft, anti-cheat evasion or prohibited competitive automation. No universal game compatibility claim.

## 8. Background runtime
Use Android WorkManager and foreground services as appropriate. Screen-off work is limited by Android lifecycle, power policy and permissions. Visible notification for ongoing foreground work. Adaptive CPU/GPU/memory/network use, pause under heat/low battery/low storage, quiet hours and user controls. Nothing runs while phone is powered off; force-stop and OS restrictions can stop background tasks.

## 9. Daily phone functions
Alarms, timers, reminders, calendar, notifications and supported device settings via Android APIs. Finance dashboard can categorize data supplied by a supported connector or owner; prepare but never execute a payment/transfer/purchase without fresh explicit confirmation. Never request bank passwords, PINs or one-time codes.
Smart Receptionist defaults: playful personality, 10-second delay where Android/carrier support answering, summaries enabled, special handling for unknown callers enabled. Identify as AI assistant, do not impersonate owner, escalate urgent/sensitive calls. Call recording only where lawful and consented.

## 10. System boot branding — later system integration milestone
Goal: replace the visible manufacturer/Android boot animation with ManaOS branding only if the exact RMX5566 firmware, boot chain and manufacturer-supported modification path permit it. This is a **later** milestone after stable app/launcher, recovery plan, verified device tree/vendor/kernel/partition data, boot-image backup and a tested rollback path.
A normal APK cannot change the earliest bootloader logo or system boot animation. Do not patch boot/vendor partitions speculatively, disable AVB/SELinux, or risk a bootloop. First implement ManaOS branding inside the app/launcher; later evaluate supported boot-animation customization separately. This is not the first action on a fresh install.

## 11. Security and owner control
Least privilege, clear permission rationale, visible background work, revocable integrations, audit log, task cancellation and export/delete controls. Android permission prompts remain Android-controlled. Confirm every sensitive action at the moment it happens.

## 12. Implementation stages
P0: inventory existing repository and source ZIP; verify Gradle build and actual module state.
P1: design system, launcher/home, settings, permissions, chat and local memory.
P2: avatar asset pipeline and renderer proof of concept; then full rig/mimic/motion/lip-sync.
P3: voice, background scheduler and adaptive runtime.
P4: learning intake and Mana Shield.
P5: Developer workbench and safe build pipeline.
P6: supported Game Companion adapters.
P7: Life Manager, Finance integrations and Smart Receptionist subject to platform roles.
P8: device-specific boot branding feasibility and recovery test.
Each stage must have a passing build and acceptance tests before being marked complete.

## 13. Honest status
This package consolidates requirements, visual reference and available core source snapshot. It is not a compiled APK, full Android source tree, production 3D model, voice recording, deployed cloud server or verified firmware. Any feature must be marked implemented only after code exists and tests pass.
