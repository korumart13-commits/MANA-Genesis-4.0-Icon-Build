# MANA Engine — Architecture Proposal

Status: design specification; implementation and device validation are still pending.
Target: MANA Genesis 4.0, Android 16 / realme RMX5566 (hardware details must be measured on-device).

## 1. Product principle

MANA is one assistant identity and one orchestration core. Models are replaceable inference providers, not separate personalities or autonomous agents. MANA owns the conversation state, user-approved memory, policy checks, tool routing, and final response.

## 2. Runtime layers

- **MANA Core**: Kotlin service/library exposing a stable internal API: `generate()`, `embed()`, `routeTool()`, `memorySearch()`, `healthCheck()`.
- **MANA Model Router**: selects a model by capability, latency, memory budget, battery/thermal state, connectivity, and user preference. It may fall back locally when offline.
- **Local Runtime Adapter**: first candidate LiteRT-LM (Android Kotlin API); keep the adapter interface independent so another runtime can be evaluated without rewriting MANA Core.
- **Local Model Packs**: selectable open-weight models with model-card, license, checksum, quantization, context length, RAM estimate, and device compatibility metadata. Evaluate Gemma, Qwen, Llama and Phi variants only where their exact model format, license, and runtime support are confirmed.
- **MANA Memory**: encrypted local conversation memory, user-approved durable facts, retrieval index, and explicit forget/export controls. Do not silently ingest contacts, messages, photos, or other private data.
- **MANA Tools**: Android capabilities exposed through narrow typed interfaces and Android permissions. Every tool declares required permission, risk level, and confirmation policy.
- **MANA Cloud Connector**: optional authenticated service for encrypted sync, update manifests, model metadata, and user-authorized backup. Local inference must remain available without it.

## 3. Local model strategy

LiteRT-LM is an inference runtime, not a model itself. Its current documentation lists support for Gemma, Llama, Qwen and Phi families, with CPU/GPU and selected NPU paths depending on SoC and model bundle. Use it as the first local runtime candidate, not as a hard-coded identity.

Initial evaluation set:
1. A compact instruction model for fast everyday requests.
2. A larger model for complex reasoning when available memory and thermal headroom permit.
3. A small tool/function-calling model or constrained tool parser, if benchmarks show it improves reliability.

Do not combine model weights into a fictional "mixture" or claim that a new foundation model has been trained. MANA Engine is a new orchestration/runtime product built around existing licensed models. A genuinely new foundation model would require training data, compute, evaluation, and a separate training project.

Before selecting a default model, collect on-device facts: SoC model, available RAM, Android ABI, GPU, NPU delegate support, free storage, sustained thermal performance, and actual tokens/second. Do not infer compatibility from the device marketing name or RAM expansion feature.

## 4. Single intelligence and provider contract

All providers implement one interface and return normalized messages/tool calls:
- Local LiteRT-LM adapter.
- Optional cloud adapters (OpenAI, Google Gemini, Anthropic, xAI) only when the user configures valid credentials and accepts provider terms/costs.
- The cloud adapters are APIs, not downloadable GPT/Claude/Gemini model weights. Never bundle proprietary model weights or expose provider secrets in the APK.
- MANA Core retains one system identity, memory policy, safety policy, and tool permission layer across providers. Providers do not independently execute Android actions.

## 5. Permission and action security

- Use Android runtime permission prompts and special-access settings screens as designed by Android.
- No malware behavior, hidden privilege escalation, permission bypass, covert collection, or persistence against the user's wishes.
- Apply least privilege. Request a permission only when a feature is enabled and needs it.
- Risk tiers: read-only; reversible change; external communication/purchase/deletion/security change. Require explicit confirmation for high-impact actions.
- Show foreground microphone/camera indicators and persistent notifications where Android requires them.
- Provide pause, revoke, disconnect, clear local memory, export data, and uninstall behavior.

## 6. Private MANA service

A private service is not a secret public website. It is a backend with:
- No public user registration or public content pages.
- Device enrollment initiated by the user.
- Per-install device key generated in Android Keystore; private key is non-exportable where supported.
- Short-lived signed access tokens; server-side authorization on every request.
- TLS, rate limits, audit events, key rotation, abuse controls, encrypted storage, backups, and deletion workflows.
- No universal embedded secret, no shared master password in the APK, and no claim that only MANA can connect unless cryptographic authentication enforces it.
- Admin/owner recovery path must exist so the user cannot be permanently locked out by a broken client.
- Cloud connector is optional; core local assistant remains functional offline.

## 7. GPT/cloud storage clarification

Do not store OpenAI GPT weights in MANA Cloud: proprietary hosted models are accessed through the official API. Store only MANA-owned configuration, encrypted user-authorized data, model manifests, and cacheable assets whose licenses permit storage. Provider API keys must be kept server-side or in secure user-controlled secret storage, never in public source code or a distributed APK.

## 8. Delivery milestones

1. Inventory existing Android modules and establish a clean Core API.
2. Add local-runtime adapter abstraction and LiteRT-LM dependency after verifying the official Android/Kotlin integration guide and license.
3. Build a model manager with checksum, storage estimate, download resume, model license display, and uninstall.
4. Add a local model and a deterministic benchmark/health screen.
5. Add memory, retrieval, and explicit privacy controls.
6. Add permission-gated tool registry and confirmation policy.
7. Add optional cloud provider adapters; do not block local mode on API credentials.
8. Implement private backend prototype and device enrollment.
9. Add security tests, offline tests, Android 16 compatibility tests, and real-device performance measurements.
10. Only then produce and label an APK as an integrated build.

## 9. Acceptance criteria

- Airplane mode: local chat and local memory work after model installation.
- Provider switching never changes MANA identity or permission policy.
- Invalid or missing credentials fail closed and do not disable local mode.
- A model is never loaded unless its checksum and declared format are verified.
- No Android tool can run outside its permission and confirmation contract.
- Backend rejects un-enrolled devices and invalid/expired signatures.
- UI clearly distinguishes implemented, unavailable, and experimental features.
