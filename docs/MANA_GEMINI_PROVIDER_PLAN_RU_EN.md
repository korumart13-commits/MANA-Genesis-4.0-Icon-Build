# MANA multi-model intelligence — Google Gemini provider plan

Status: architecture specification; no Google Cloud/Firebase project credentials have been supplied or connected.

## Priority order

1. LiteRT-LM local model is the default brain and must work offline.
2. Optional user-configured providers may include OpenAI, Google Gemini, Anthropic and other providers through provider adapters.
3. A cloud provider is never required to boot the app, use local memory, diary, launcher or basic MANA tools.
4. Provider failure falls back to local inference; strict offline mode disables every network provider.

## Google provider

Google's current official Android path for Gemini is Firebase AI Logic. It provides Kotlin/Java client SDKs, supports Gemini Developer API and Vertex AI Gemini providers, and offers App Check / proxy protections so raw provider credentials are not embedded in the APK.

References:
- https://developer.android.com/ai/gemini/developer-api
- https://firebase.google.com/docs/ai-logic
- https://firebase.google.com/docs/ai-logic/faq-and-troubleshooting

Do not put a Gemini API key, service-account JSON, OAuth refresh token or other secret in source control, APK assets, Gradle constants or logs. Firebase project setup and App Check configuration must be completed by the project owner in their own Google/Firebase console. The app can then use Firebase's SDK and App Check rather than shipping a raw Gemini key.

## Adapter contract

A future Gemini adapter must implement the same provider-neutral interface as the existing MANA engine:
- request: conversation messages, system policy, bounded context, optional tool declarations;
- response: text, provider/model metadata, token usage where available, safety/finish metadata;
- cancellation and timeout;
- retry only for transient failures with bounded backoff;
- explicit opt-in for sending content to cloud;
- no silent upload of local diary, memory or personal files;
- allow-list tools and owner confirmation for consequential actions.

## Privacy and user controls

Expose in MANA settings:
- Local only (default safe offline mode).
- Local first + cloud fallback (default when cloud is configured).
- Gemini enabled/disabled.
- Data categories allowed to leave device, default none beyond the current message.
- Clear provider history and disconnect provider.
- Usage/cost status where provider exposes it.

## Current implementation boundary

The current branch has a local-first engine and generic gateway abstraction. This document does not claim Firebase/Gemini is already integrated. Do not invent Firebase app IDs, project IDs, OAuth credentials, API keys or billing settings. Add the concrete adapter only after a Firebase project configuration is provided and the dependency versions are verified in CI.
