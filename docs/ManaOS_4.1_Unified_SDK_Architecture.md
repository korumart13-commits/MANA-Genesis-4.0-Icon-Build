# ManaOS 4.1 — Unified SDK and Product Architecture

Status: implementation specification; NOT proof that these capabilities are already implemented.
Product name: ManaOS
Assistant identity: Mana
Primary target: realme RMX5566 / realme C85 5G, Android 16 (device details supplied by owner)
SDK contract version: 4.1.0

## 1. Product principle

Mana is the single user-facing intelligence and assistant identity of the phone. The avatar is Mana's primary embodied interface, not a decorative extra. Android and realme UI remain the underlying operating system/platform services. Mana may integrate with supported Android APIs and gradually gain additional capabilities through explicit permissions, tested app updates, and (only where supported by the device vendor) later system-level integration. Mana must never claim to be the literal consciousness of the hardware, silently take control, bypass Android security, or self-grant privileges.

Brand and UI name: ManaOS. Do not show MANA Genesis as the user-facing product name except in developer/build metadata.

## 2. Unified module map

All modules share one Mana Core SDK, one identity/profile, one permission broker, one event bus, one local encrypted data layer, one update manager, and one design system.

### Core and intelligence
- Mana Core: lifecycle, module registry, capability discovery, event routing, settings, health checks.
- Mana Brain: conversation orchestration, context selection, tool routing, response streaming, model/provider selection.
- Mana Chat: first-class chat screen, conversation history, attachments where supported, voice input/output, stop/regenerate, offline-state indicator.
- Mana Memory: user-controlled local memory, inspect/export/delete controls, retention policy; sync disabled until separately approved.
- Mana Adaptive Intelligence: source-aware web/API retrieval when configured, provenance, confidence, local knowledge index, scheduled learning only with visible controls and resource limits.
- Mana Self-Development: idea inbox, roadmap, propose → implement in isolated branch/build → tests/security checks → owner approval → signed release. No autonomous production code replacement, no silent self-modification, no privilege escalation.
- Mana Model Hub: provider abstraction for on-device models and HTTPS cloud inference; no embedded API secrets; user can select provider and see connection status.

### Avatar and interaction
- Mana Avatar: persistent character identity; animated 2D/3D presentation, expressions, lip sync when assets/engine are available, outfit/accessory customization without changing identity.
- Avatar Launcher: summon/dismiss floating avatar from a visible app control, launcher shortcut, quick-settings tile/notification action where Android permits.
- Floating overlay is optional and requires Android's special “Display over other apps” approval. Never show unsolicited overlays at arbitrary times; provide quiet hours, trigger rules, emergency dismiss, and an always-visible indicator when active.
- Voice: speech recognition and text-to-speech through user-approved Android services/providers; microphone use only while clearly indicated.

### Phone experience
- Mana Launcher: modern, youthful, accessible home experience; adaptive cards, search, dock, widgets/shortcuts, coherent dark-navy visual language, large touch targets, motion reduced setting.
- Mana System UI companion: in-app quick controls and supported Android notification/quick-settings integrations; cannot replace privileged Android SystemUI as an ordinary APK.
- Mana Settings and Control Center: module controls, permissions status, battery/network state, privacy controls.
- Mana First Run: guided setup with a single checklist screen that explains all required capabilities. Each Android runtime/special permission must still be approved in the OS's own dialog/settings page; never misrepresent this as one universal grant.
- Overlay permission: dedicated explanation and button opening the official Android special-access settings screen.
- Install/update permission: only if the user chooses in-app APK updates; direct to Android “Install unknown apps” for this app and explain source verification. Prefer Play Store or verified signed update channel.
- Reboot: show a reboot instruction/action only where the device grants an authorized API. A normal third-party app cannot silently reboot Android; otherwise provide clear manual steps. Never request root as a shortcut.
- Mana Developer Center: owner role, device status, module diagnostics, logs scrubbed of secrets, backup/export, trusted-device management, build/version info.

### Personal and connected modules
- Smart Receptionist: optional call screening/answering only where default dialer/call-screening role and regional Android APIs permit; playful personality, 10-second configured delay, summaries enabled, unknown-number handling enabled only after permissions and user confirmation.
- Contact Intelligence: permission-gated contact context; no uploading address books by default.
- Life Manager: reminders, tasks, routines, calendar integration only with consent.
- Mana Diary: private local journal, encrypted at rest, explicit export/delete.
- Yandex and VK integrations: official APIs/OAuth and provider terms; no scraping, no embedded credentials.
- Game Intelligence: optional companion/advice and supported game integrations; no claims of universal control of games or inaccessible game memory.
- Wearables, earbuds and car: standard Bluetooth profiles/Android APIs; device-specific compatibility checks.

