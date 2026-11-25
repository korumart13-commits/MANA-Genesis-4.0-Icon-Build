# MANA Genesis 4.0 — Engine Implementation Status

Branch: `mana-avatar-voice-server`

## Implemented in source

- `integration/ManaEngine.kt`: MANA-owned provider router. GPT is preferred; local inference is the fallback contract. Cancellation is checked before, during provider transitions, and after calls.
- `integration/ManaGatewayChatService.kt`: chat path now routes through `ManaEngine` and `ManaGatewayGptProvider`.
- `integration/ManaSystemKernel.kt`: MANA-first composition root. It registers the MANA Engine as the central `brain` module and routes assistant requests through the cognitive runtime.
- `integration/tests/ManaEngineTest.kt`: tests for GPT priority, local fallback, and cancellation.
- `docs/MANA_DUAL_CORE_AND_GPT_ENGINE_SPEC.md`: dual-core/GPT architecture contract.

## Important implementation boundaries

- GPT is a hosted model. The OpenAI API key must remain on the MANA server/gateway; the Android app receives only a revocable MANA device token stored through Android Keystore.
- `ManaLocalInferenceRuntime` is an integration contract, not a completed LiteRT-LM binding. A specific LiteRT-LM artifact, compatible model, tokenizer/template, and ARM64 device benchmark must be selected before claiming local inference works.
- The chat service currently constructs the engine with GPT gateway configured and `local = null`. The local fallback path is covered by a fake-provider unit test but is not yet wired to a real on-device model.
- `ManaSystemKernel` is currently an application-layer composition root. It does not replace the phone's Linux kernel, Android framework, HAL, bootloader, or recovery.
- No APK, boot image, kernel image, recovery image, or ROM has been built by these commits. CI execution and device testing remain pending.

## Next engineering steps

1. Add a tested local inference adapter after pinning LiteRT-LM Android dependency and compatible model bundle.
2. Add engine-level integration tests with a mock MANA gateway and local runtime.
3. Build the Android project in CI; resolve actual compiler/test failures rather than assuming success.
4. Add Android lifecycle, foreground-service, thermal/battery and memory-budget integration.
5. Only then continue the separate RMX5566 AOSP/Linux device bring-up using exact kernel/device/vendor sources and verified partition/AVB data.
