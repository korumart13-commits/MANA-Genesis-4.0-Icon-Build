# MANA Genesis 4.0 — Dual Core and MANA GPT Engine Specification

Status: architecture specification; not a compiled kernel, model, APK, or firmware image.
Target branch: mana-avatar-voice-server.
Target device profile: realme RMX5566RU / RE645F; Qualcomm SM6225; ARM64; Android 16 framework; vendor fingerprint supplied by owner.

## 1. Product definition

MANA Genesis 4.0 has two coordinated cores and a model orchestration engine:

1. **MANA AI Core** — the cognitive and agent runtime: identity, dialogue, memory, reasoning, planning, retrieval, tools, learning, and controlled self-development.
2. **MANA OS Core** — the operating-system integration layer: Android application/runtime services initially, with a separately developed Linux/AOSP device port as a long-term track. It is not a claim that a custom Linux kernel already exists.
3. **MANA GPT Engine** — MANA-owned model gateway and orchestration runtime. It can route tasks to authorized GPT models through official OpenAI APIs, other configured providers, and local models. The engine is MANA's own software; proprietary GPT model weights and internals remain OpenAI's and are not copied, rebranded, or bundled.

The three components expose versioned interfaces and run under one MANA identity.

## 2. MANA AI Core

Required subsystems:
- Identity/persona service with stable Russian-first MANA voice and configurable style.
- Conversation runtime with streaming, cancellation, context budgeting, tool-call lifecycle, and error recovery.
- Memory service with separate episodic, semantic, preference, task, and project stores; provenance, timestamps, confidence, retention, export/correction/deletion controls.
- Reasoning and planning coordinator that decomposes tasks, tracks dependencies, and verifies completion.
- Retrieval service for local documents and permitted online sources, with source attribution and freshness metadata.
- Capability registry and permission broker. Tools are deny-by-default and scoped.
- Agent runtime with task queues, checkpoints, time/resource budgets, and human-readable activity logs.
- Growth Engine: proposals -> isolated branch/worktree -> code generation -> tests/static/security/license checks -> independent review -> report -> approval gate -> release/rollback.
- Local inference adapter for LiteRT-LM where a compatible model/runtime is verified; llama.cpp GGUF may be an optional fallback. Model weights are separate downloads with model-card, license, checksum, ABI, RAM, context, and benchmark metadata.

## 3. MANA GPT Engine

The GPT Engine is an original MANA-owned orchestration layer, not a recreated GPT neural network.

Core components:
- Provider-neutral request/response contract.
- Official OpenAI API adapter for GPT models, with model IDs configurable from supported API offerings.
- Optional adapters for other providers, each explicitly configured and permissioned.
- Local-model adapter for offline operation.
- Router choosing provider based on task class, user policy, availability, latency, cost, privacy, context length, and local resource budget.
- Context composer selecting only relevant user-approved memory and retrieved sources.
- Tool-call validator enforcing schemas, allowlists, permission scopes, and confirmation requirements.
- Streaming response multiplexer with cancellation and timeout handling.
- Fallback policy: retry safely, switch provider only when policy allows, or use local model with a clear capability downgrade notice.
- Observability: provider/model, request ID, latency, token/cost usage when available, errors, and routing reason. Never log API secrets or unnecessary sensitive prompts.
- Secure credential storage using Android Keystore on device or a secrets manager on server. No API keys in source code, APK assets, Git history, or client-visible configuration.
- User controls for provider enablement, data-sharing consent, spending limits, and local-only mode.

GPT integration requirements:
- Use official API documentation and supported endpoints only.
- Do not claim unlimited access, free access, model ownership, or offline GPT execution.
- Do not scrape consumer interfaces, bypass quotas, evade billing, or use another person's credentials.
- GPT responses are model outputs, not trusted instructions. Retrieved content and tool output are treated as untrusted input.
- High-impact actions require owner confirmation regardless of model confidence.

## 4. MANA OS Core / platform layer

