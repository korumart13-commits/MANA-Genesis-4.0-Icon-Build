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


## Read-only tool execution boundary (Genesis 4.0 next stage)

The Android project now includes `ManaReadOnlyToolRegistry`, a fail-closed execution boundary for future local read-only adapters.

- Only a fixed allowlist of read-only tool identifiers can be registered.
- The model cannot register handlers, add tool names, or widen argument schemas.
- Each registered tool declares its accepted and required string arguments.
- Unknown tools, unknown arguments, missing required arguments, control characters, oversized values, duplicate registrations, and stopped requests are rejected before a handler runs.
- Handler exceptions become a generic safe failure; exception details are not returned to the model.
- Side-effecting operations are intentionally excluded. They remain blocked by the cognitive policy until a separate owner-confirmation UI and auditable approval token flow are implemented.
- The registry itself does not grant Android permissions and is not yet populated with Android system handlers.

The associated unit tests cover allowlisting, schema enforcement, cancellation, and rejection of side-effecting tool registration. This is an execution-boundary foundation, not a claim that MANA can already control phone settings or applications.


## Android read-only diagnostics

The gateway chat runtime now registers three local Android read-only tools:

- `device.read_battery`: reads the battery percentage through Android's `BatteryManager`.
- `device.read_storage`: reads coarse internal-storage totals and available space through `StatFs`.
- `device.read_status`: combines Android release, battery percentage, and coarse storage availability.

These tools request no runtime permission and cannot modify device state. They are passed to the model only when the user asks a question that needs device status, through the normal bounded tool cycle. The app inventory tool name remains reserved but is not registered: installed-app lists can reveal private habits and are not sent to the remote gateway. No app launching, settings changes, calls, messages, file writes, or deletion are implemented in this stage.


## Exact-action owner approval broker

The Android source tree now includes `ManaOwnerApprovalBroker`, a platform-neutral approval-token primitive for future side-effecting adapters.

- Only actions already classified by `ManaDefaultCognitivePolicy` as `REQUIRES_OWNER_CONFIRMATION` can create an approval request. Denied, unknown, and read-only actions cannot use this path to obtain a token.
- A request exposes the exact bounded action arguments alongside the action name and short human-readable summary, so the owner can inspect the recipient, target, content, or setting before confirming. It expires after 60 seconds by default (hard maximum two minutes).
- The owner-facing UI must call `confirmFromOwnerUi` only from a deliberate confirmation interaction. A model proposal, background job, or network response is not owner confirmation.
- An issued opaque token is bound to the exact action name and canonical SHA-256 fingerprint of all arguments. Any changed argument, changed action, expired token, missing token, or replay is rejected.
- Tokens are single-use and removed before the authorization result is returned. The future adapter must consume the token immediately before execution and still check Android runtime permission / platform role.
- Denials, approvals, expiry, mismatches, and consumption create a bounded in-memory audit trail. Argument values and secrets are not written to that audit trail.
- Pending requests and tokens are memory-only and are lost when the process ends. This deliberately avoids persisting reusable approvals.

The chat now surfaces pending approval requests in an owner-facing Android confirmation dialog. A dedicated MANA Permission Center also shows pending requests, the latest bounded in-memory audit events, and a shortcut to Android's app-permission settings. The dialog shows the action name, exact arguments, summary, and remaining time, with explicit approve/decline controls. Dismissing the dialog is treated as decline. Approval is recorded, but because no side-effecting execution adapter is connected, the one-time token is immediately discarded and the app explicitly tells the owner that nothing was changed. This UI is a confirmation preview, not an Android OS permission prompt. Side-effecting actions remain unavailable until a concrete adapter is implemented, independently checks platform permissions/roles, consumes an exact-action token immediately before execution, and has dedicated tests.


## First owner-approved Android action: launch an installed app

The first side-effecting adapter is deliberately narrow.

- The model can request `apps.find_launchable` with a user-provided name fragment. Android filters launcher-visible apps locally and returns at most five matching labels and package IDs; the full installed-app inventory is never sent.
- Android package visibility is limited to launcher activities through a manifest `<queries>` intent filter. The app does not request `QUERY_ALL_PACKAGES`.
- `apps.launch` requires owner confirmation. The dialog shows the actual app label resolved by Android and the exact package ID, not a model-supplied display name.
- The executor accepts exactly one argument, `package_name`, rejects arbitrary intents, malformed package IDs, MANA's own package, non-launchable packages, and every other action type.
- On the explicit owner button, the broker issues a short-lived token; the executor consumes it for the exact action immediately before calling Android's package launch intent. The launch intent is obtained from PackageManager, never supplied by the model.
- Android still decides whether the app can be launched. No runtime permission, default role, root access, or system privilege is granted by this feature.
- All other side-effecting actions remain unavailable and are declined by the UI until separately implemented and reviewed.


## Owner-approved Android settings navigation

The next safe system-integration stage adds `settings.open_page`. It accepts exactly one `page` argument and only these fixed values:

- `wifi` — Android Wi-Fi settings
- `bluetooth` — Android Bluetooth settings
- `display` — Android display settings
- `sound` — Android sound settings
- `battery` — Android battery-saver settings

The owner confirmation dialog displays the resolved page name. The executor creates the Android intent from a fixed internal mapping, checks that Android can resolve it, consumes the one-time token for the exact action, and only then opens the system page. Model-provided intent actions, component names, URLs, arbitrary settings pages, and extra arguments are rejected. This feature opens Android settings; it does not change switches or values itself. All other system modifications remain unavailable.


## Notification status and settings navigation

The next notification-related increment remains privacy-preserving:

- `device.read_notification_status` reads only whether MANA's own app notifications are enabled and whether Android has granted MANA notification-policy access for Do Not Disturb. It does not read notification titles, message bodies, sender names, or other apps' notification data.
- `settings.open_page` accepts two additional fixed destinations: `notifications` opens Android's notification settings, and `do_not_disturb` opens Android's notification-policy access screen.
- Opening either page requires the same exact-action, one-time owner confirmation as other settings navigation. Android still requires the owner to grant any special access manually.
- MANA does not enable notification access, change Do Not Disturb, silence calls, or read notification content in this increment.
