# MANA Genesis 4.0 — Avatar, Voice and Server Integration

Status: implementation workstream. Do not mark any milestone complete without a build and runtime evidence.

## Server gateway — first implementation committed

The `server/` directory contains a FastAPI HTTPS-gateway service with:
- bearer authentication checked in constant time;
- strict bounded request schema matching the Android `/v1/mana/respond` contract;
- server-side provider credentials only;
- HTTPS-only upstream provider configuration;
- bounded timeouts and fail-closed error handling;
- action proposals restricted to the tool allowlist supplied by the client;
- Docker deployment files and a health endpoint;
- CI contract tests.

This is a deployable service component, not a deployed 24/7 server. It currently has no real hosting endpoint, no device-enrollment flow, no persistent server memory, and no configured model credentials. It returns 503 until an operator configures a provider. Never put production secrets in GitHub source files or APK assets.

## Separate MANA voice

Preferred research candidate: Qwen3-TTS VoiceDesign, official repository:
https://github.com/QwenLM/Qwen3-TTS

The upstream project documents Russian support, expressive voice design, and an Apache-2.0 code repository/license. Model weights and compute requirements must be reviewed separately before commercial redistribution. The MANA voice must be original: soft, expressive Russian female voice, stable across releases, not cloned from an actor, game character, or private recording.

Integration sequence:
1. Pin a reviewed Qwen3-TTS package/model revision and record model-weight license.
2. Generate an original voice design using a descriptive prompt, not a reference person's voice.
3. Run synthesis on a GPU-capable host or compatible managed inference service.
4. Expose a separately authenticated `/v1/mana/voice/synthesize` endpoint returning bounded audio.
5. Add Android streaming/download playback with cancellation, cache limits, and system-TTS fallback.
6. Add latency, Russian pronunciation, emotional consistency, and privacy tests.

The Android integration now has a separate HTTPS audio client and MediaPlayer playback path. It activates only when trusted build variables `MANA_TTS_ENDPOINT` / `MANA_TTS_HOST` are set and an owner-approved device token exists in Android Keystore; otherwise it falls back to Android's installed Russian TTS voice. The end-to-end path is not yet verified against a deployed model server.

## Original 3D avatar

Target asset: an original stylized female character with long dark hair, dark eyes, dark clothing, consistent face/body/voice identity, and interchangeable outfits/accessories. Do not rip the MiSide model, copy its proprietary rig/texture, or use an actor's likeness. Only use assets with a documented license allowing the intended distribution.

Required production asset:
- VRM 1.0 or GLB/glTF with humanoid skeleton;
- facial blend shapes for blink, gaze, mouth shapes and expressions;
- idle, speaking, listening and gesture animations;
- optimized textures/materials and LODs for mobile;
- source project and asset/license manifest.

Android integration:
1. Select a maintained Android glTF/Filament renderer with a license compatible with MANA.
2. Bundle the model and renderer; do not depend on a remote web page for the avatar.
3. Add a lifecycle-aware avatar surface, background/foreground handling and GPU/thermal fallback.
4. Synchronize mouth animation with voice playback and provide blink/gaze/expression controls.
5. Test frame time, memory, heat and battery on the actual target phone.

The current development branch now generates an original procedural GLB character, bundles a pinned local Three.js/GLTFLoader renderer into the Android assets, and replaces the letter placeholder with a WebView-based 3D surface. Blink and mouth morph targets are driven by the viewer; the Android speech output can signal mouth motion. The asset-generation CI has passed. The Android APK build and actual device rendering/performance test are still pending. This is an initial stylized character, not a polished final art asset.

## Device verification

The previous target in project notes is realme RMX5566 / realme C85 5G. The new phrase “M65 5G” is not enough to identify a device. CI can check compileSdk/minSdk and package metadata, but only a test on the physical phone can verify install, renderer, microphone, TTS, thermal behavior and launcher role. A diagnostic APK/report can guide that test.

## Tesla references

Only public Tesla materials explicitly licensed for reuse, or public patents/specifications used as factual references, may be consulted. Do not import proprietary vehicle software, internal files, firmware, unreleased model weights, logos, or Tesla-owned assets without permission. Tesla material is not a shortcut for avatar geometry or a server implementation.
