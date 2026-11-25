# MANA Genesis 4.0 — AI Provider and Google Review Plan

Status: planned; no provider integrations or Google approval are claimed by this document.

## 1. Multi-provider AI architecture

MANA must not be hard-wired to Gemma or to one vendor. Implement a provider abstraction with a common conversation/tool-calling interface and selectable routing:

- **OpenAI GPT** — primary cloud reasoning provider, through the official Responses API.
- **Anthropic Claude** — optional provider for long-form reasoning and agentic tasks.
- **Google Gemini** — optional provider through the official Gemini API.
- **xAI Grok** — optional provider through the official xAI API.
- **Local offline model** — LiteRT-LM runtime with a compatible downloadable model; remains available when there is no network.

Tesla vehicle/FSD neural-network technology is not treated as an available general-purpose Android LLM engine. Grok is an xAI service, not a Tesla app engine.

### Provider router requirements
1. Create a provider interface for text, image input where supported, streaming, conversation history, structured output, and tool/function calls.
2. Add a provider selector and an automatic routing mode. The user can choose cloud quality or local/offline mode.
3. Show the active provider, network state, and any estimated/actual usage cost.
4. Implement graceful fallback: cloud unavailable or user disables network => local model; local model unavailable => explain limitation instead of pretending to answer offline.
5. Keep MANA's persona, memory, permissions, and tool execution under MANA Core rather than inside a vendor model.
6. Cloud calls require the user's provider account, valid API credentials, internet access, and may incur charges. Do not embed secret API keys in the APK; use a secure backend/proxy or a secure user-provided credential flow.
7. Do not claim cloud models run locally. Clearly disclose which data is sent to each provider before enabling cloud mode.

## 2. Local model and runtime

- Integrate LiteRT-LM as the on-device inference runtime only after verifying Android compatibility and device performance.
- Treat Gemma as one possible model, not the only or mandatory choice.
- Add model download, checksum/signature verification, storage checks, cancellation, versioning, and offline startup.
- Benchmark on the target realme RMX5566 / Android 16 before selecting default model size. Do not infer performance from another phone's benchmark.
- Keep local memory and private data on-device by default; cloud sharing must be explicit and configurable.

## 3. Google Play review and safety/compliance work

Prepare a genuine submission package for Google Play review. This is a request for review, not a guarantee of approval or a Google-issued security certification.

Tasks:
1. Write and publish an accurate privacy policy describing microphone, camera, contacts, notifications, files, local memory, diagnostics, and any cloud AI providers actually used.
2. Complete the Play Console Data safety form accurately, including data collected/shared by SDKs and cloud AI providers.
3. Add clear in-app disclosures immediately before sensitive data access and request Android runtime/special permissions only when the related feature is used.
4. Provide permission rationale screens, revocation guidance, data deletion controls, retention rules, and secure transport/storage documentation.
5. Remove deceptive behavior, hidden collection, stealth persistence, permission bypasses, and any malware-like behavior.
6. Prepare reviewer instructions, test credentials if needed, a feature/permission matrix, screenshots, and a security test report.
7. Submit the app through the developer's Play Console account and respond to Google's review feedback. Only Google can decide approval; do not label the app “Google approved” or “certified safe” before official confirmation.

## 4. Acceptance criteria

- Each provider can be enabled/disabled independently.
- No API secret is hard-coded in the APK or public repository.
- The app clearly distinguishes local inference from cloud inference.
- Offline mode is tested with network disabled.
- Permission prompts are user-visible and follow Android/Google Play policy.
- Privacy disclosures match the app's actual data flows.
- Build, integration tests, and target-device tests pass before release.
