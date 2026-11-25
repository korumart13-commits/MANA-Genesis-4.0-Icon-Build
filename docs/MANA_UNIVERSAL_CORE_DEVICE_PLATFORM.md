# MANA Genesis 4.0 — Universal Core Architecture

**Decision:** MANA is one device-independent intelligence core plus pluggable inference adapters and separate device platform packages. This is an architecture specification, not a completed runtime or flashable ROM.

## Layers

1. **MANA Core:** identity, original character/personality, Russian-first dialogue policy, memory schema, planner, module registry, typed tool contracts, model router, safety policy and capability metadata. No device-specific assumptions and no large model weights.
2. **Inference Fabric:** stable backend API; adapters for independent local runtimes.
3. **Device Bridge:** OS APIs and permissions for audio, notifications, contacts, files, camera, sensors, Bluetooth, storage and power.
4. **Device Platform:** per-device ABI, hardware profile, graphics/audio and accelerator configuration. A real ROM later needs device tree, kernel, vendor blobs, HAL/VINTF, partitions, AVB and signing.

## Modules in Core

Brain; Memory; Character; Skills; Diary; Life Manager; Smart Receptionist; Contact Intelligence; Game Intelligence; Adaptive Learning; Update Manager. Each module declares required capabilities and reports unavailable if the device cannot provide them. Adaptive Learning may update user-approved local knowledge and preferences; it must not modify executable code autonomously.

## Runtime portfolio

Do not merge upstream runtimes into one monolithic binary. Wrap them behind one interface so model formats, licences, optimizations and update paths remain independent.

- **Google LiteRT-LM:** primary candidate for supported .litertlm LLM bundles; CPU/GPU first, NPU only after exact device/driver verification. Supports model families such as Gemma, Llama, Phi and Qwen.
- **llama.cpp:** GGUF fallback; ARM64 CPU baseline, Vulkan/specialized kernels only after tests.
- **ExecuTorch:** optional backend for supported exported LLM, vision and speech models.
- **ONNX Runtime:** optional interoperability backend for ONNX speech, vision, embeddings and utility models; CPU/XNNPACK first, NNAPI only when tested.
- **MLC LLM:** optional GPU-specialized backend for explicitly supported model builds and devices.

Suggested backend API:

    interface ManaInferenceBackend {
        val id: String
        fun capabilities(): BackendCapabilities
        suspend fun inspect(pack: ModelPack): CompatibilityReport
        suspend fun load(pack: ModelPack, config: InferenceConfig): LoadResult
        suspend fun generate(request: GenerationRequest): Flow<GenerationEvent>
        suspend fun unload()
    }

The router selects by task, format, measured RAM, latency, battery, temperature and device profile. Keep one large chat model resident by default.

## Model packs

Evaluate open-weight families from Google Gemma, Meta Llama, Microsoft Phi, Alibaba Qwen and Mistral. For every exact revision, verify its current model card, licence, redistribution/use terms, attribution and runtime format. A family name does not establish a licence. Store weights separately from Core.

Every pack needs publisher/revision/source, SHA-256, licence URL, runtime/format, tokenizer/chat template, quantization, context, file size, tested RAM/ABI/accelerators, measured load time/tokens per second/peak memory and task tags.

## Universal device support

Universal means portable architecture with per-device capability profiles, not one model or ROM that runs identically everywhere. Detect Android API/build, ABIs, physical/available RAM, free storage, CPU/GPU capabilities, supported APIs and permissions. Capability tiers: Basic (small CPU model), Standard (moderate quantized model), Advanced (verified GPU/NPU), Unavailable (disabled with reason). Do not count storage-backed RAM expansion as physical RAM or infer NPU from marketing names.

## First target: realme C85 5G / RMX5566

First deliver an Android app/device profile, not a replacement ROM. Detect exact model/board/build/ABI on-device and benchmark before selecting models. Do not hard-code a chipset based on uncertain model listings.

A flashable ROM is a later project requiring exact device tree, kernel source/config, vendor blobs, HAL/VINTF, partition map, bootloader/AVB constraints, signing, recovery and device tests. An APK or universal AI core cannot replace these.

## Safety and resource rules

Estimate weights + KV cache + runtime overhead; start with conservative context; unload on memory/thermal pressure. Never execute model-generated shell/code. Tools use typed allowlists and Android permissions. Confirm before messages, deletion, purchases or consequential actions. Keep memory local by default and protect sensitive data with Android Keystore where available. No API keys in APKs/model packs.

## Build sequence

1. Freeze Core contracts and capability schema.
2. Build platform-neutral Kotlin Core and JVM tests.
3. Integrate LiteRT-LM with one small tested model.
4. Add llama.cpp/GGUF fallback.
5. Add model-pack manager with checksums/licence metadata.
6. Implement Android Device Bridge and report.
7. Validate realme profile on the actual phone.
8. Benchmark offline Russian chat, memory, thermals and battery.
9. Add ExecuTorch, ONNX and MLC only for proven use cases.
10. Research ROM after device app/platform is stable and device sources are available.

## Acceptance

Core builds/tests without Android SDK; backends can be added/removed without changing Core; missing models report unsupported cleanly; one MANA identity/memory persists across backend switches; offline chat works after pack installation; packs are checksum- and licence-checked; device capabilities are measured, not guessed. No claim of universal ROM or unverified NPU support.

## References

- https://developers.google.com/edge/litert/overview
- https://developers.google.com/edge/litert-lm/overview
- https://github.com/google-ai-edge/LiteRT-LM
- https://github.com/ggml-org/llama.cpp/blob/master/docs/android.md
- https://docs.pytorch.org/executorch/stable/
- https://onnxruntime.ai/docs/execution-providers/
- https://llm.mlc.ai/docs/get_started/introduction

No cloud GPT/Gemini/Claude/Grok in this phase. Combining runtimes does not create newly trained foundation-model weights. No proprietary source/weights/assets or permission bypass.
