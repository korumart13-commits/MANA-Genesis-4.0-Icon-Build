# MANA Genesis Gateway

This is a deployable HTTPS-gateway service for the Android client route
`POST /v1/mana/respond`. GitHub Actions builds the APK; it does **not** host
this service continuously. Deploy this container to an always-on VPS or managed
container host, put it behind TLS, and configure the APK build with the verified
public HTTPS endpoint.

## Required configuration

Copy `.env.example` to `.env` on the server and set:
- `MANA_DEVICE_BEARER_TOKEN`: random device credential, at least 32 characters.
- `MANA_MODEL_BASE_URL`: trusted HTTPS OpenAI-compatible provider base URL.
- `MANA_MODEL_NAME`: provider model identifier.
- `MANA_MODEL_API_KEY`: provider secret. Keep it only on the server.

Do not commit `.env`, credentials, certificates or private keys. The current
Android integration expects a bearer token provisioned into Android Keystore;
the deployment workflow for issuing/revoking per-device tokens is a separate
release task and must not be replaced by a shared hard-coded token.

## Run locally

```sh
cp .env.example .env
# Edit .env with real values
docker compose up --build -d
curl http://127.0.0.1:8080/health
```

For production, terminate TLS at a reverse proxy or managed ingress, restrict
network access, enable backups/monitoring, rotate device credentials, and do
not expose port 8080 directly to the public internet.

## API behavior

- `GET /health`: service readiness and configuration flags; no secrets returned.
- `POST /v1/mana/respond`: authenticated, bounded request; returns structured
  `summary`, `answer`, `actions`, and `stop_after_actions`.
- Missing model configuration returns HTTP 503. Provider/network errors fail
  closed; no fake answer or simulated "online" state is returned.
- Proposed actions must be in the tool allowlist sent by the Android client.
  Android still applies its own permission policy and owner-approval broker.

## Current limitations

This gateway does not yet implement per-device enrollment/refresh/revocation,
server-side memory storage, rate-limit persistence, audit retention, or provider
selection UI. It is a functional authenticated gateway skeleton, not a complete
managed cloud service. Use a unique device token and add enrollment before
multi-device production use.
