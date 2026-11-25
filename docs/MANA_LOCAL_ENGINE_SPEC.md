# MANA Local Engine — Architecture and Device-Fit Plan

**Project:** MANA Genesis 4.0  
**Mode:** local-first / offline-capable  
**Cloud GPT:** explicitly out of scope for this phase  
**Status:** architecture specification; not yet an integrated or device-tested runtime.

## 1. Goal

Build one MANA assistant whose identity, conversation state, memory, tools, and personality are owned by the MANA Core. Local language models and runtimes are interchangeable inference backends, not separate assistants.

The first release must:
- start and answer without an internet connection after the required model pack is installed;
- keep conversation memory and user data in app-private local storage by default;
- run only one primary generative model at a time on a memory-constrained phone;
- select a model only after checking device RAM, free storage, ABI, runtime compatibility, and a real benchmark;
- degrade gracefully to a smaller model or non-generative offline functions if a model cannot be loaded.

## 2. Runtime strategy

Use adapters behind one Kotlin interface. Do not bind MANA Core directly to one vendor runtime.

### Primary candidate: LiteRT-LM
- Intended for compatible .litertlm model bundles.
- Evaluate CPU and GPU backends on the actual phone.
- Treat NPU as optional and enable it only if the exact chipset, driver, model bundle, and runtime build are confirmed compatible.
- LiteRT-LM is an inference runtime, not a language model. Model weights and their licences are separate.

### Compatibility fallback: llama.cpp
- Intended for compatible quantized GGUF models.
- Android arm64-v8a support is documented by the upstream project.
- Begin with CPU inference; evaluate Vulkan or vendor-specific acceleration only after a successful device test.
- Do not assume every GGUF model, architecture, or quantization is supported equally.

### Future adapters
Additional runtimes may be added only behind the same interface and only when they provide a measurable benefit on the target device. Avoid bundling several large native runtimes into the first APK.

## 3. MANA Core contract

Suggested Kotlin-facing contract:

    interface LocalInferenceEngine {
        suspend fun load(model: ModelPack, options: LoadOptions): EngineLoadResult
        suspend fun generate(request: GenerationRequest): Flow<GenerationEvent>
        suspend fun embed(texts: List<String>): List<FloatArray>
        suspend fun unload()
        fun status(): EngineStatus
    }

The Model Router chooses an installed model by:
1. task capability (chat, summarization, structured extraction, embeddings);
2. runtime and model-format compatibility;
3. measured peak memory and available RAM;
4. measured latency and thermal behaviour;
5. battery and foreground/background policy;
6. user-selected quality/performance preference.

The router must never load a second large chat model merely to compare answers. It unloads the previous model and verifies that memory was released before loading another.

## 4. Model-pack manager

The APK should contain the MANA shell, core interfaces, and small metadata only. Large model weights are separate downloadable model packs, installed into app-private storage after the user chooses them.

Each model pack manifest must record:
- model identifier and publisher;
- exact model version and cryptographic SHA-256;
- runtime and model format;
- quantization and context length;
- download size and estimated peak RAM;
- supported ABIs / tested devices;
- licence name and licence URL;
- tokenizer and chat-template identifiers;
- required safety/system prompt configuration.

Reject incomplete downloads, hash mismatches, unsupported formats, and packs without a known licence. Never execute a model pack as native code.

## 5. Device-fit and memory policy

Do not count Android's RAM expansion / dynamic RAM as equivalent to physical RAM. It may use storage and is not a guarantee that a large model will run smoothly.

At runtime:
- read total and available physical memory from Android;
- reserve memory for Android, MANA UI, avatar, audio, and background services;
- estimate model weights + KV cache + runtime overhead before loading;
- start with a modest context window (for example 2,048 tokens), then benchmark 4,096 only if safe;
- reduce context, batch size, and concurrency before considering a larger model;
- stop or unload inference when the OS reports memory pressure;
- never keep multiple large LLMs resident simultaneously.

The model file size is not the same as peak RAM usage. A 2.58 GB model bundle does not imply a 2.58 GB RAM requirement. Measure actual peak memory on the target phone.

