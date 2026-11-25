# MANA Private Cloud — Service and Website Specification

Status: specification only. No domain, hosting account, DNS, cloud database, or deployment credentials have been supplied; no live service has been deployed.

## Goal

Create a private backend for MANA Genesis 4.0, not a public social network or publicly browsable website. The web origin may host a minimal status/health endpoint and owner-only administration surface; application APIs require device authentication.

## Access model

1. Owner installs MANA and explicitly enrolls this installation.
2. Android Keystore generates a device-bound asymmetric key pair.
3. Enrollment challenge is signed by the device key and approved by the owner.
4. Server issues short-lived scoped tokens after signature verification.
5. Every API call checks signature/token, device status, scope, expiry, and rate limits.
6. Owner can revoke a device and rotate server credentials.
7. No anonymous registration, public model downloads, or unauthenticated data endpoints.

"Only MANA can connect" means only enrolled installations possessing valid device keys can access private APIs. It cannot mean a website is literally invisible to the internet: network endpoints can be discovered and probed, so they must be secure by design.

## Services

- `/v1/device/enroll`: owner-approved enrollment.
- `/v1/device/heartbeat`: device and app version status; no private content.
- `/v1/models/manifest`: signed metadata for approved local model packs.
- `/v1/sync`: optional end-to-end encrypted sync of explicitly selected data.
- `/v1/updates/manifest`: signed update metadata; installation remains user-controlled and platform-compliant.
- `/v1/audit`: owner-visible security events, without logging message contents by default.

## Storage policy

- Do not store proprietary GPT weights. Connect to GPT through the official provider API.
- Store model manifests and checksums; cache only model files whose license and distribution terms permit it.
- Encrypt user data at rest and in transit. Prefer client-side encryption for personal memory so the server cannot read it.
- Define retention, export, deletion, backup, and breach-response procedures before production.

## Deployment requirements

- Owner must choose/authorize a domain and hosting provider.
- Configure DNS, TLS, secrets, database, object storage, backups, monitoring, and access controls.
- Keep all production secrets outside Git and outside the Android package.
- Deploy from a private repository or controlled CI pipeline; verify that the repository and artifacts do not expose private configuration.
- Perform threat modeling, dependency scanning, API authorization tests, and external security review before real user data is stored.

## Non-goals

- No covert access, malware persistence, Android permission bypass, hidden data collection, or public exposure of private user data.
- No guarantee of absolute security or perfect exclusivity; security depends on implementation, hosting, operations, and ongoing patching.
