# MANA Genesis 4.3 — Product and Runtime Implementation Contract

Status: implementation contract; not a claim that these capabilities are already implemented.
Target: Android 16, realme RMX5566 / realme C85 5G (device compatibility remains to be tested).
Product: one installable APK that provides the MANA home/launcher experience and one integrated assistant identity.

## 1. Product definition

MANA is not a dashboard of unrelated demo buttons. The installed app must open directly into a coherent MANA home experience:
- navy / electric-blue visual system, Android-first layout;
- persistent home surface with app shortcuts, widgets, quick controls and settings;
- one persistent assistant identity, conversation history and memory;
- an animated original 3D avatar as the primary assistant presence;
- voice and text conversation;
- one module registry and one permission-aware action broker shared by every feature.

The app must not present an empty setup dashboard, a Google Play link, or a field asking the owner to type a server URL.

## 2. First-launch experience

1. Launch into the MANA home screen, not a configuration form.
2. Show the avatar and a clear conversation entry point immediately.
3. Load bundled safe defaults and local features before attempting network access.
4. Connect automatically to the preconfigured HTTPS gateway when a real deployment endpoint is supplied at build time.
5. Display truthful connection state: online, offline, connecting, or authentication required.
6. Ask Android for each sensitive permission only when its feature is first used, with a short explanation and a graceful decline path.
7. Offer the Android HOME-role request as a system-mediated action; never claim the app silently became the default launcher.

## 3. Unified assistant runtime

All modules use the same interfaces:
- ManaBrain: intent understanding, planning, tool selection and response composition.
- ManaMemory: local encrypted conversation/preferences store with export and clear controls.
- ManaAvatarRuntime: avatar state, expression, gaze, animation and speech synchronization.
- ManaVoiceRuntime: Android speech recognition and TTS adapter, with optional licensed cloud voice.
- ManaToolBroker: typed tools with explicit permission checks, confirmation policies and audit log.
- ManaModuleRegistry: capability discovery and lifecycle for Launcher, Device, Diary, Life Manager, Receptionist, Games, Learning, Developer and Updates.
- ManaNetworkGateway: HTTPS API client, auth/session refresh, timeouts, retry/backoff and offline fallback.
- ManaUpdateManager: signed update metadata, integrity verification, staged rollout and owner approval.

No module may create a second assistant persona or maintain an isolated memory.

## 4. Cloud chat and ChatHub integration

A chat client already exists in the Android source and targets the OpenAI-compatible `/v1/chat/completions` route. This is only a client contract; it does not prove that a provider, endpoint, account, API key, or server has been configured.

The earlier discussion called the existing chat service “ChatHub”, but no verified ChatHub deployment URL, provider, credentials, or authentication details were recorded. Do not guess these values and do not embed a private API key in the APK.

Required implementation:
- compile the real gateway base URL into a build configuration value after deployment is verified;
- keep provider secrets on the server, never in the APK or Git;
- use short-lived user/device sessions and TLS;
- make first launch connect without asking the owner to enter an address;
- preserve local chat and offline features when the gateway is unreachable;
- never display “connected” until a real authenticated request succeeds.

Until a real endpoint is supplied/deployed and verified, the APK must clearly identify cloud chat as not configured rather than simulating a connection.

## 5. Launcher and device control boundaries

The launcher may provide a home screen, shortcuts, widgets where supported, app discovery, wallpaper/theme controls, and system settings shortcuts. It may request the HOME role through Android's official role flow.

Device actions must use public Android APIs and the narrowest permissions available. Android may block silent changes or removal of protected system packages. For protected apps, MANA can explain the restriction and open the relevant system settings; it must not exploit root, accessibility abuse, hidden APIs, or security bypasses.

Changing the complete system UI, status bar, lock screen, navigation behavior or privileged SystemUI requires OS/OEM-level integration and cannot be promised by an ordinary APK.

## 6. Avatar and identity

Use an original or properly licensed VRM 1.0 / GLB / glTF character, not a ripped game model or copied actor voice. Keep face, body proportions, identity and voice consistent; allow clothing/accessory changes. Minimum milestones:
- bundled avatar asset and renderer;
- idle loop, blink, gaze and a small expression set;
- speech playback with approximate lip-sync;
- later: richer facial rig, gesture and emotion animation.

If a model asset is not present, do not ship a fake avatar placeholder while describing it as a live 3D character.

## 7. Self-development and learning

The learning pipeline may collect owner ideas, opted-in diagnostics, public web knowledge, supported app/game observations and error reports. It must:
1. deduplicate and classify;
2. score usefulness, feasibility, privacy impact and risk;
3. draft a plan;
4. create code changes in an isolated branch/sandbox;
5. run tests, static analysis, dependency/license and security checks;
6. produce a reviewable candidate and changelog;
7. request owner approval before release or installation.

Learning is not unrestricted copying. For video, MANA may analyze user-provided or legally accessible material where platform terms and copyright permit; it must not download/repost protected videos or copy proprietary assets without rights. No silent self-install, self-granting permissions, privilege escalation, or modification of Android security.

## 8. Games and Minecraft

Minecraft co-play is a separate integration milestone. Use a supported bot framework, server plugin, mod/API or documented protocol that is compatible with the selected Minecraft edition and server rules. The owner plays normally; MANA joins as a separate bot identity when the server permits it. Do not claim game control or learning works until an end-to-end test demonstrates it.

## 9. Security and owner authority

- Preserve Play Protect, Verified Boot, SELinux and Android security services.
- No root requirement and no security bypass.
- Sensitive actions (uninstall, data deletion, account changes, purchases, publishing, system changes) require explicit confirmation and Android authorization.
- Local LAN server is off by default; when enabled, it is foreground-visible, LAN-only and paired by owner command.
- No passwords, PINs, OTPs or private provider keys in chat, source code, APK assets or logs.
- Keep an owner-visible action history and revoke device sessions.

## 10. Build and release gates

A CI build is not proof of device compatibility. Release gates:
- reproducible Gradle build and unit tests;
- APK manifest, package ID, minSdk/targetSdk and ABI inspection;
- stable signing key managed outside the repository;
- install/update test on a clean Android 16 device;
- launcher HOME-role test;
- offline/online gateway tests;
- permission-denial tests;
- avatar rendering and performance/thermal test on RMX5566;
- no placeholder controls presented as working features.

Debug builds must not rotate signing certificates between releases. Production signing material must be stored in protected CI secrets and never committed.

## 11. Delivery sequence

A. Real home/launcher UI and navigation, not the current temporary text dashboard.
B. Stable local ManaBrain shell, chat UI, memory, voice adapter and permission broker.
C. Bundled original avatar and animation runtime.
D. Verified ChatHub/cloud gateway deployment and automatic connection.
E. Device actions through public Android APIs and launcher role.
F. Self-development sandbox, tests and owner-reviewed updates.
G. Minecraft bot integration and game-learning module.
H. Further supported integrations (Yandex/VK, wearables, car and receptionist).

Each milestone must have a working build and test evidence. Do not describe later milestones as completed before implementation and device testing.
