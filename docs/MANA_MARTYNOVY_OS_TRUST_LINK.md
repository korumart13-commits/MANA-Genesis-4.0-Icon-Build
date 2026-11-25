# MANA ↔ «Операционная система Мартыновы» — Secure Trust Link

Status: implementation specification only. No production website, domain, server, certificates, or deployed connection have been created yet.

## Product naming

- Website/service brand: **«Операционная система Мартыновы»**.
- Assistant and local intelligence: **MANA**.
- Device-side system integration layer: **MANA System Bridge**.
- Private backend: **MANA Cloud**.

The intended relationship is analogous at a product-architecture level to an operating system and its trusted vendor services: the OS owns local device functions; MANA Cloud provides optional account/device enrollment, signed update metadata, model manifests, and encrypted synchronization. This does not make MANA part of Android or grant Android platform privileges.

## Important Wireshark clarification

Wireshark is a packet-capture and protocol-analysis tool. It does not encrypt website keys or secure an application. TLS 1.3 encrypts traffic in transit. Wireshark may inspect packet metadata; decrypting a capture requires session secrets or other applicable credentials, which must never be embedded in production logs or exposed to users. Never send private keys, API keys, passwords, or TLS session secrets to Wireshark or commit them to source control.

## Device identity and key handling

1. On first-run enrollment, MANA System Bridge generates a unique asymmetric key pair using Android Keystore.
2. The private key remains non-exportable in Android Keystore where the device supports it; the app requests signing operations rather than reading private key bytes.
3. Prefer hardware-backed TEE/StrongBox protection when supported. Detect and report the actual security level; do not assume every realme device supports StrongBox.
4. Optionally request Android hardware key attestation and verify the certificate chain on the server. Attestation is an evidence signal, not a substitute for authentication or app security.
5. Server issues a one-time enrollment challenge. The app signs it; owner approves enrollment.
6. Server binds the public key to a device record and issues short-lived, narrowly scoped access tokens.
7. Every API call uses TLS 1.3 where supported, validates the server certificate, and is authorized server-side.
8. Consider certificate/public-key pinning with a documented backup pin and rotation procedure. Pinning must not disable normal certificate validation or create an unmaintainable lockout.
9. Revoke a device immediately if the owner disconnects it, the key is suspected compromised, or the installation is reset.

## Secret storage

- Never place server private keys, database passwords, signing secrets, or provider API keys in APK assets, source code, GitHub Actions logs, or public repositories.
- Keep server secrets in the hosting provider's secret manager/KMS.
- Keep user/provider credentials in Android Keystore-backed encrypted storage when user-controlled client storage is required; prefer server-side provider credentials for a managed cloud proxy.
- Rotate secrets and certificates; maintain audit trails without logging message contents or credentials.
- Use separate development, staging, and production keys and endpoints.

## API trust boundaries

- MANA local core can work offline.
- Cloud calls are optional and require a valid enrolled device.
- Server never receives broad Android permissions; Android permissions remain on the device and are granted by the user through Android.
- The backend cannot remotely grant Android permissions or become a privileged Android system service.
- Cloud commands must be typed, scoped, signed, replay-protected, and subject to local permission checks and user confirmation for high-impact actions.
- No remote shell, arbitrary APK execution, hidden persistence, or unrestricted device-control endpoint.

## Suggested service endpoints

- `POST /v1/enrollment/challenge`
- `POST /v1/enrollment/complete`
- `POST /v1/device/heartbeat`
- `GET /v1/models/manifest`
- `GET /v1/updates/manifest`
- `POST /v1/sync` (only explicitly selected, client-encrypted data)
- `POST /v1/device/revoke`

## Hosting and website scope

The public-facing website may show only basic product information, legal documents, and a sign-in/enrollment entry point. Private API endpoints require device authentication and are not exposed as a public file browser. The website itself cannot be made literally invisible to the internet; endpoints can be discovered, so they must be hardened and return no private data to unauthenticated clients.

Production deployment requires an owner-approved domain and hosting account, DNS, TLS certificate, backend runtime, database, encrypted object storage, backups, monitoring, security review, and incident-response procedures. No account, domain, or hosting access is assumed or fabricated.

## Implementation and verification checklist

- [ ] Confirm domain and hosting provider with owner.
- [ ] Create backend in a private repository; keep secrets outside Git.
- [ ] Implement enrollment challenge, signature verification, revocation, and short-lived tokens.
- [ ] Implement TLS, authorization middleware, rate limiting, audit events, and abuse monitoring.
- [ ] Implement Android Keystore key generation and signature-based requests.
- [ ] Test whether hardware-backed keys/attestation are available on the target realme phone.
- [ ] Add key rotation and recovery/re-enrollment.
- [ ] Test invalid signatures, replayed requests, expired tokens, revoked devices, and offline mode.
- [ ] Publish privacy policy, retention/deletion policy, and security contact.
- [ ] Only after deployment and independent tests, describe the service as production-ready.

## Official technical references

- Android Keystore: https://developer.android.com/privacy-and-security/keystore
- Android key attestation: https://developer.android.com/privacy-and-security/security-key-attestation
- Android TLS/network security: https://developer.android.com/privacy-and-security/security-ssl
- Android network security configuration: https://developer.android.com/privacy-and-security/security-config
- Wireshark TLS analysis: https://wiki.wireshark.org/TLS