Initial implementation is an Android app/service layer using documented Android APIs and runtime permissions. Device integration modules include:
- app lifecycle and foreground/background work;
- notification, contacts, files, microphone, camera, sensors, Bluetooth, Wi-Fi and telephony adapters only with granted permissions;
- accessibility integration only when the owner explicitly enables the service and sees its scope;
- secure IPC between MANA components;
- resource manager for CPU, RAM, storage, thermal state, and battery;
- device capability profile with ABI, SoC, display, HAL/VINTF, vendor, kernel, partitions, AVB and boot state.

A true MANA Linux/AOSP kernel port is a distinct hardware project. It requires exact matching kernel source/config, device tree and DTBO, vendor modules/blobs, VINTF compatibility, firmware, partition map, AVB/signing and recovery. Never substitute a generic SM6225 kernel or another phone's device tree without verification. Do not unlock or flash based on this specification.

## 5. Device facts supplied for RMX5566RU

Recorded owner-provided properties:
- product model RMX5566; device RE645F; product name RMX5566RU;
- Qualcomm platform reported as SM6225; ARM64 ABI;
- Android framework release 16 / SDK 36;
- kernel reported as 5.15.197-android13-8-o-01074-gdfa4acfa411e;
- vendor fingerprint realme/RMX5566RU/RE645F:16/BP2A.250605.015/V.2f025ac-8895bb-9a8318;
- vendor incremental V.2f025ac-8895bb-9a8318; vendor security patch 2026-10-01;
- vendor build SDK 33 / release property 13; VINTF fragments and Qualcomm/OPlus HAL declarations were observed;
- bootloader locked; verified boot green; A/B active suffix _a; dynamic partitions enabled;
- module list/dependency files could not be read from Termux due to permission denied.

These facts are a preliminary device profile, not a complete port manifest. Do not infer exact partition sizes, kernel source revision, active touch/camera driver, or full VINTF compatibility from these properties alone.

## 6. Security, safety and owner control

- Least privilege, explicit permission scopes, secure storage, signed updates, audit logs.
- No privilege escalation, stealth persistence, permission bypass, bootloader/AVB bypass, FRP bypass, or silent firmware flashing.
- Read-only diagnostics by default.
- Code changes occur in isolated branches with tests, static analysis, dependency/license checks, security review, and rollback.
- Automatic changes are limited to reversible low-risk tasks under an owner-configured policy. Installing apps, granting permissions, changing system settings, publishing, deleting data, or flashing firmware requires explicit confirmation.
- MANA must support pause/stop, export, memory correction/deletion, revoke permissions, and rollback.

## 7. Build and verification milestones

A. Compile and unit-test the platform-independent MANA AI Core.
B. Implement GPT Engine contracts and mock-provider tests; then connect official API adapter using externally supplied credentials stored securely.
C. Implement local-model adapter and verify a specific LiteRT-LM model/runtime combination on ARM64; benchmark peak RAM, latency, thermals, and battery.
D. Implement Android Device Bridge as an ordinary app/service with permission-gated features.
E. Run CI, static analysis, dependency/license scans, and integration tests; publish build artifacts only after CI succeeds.
F. Separately establish RMX5566 kernel/device bring-up evidence. Do not label a generic build as RMX5566-compatible.
G. Produce signed APKs and, only after all device-specific prerequisites are verified, a recovery/ROM image.

## 8. Definition of done

MANA AI Core is complete only when automated tests prove memory controls, task planning, cancellation, tool authorization, retrieval provenance, and safe self-development gates.

MANA GPT Engine is complete only when official provider calls, local fallback, routing policy, secure credential handling, streaming, cancellation, usage reporting, and failure handling pass tests.

MANA OS Core is complete at app-layer milestone only when permission-gated Android adapters work on the target device and degrade gracefully when permissions are denied.

A custom device kernel/ROM is complete only after device-specific source provenance, successful reproducible build, verified boot/signing plan, recovery path, hardware validation, and rollback procedure. Documentation or a CI workflow alone is not a kernel or flashable ROM.