## 6. Candidate model evaluation

Do not lock the project to Gemma or to one model vendor. Maintain a small benchmark shortlist across supported open model families and choose based on:
- Russian-language quality;
- instruction following and tool-call reliability;
- reasoning quality for the intended tasks;
- peak RAM and load time;
- generation speed and thermal throttling;
- licence compatibility;
- model availability in the chosen runtime's supported format.

A compact chat model is the initial default. Larger packs are optional downloads, never mandatory for installation. A separate small embedding model may be used for local semantic memory if its additional RAM and storage cost is justified.

## 7. Other local capabilities

Keep these as separate replaceable modules:
- Speech-to-text: Android offline speech service if present, or a tested local ASR engine.
- Text-to-speech: Android offline voice when installed; optional local TTS pack later.
- Embeddings: local embedding model for semantic memory, with keyword search as a fallback.
- Vision: optional local image model, not loaded while the chat model is active unless memory permits.
- Avatar: lightweight 2D/3D rendering; avatar animation must pause or reduce quality during heavy inference.

No claim of offline voice support should be made until the chosen Russian ASR/TTS assets are installed and tested without network access.

## 8. Local memory and privacy

- Store conversations, preferences, diary, and vector index locally by default.
- Encrypt sensitive local records with keys protected by Android Keystore.
- Provide controls to inspect, export, and delete memory.
- Keep model prompts and tool outputs out of logs unless the user explicitly enables diagnostics.
- The model does not receive Android permissions. Tools call Android APIs through permission-gated app components.

## 9. Safety and permissions

- Use Android permission prompts and supported APIs; no permission bypass.
- Ask for confirmation before sending messages, deleting files, making purchases, or performing other consequential actions.
- Use allowlisted tool schemas; never execute arbitrary shell commands or model-generated code.
- Run model inference in a bounded worker/service with cancellation, timeouts, and memory limits.

## 10. Acceptance tests before calling it usable

1. Install the APK without any model and confirm that the app opens and explains how to install a local model.
2. Install one small model pack and verify its SHA-256.
3. Load the model in airplane mode and complete a Russian conversation.
4. Record model load time, tokens/second, peak process memory, temperature, and battery drain.
5. Test 2,048-token context, then 4,096 if memory permits.
6. Background and resume the app; confirm that Android does not leave a stuck foreground service.
7. Force memory pressure and confirm safe unload/fallback instead of a crash.
8. Confirm that no network request is required for local chat, memory search, or offline speech features claimed by the build.

## 11. Device identity must be verified

The project notes currently identify the target as realme RMX5566 / “realme C85 5G”, but public device listings do not agree on the chipset associated with that model number. The official realme C85 5G specification page lists MediaTek Dimensity 6300, while a third-party RMX5566 listing identifies Snapdragon 685. Therefore the build must not hard-code either chipset or promise NPU acceleration.

Before selecting the final model pack, collect on-device values:
- Build.MANUFACTURER, Build.MODEL, Build.DEVICE, Build.HARDWARE;
- Build.SUPPORTED_ABIS;
- total physical RAM and current available RAM;
- total and free app-accessible storage;
- Android API level and GPU/driver information where available.

The app should generate a local compatibility report and benchmark the candidate model on the actual handset.

## 12. Sources

- LiteRT-LM overview and supported model/runtime information: https://developers.google.com/edge/litert-lm/overview
- LiteRT-LM source and releases: https://github.com/google-ai-edge/LiteRT-LM
- llama.cpp Android guide: https://github.com/ggml-org/llama.cpp/blob/master/docs/android.md
- realme C85 5G official specifications: https://www.realme.com/global/realme-c85-5g/specs

## 13. Explicit non-goals for this phase

- No GPT, Gemini, Claude, Grok, or other cloud inference.
- No API keys or cloud credentials in the APK.
- No claim that the local model is a newly trained foundation model. MANA Core is the assistant/orchestration layer using separately licensed model weights.
- No claim of full OS/system privileges from an ordinary APK.
- No fixed promise that a particular model will run until it passes the target-device benchmark.