### Cloud and connectivity
- Mana Cloud Gateway: optional HTTPS service for inference, model/resource catalog, backups and signed update metadata. Cloud is a separate deployable service and must expose health/version endpoints.
- The Android app contains a configurable service URL/domain and environment configuration, not guessed fixed IP addresses. Prefer a stable domain with TLS and DNS; IPs can change. No real cloud hostname, IP, API endpoint, or deployed public server has been supplied in the project history. Do not fabricate one or pretend cloud is connected.
- Until the owner supplies/deploys a domain and service, show “Cloud not configured” and keep local features usable.
- Transports: Internet/Wi-Fi HTTPS; local Wi-Fi/LAN; phone hotspot/local tethering where Android networking permits; Bluetooth only for supported nearby-device control/data channels and small payloads. Bluetooth is not a substitute for an IP cloud connection. Provide transport discovery, pairing, per-device identity, encryption, and explicit approval.
- Local server off by default, LAN-only, manually started, foreground notification while active, no UPnP/port forwarding/public listener.
- Device pairing: short-lived one-time QR/code, owner approval, individual revocable credentials, minimum scopes, TLS/pinned identity where appropriate. Memory sync off by default.

## 3. Permission and setup experience

Provide one Mana Setup dashboard with a clear progress checklist and “Set up next permission” action. Do not promise one Android dialog can grant everything. Permissions are requested just-in-time and individually:
1. Notifications (if needed).
2. Microphone (voice).
3. Contacts/call-related roles only if user enables receptionist/contact features.
4. Bluetooth / nearby devices (for device connectivity).
5. Network access is governed by Android manifest and OS; no runtime grant for ordinary Internet.
6. Display over other apps via Android special app access.
7. Install unknown apps via Android special app access only if user opts into APK updates.
8. Default assistant/dialer roles only through official role request flows where available.
9. Battery/background behavior via supported Android settings; never suppress OS warnings.
10. Reboot/system-level capabilities are unavailable to ordinary apps unless OEM/system privileges explicitly exist.

Every item must show purpose, data used, consequence, how to revoke, and current status. Denial must not break unrelated features.

## 4. Update and recovery model

- Update manifest is signed; APK/AAB signatures verified; rollback protection and staged rollout.
- Update download does not equal installation: Android user confirmation and signature compatibility are required.
- Self-development can create proposals, tests, changelogs and candidate builds, but release installation requires owner approval and platform security checks.
- Never store signing keystores, API keys, cloud secrets or pairing private keys in source control, APK assets or logs.
- Recovery is an in-app diagnostics/export/reset guide unless a separately built and device-validated recovery/firmware package exists. Do not claim a normal APK is firmware.

## 5. Visual design system

- Product label: ManaOS; assistant: Mana.
- Dark navy base, clean contemporary cards, restrained blue/cyan accents, readable typography, clear hierarchy, generous spacing, responsive layouts, accessible contrast, haptics/motion only when supported.
- Replace legacy desktop-like panels with mobile-first navigation: Home, Mana (avatar/chat), Modules, Devices, Settings.
- Home immediately exposes “Talk to Mana” and “Summon Avatar”.
- Do not use fake controls: every visible button must work or be explicitly marked “planned”.

## 6. SDK contracts

Core interfaces (illustrative contract; implementations and Android adapters are separate source files):
- ManaModule: id, version, requiredCapabilities, initialize(context), health(), shutdown()
- ManaAssistant: sendMessage(conversationId, text, attachments), streamResponse(), cancel()
- ManaAvatarController: show(trigger), hide(), setExpression(), setVoiceState(), setVisibilityPolicy()
- ManaPermissionBroker: status(capability), explain(capability), request(capability), revokeGuide(capability)
- ManaTransport: discover(), pair(), connect(), send(encryptedPayload), disconnect()
- ManaCloudProvider: health(), chat(), fetchCatalog(), fetchSignedUpdateManifest()
- ManaUpdateManager: check(), verify(), stage(), requestOwnerApproval(), installThroughSupportedPlatform()
- ManaMemoryStore: query(), saveWithConsent(), export(), delete()
- ManaSelfDevelopment: submitIdea(), plan(), buildSandboxCandidate(), runChecks(), requestReleaseApproval()

All APIs must return typed success/error states, expose permission/network failures, and avoid blocking the UI thread. Secrets are supplied at runtime from secure storage, never constants.

## 7. Acceptance gates

A release is not complete until:
- The avatar can be summoned and dismissed reliably on the target phone, with overlay permission behavior tested.
- Chat sends a message to a configured provider and displays real response/error states.
- Offline mode and cloud-not-configured states are honest and usable.
- Every permission is explained and denial is handled gracefully.
- Wi-Fi/LAN, hotspot and Bluetooth features are individually tested on supported devices; unsupported modes are clearly reported.
- Update signature, owner approval, rollback and data migration tests pass.
- Unit tests, Android build, lint/security checks and on-device smoke tests pass.
- Release notes distinguish implemented, experimental and planned capabilities.

## 8. Current known gaps — do not conceal

The existing GitHub repository has source ZIP archives rather than a fully expanded, verified Android source tree in the build repository. A previous successful debug build does not prove the avatar, chat, cloud, floating overlay, multi-transport, self-development or system integration are implemented. No cloud domain/IP/API endpoint has been deployed or supplied. The previously generated square avatar image is not available in this repository context; request the owner to upload that exact image before using it as the launcher icon. Do not substitute a newly invented avatar without approval.

This document is the unified SDK/product contract for implementation. It is not itself an SDK binary, APK, firmware image, or completed feature set.
