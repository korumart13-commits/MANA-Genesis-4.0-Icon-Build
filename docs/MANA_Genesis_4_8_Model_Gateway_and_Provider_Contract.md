# MANA Genesis 4.8 — Model Gateway and Provider Integration Contract

## Purpose

Define the next executable boundary between the Android MANA client and a real reasoning provider without placing provider credentials in the APK or source repository.

## Required deployment shape

1. Android client calls one preconfigured HTTPS MANA gateway. The endpoint is injected by a verified build/deployment configuration; the owner is not asked to type a URL into the app.
2. The gateway authenticates the installed client using a revocable, scoped device credential. A long-lived provider API key is never shipped to Android.
3. The gateway stores provider API keys in its server-side secret manager and forwards only the minimum conversation context needed for the request.
4. The gateway enforces request size, rate limits, timeouts, provider allowlists, and abuse controls.
5. The gateway returns structured answer text, tool-call proposals, and source metadata. The Android client never executes arbitrary code or shell commands returned by a model.
6. MANA's local policy broker checks every proposed action. Sensitive operations require a separate owner confirmation; unknown operations are denied.
7. When the gateway is unreachable, MANA reports offline mode honestly. It does not fabricate an AI answer or pretend a local placeholder is a connected model.

## Runtime adapter contract

- Implement `ManaReasoningModel` for the configured provider.
- Prefer `ManaCancellableReasoningModel` and close in-flight HTTP calls when `ManaRuntimeControl.onStop` fires.
- Treat local memory, dialogue, and retrieved page text as untrusted reference data.
- Enforce maximum input/output tokens, bounded history, explicit timeouts, and safe parsing of structured responses.
- Keep private reasoning traces out of local memory and UI. Store only user-visible conclusions and action outcomes.
- Never let a provider response change permissions, safety policy, endpoint, update channel, or owner identity.

## Source-aware retrieval

A separately configured retriever may implement `ManaCancellableKnowledgeRetriever`. Search results must retain title, HTTPS URL, excerpt, and retrieval timestamp. The model must distinguish evidence from instructions and must not invent citations.

## Configuration and release gates

Before a real provider is considered connected:
- deploy the gateway to a verified HTTPS domain;
- configure provider credentials server-side only;
- configure the gateway endpoint through a controlled build variable or signed remote configuration with an allowlisted origin;
- implement device authentication, rotation, and revocation;
- test stop propagation against an in-flight HTTP request;
- test prompt injection, oversized responses, invalid JSON, timeouts, and provider outages;
- verify that no API key, device secret, or private endpoint credential appears in the APK, logs, artifacts, or Git history;
- publish a privacy notice and data-retention policy.

## Current project status

The Android project currently has client-side cloud-health/chat plumbing and a provider-neutral cognitive orchestration interface. The real gateway deployment, provider adapter, credentials, source-search provider, device authentication, and production privacy controls are not verified as deployed. Therefore the app must continue to show `not connected / local mode` until these gates pass.

## Implemented Android adapter contract

The build branch now contains `integration/ManaGatewayReasoningModel.kt`:

- `ManaGatewayConfig` accepts only the exact configured HTTPS host, standard HTTPS port, and `/v1/mana/respond` route. It rejects URL credentials, query strings, fragments, host mismatches, and HTTP.
- `ManaGatewayCredentialProvider` is an injection point for a short-lived, revocable device bearer token. No token is bundled, generated, or persisted by this adapter.
- `ManaAndroidGatewayCredentialStore` encrypts an enrolled device token with an AES-GCM key held by Android Keystore; the app can revoke the stored credential and its key.
- `ManaGatewayChatService` connects the adapter to the bounded cognitive runtime and exposes a visible Stop control in the MANA chat. No action tools are advertised or executed until real Android tool adapters exist.
- The build reads optional `MANA_GATEWAY_ENDPOINT` and `MANA_GATEWAY_HOST` repository variables. Both must be set together and pass HTTPS/host/route validation. If absent, a generated blank configuration keeps the app in offline mode; the user is not asked to type a server URL.
- `ManaHttpsUrlConnectionTransport` disables redirects, applies bounded connect/read timeouts, limits response bytes, and disconnects when the host cancellation signal fires.
- `ManaAndroidGatewayJsonCodec` serializes bounded context and parses only bounded structured answer/action proposals. It does not accept executable code. The existing local policy remains authoritative for every proposed action.
- Non-2xx responses, missing credentials, invalid endpoint configuration, cancellation, and oversized payloads fail closed.

### Gateway wire protocol v1

The Android adapter sends JSON to the configured route with `protocol_version: 1`, `conversation_id`, `cycle`, `input`, bounded `memories`, `dialogue`, `sources`, `observations`, `available_tools`, and an explicit `context_trust` marker. The gateway must treat all context fields as untrusted data.

A successful response is JSON with:
- `summary`: short user-safe progress summary;
- `answer`: optional user-facing answer;
- `actions`: zero to three objects with `name` and string-only `arguments`;
- `stop_after_actions`: boolean.

The response action list is a proposal only. Android must pass each proposal through the local allow-list and owner-confirmation flow before any tool adapter executes it. The gateway must never be allowed to grant permissions, change the endpoint, or modify local policy.

### Remaining deployment/integration gates

The adapter is wired into the home-screen chat path behind generated build configuration. It activates only when both trusted build variables `MANA_GATEWAY_ENDPOINT` and `MANA_GATEWAY_HOST` are set and pass validation. Neither variable is currently supplied in this repository's build, so the app remains in offline mode. No endpoint, provider, or credential has been invented. A real deployment still needs a verified HTTPS origin, server-side provider configuration, device enrollment/token issuance and rotation/revocation, backend implementation of protocol v1, privacy and retention controls, and a reviewed owner-approved enrollment UI that provisions the Android Keystore token.

## Owner authority

The owner can stop a request, clear local memory, revoke paired devices, and decline any proposed action. No self-update, release, privilege escalation, or firmware change may bypass owner approval.
