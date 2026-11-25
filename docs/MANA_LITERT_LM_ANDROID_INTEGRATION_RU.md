# MANA Local AI — LiteRT-LM Android integration

## Current design

- MANA uses Google's LiteRT-LM Android runtime through `ManaLiteRtLmRuntime`.
- The generated Android app pins `com.google.ai.edge.litertlm:litertlm-android:0.16.1`.
- The generated app's Kotlin compiler is raised from 2.0.21 to 2.2.20 when that exact baseline is present.
- The selected local model is Qwen3-0.6B INT4, LiteRT-LM format, Apache-2.0.
- On first explicit setup or first offline chat, MANA automatically downloads the pinned model revision from Hugging Face into app-private `filesDir/models/mana-local.litertlm`. No file picker, manual transfer, or separate model installer is required.
- The downloaded file is accepted only after a minimum-size check and SHA-256 verification. An incomplete or modified download is rejected.
- The model is not copied into the APK's assets. The Android on-device AI guidance recommends keeping large model files outside the APK and downloading them into app-private storage; this avoids an enormous APK and lets Android install the app normally.
- The model download is about 347 MB and requires internet access once. After it is installed, local inference runs offline.
- GPT through MANA Gateway remains the preferred provider when configured; the local model is the on-device fallback. If no gateway is configured, MANA provisions and uses the local model.
- CPU is the conservative initial backend. GPU/NPU are not enabled until tested against RMX5566 drivers and a matching model bundle.
- The local engine is initialized on the chat worker, retained for subsequent messages, and closed with the MANA chat lifecycle.

## Pinned local model

- Repository: https://huggingface.co/litert-community/Qwen3-0.6B-int4
- File: `qwen3_0.6b_q4_block32_ekv1280.litertlm`
- Revision: `6aa2daf`
- Approximate published size: 347 MB
- SHA-256: `312ce4e6bc0816edf844b9b7e66542648a89440b54822c3581e48a19e9ff5715`
- License: Apache-2.0 (model card states it is inherited from Qwen/Qwen3-0.6B).

The 0.6B INT4 model is a compact baseline, not a claim of perfect or frontier-level intelligence. It is intended for offline chat, summaries and simple question answering. More demanding reasoning stays with the configured cloud provider when available.

## Installation and first launch

1. Install the MANA Android application.
2. Open MANA and tap «Подготовить встроенный локальный ИИ · 347 МБ», or send the first message while no cloud gateway is configured.
3. Keep internet access available while the model is downloaded and verified.
4. After successful verification, MANA can answer locally without internet. The model stays in app-private storage.

## Validation still required

- GitHub Actions must compile and test the generated Android project with the pinned Kotlin and LiteRT-LM versions.
- The RMX5566 must be tested physically for model initialization, Russian-language response quality, memory pressure, latency, heat and battery use.
- The current local adapter uses the CPU backend; device-specific GPU/NPU acceleration is not enabled.
- Cancellation is checked between streamed chunks; a native inference cancellation hook needs a verified API-specific implementation.
- This Android app is MANA's primary Android home/assistant app, not yet a privileged Android system component and not a replacement for the Linux kernel. The AOSP/system-image stage comes after the app is stable.
