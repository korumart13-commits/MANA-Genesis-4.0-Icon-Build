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

## Owner authority

The owner can stop a request, clear local memory, revoke paired devices, and decline any proposed action. No self-update, release, privilege escalation, or firmware change may bypass owner approval.
