# MANA Local AI — LiteRT-LM Android integration

## Implemented in this branch

- ManaLiteRtLmRuntime adapts Google's LiteRT-LM Kotlin API to the MANA provider contract.
- The generated Android app pins com.google.ai.edge.litertlm:litertlm-android:0.16.1.
- The generated app's Kotlin compiler is raised from 2.0.21 to 2.2.20 when that exact baseline is present. LiteRT-LM 0.16.1 carries Kotlin metadata 2.3; Kotlin 2.2+ is required.
- Model import copies the selected file off the UI thread into app-private storage: filesDir/models/mana-local.litertlm.
- The app never packages model weights in the APK.
- Chat routing is GPT through MANA Gateway first, then local LiteRT-LM if GPT fails; if no gateway exists, local-only chat is available when a model is installed.
- CPU is the conservative initial backend. GPU/NPU are not enabled until tested against the RMX5566 drivers and a matching model bundle.
- The local engine is initialized in the chat worker, retained for subsequent messages, and closed with the MANA chat lifecycle.

## Installing a model

1. Obtain a compatible .litertlm bundle and review its model license and usage conditions.
2. Transfer the file to the phone.
3. In MANA Home select «Установить локальный ИИ-движок» and choose the file.
4. Restart MANA. The first load can take several seconds and uses significant memory.
5. Test in airplane mode. A successful response confirms the local path; merely importing the file does not.

A candidate for initial evaluation is the public LiteRT Community Gemma 3 1B repository:
https://huggingface.co/litert-community/Gemma3-1B-IT

It includes a 584 MB INT4 gemma3-1b-it-int4.litertlm file, but access is gated by acceptance of the Gemma terms. This is a candidate, not a verified RMX5566 model. Use the general CPU-compatible LiteRT-LM bundle, not the Tensor-NPU-specific bundles compiled for Google Tensor chips. Qualcomm SM6225 is not Google Tensor.

## Important limitations / next validation

- GitHub Actions must compile the generated app with the pinned Kotlin and LiteRT-LM versions. API/compiler incompatibilities must be fixed from the official LiteRT-LM Android guide before treating the integration as build-ready.
- The model file is not included in the APK and is not automatically downloaded. Its license, SHA-256, memory use, latency, thermal behavior, and Russian-language quality need a device test.
- Model import currently verifies only that the selected file is nontrivial in size. It does not yet validate model metadata or compute/display SHA-256.
- Cancellation is checked between streamed chunks; a hard native inference cancellation hook still needs a verified API-specific implementation.
- This Android application is an app-level MANA environment, not a privileged system service or a replacement for the Linux kernel.