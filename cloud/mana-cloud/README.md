# MANA Cloud — deployable starter

This folder contains a Cloudflare Worker that serves the Martynovy OS website and a protected GPT relay API.

## What it does

- `GET /api/health`: reports whether the Worker and its required secrets are configured.
- `POST /api/gpt`: accepts a MANA device request, optionally including selected local-model context, calls the official OpenAI Responses API, and returns the response to the phone.
- All other paths are served from `public/`.

The phone remains responsible for running local models. The phone can send selected local context to the cloud and then route the returned GPT answer back into its local MANA Engine. The cloud does not directly execute a model on the phone.

## Deploy

1. Create/sign in to a Cloudflare account.
2. In Workers & Pages, create a Worker by connecting this GitHub repository and selecting the `cloud/mana-cloud` project root, or deploy this folder with Wrangler.
3. Set Worker secrets (never commit them):
   - `OPENAI_API_KEY`: an API key from the OpenAI Platform.
   - `MANA_DEVICE_TOKEN`: a long random secret used by the currently enrolled prototype device.
4. Deploy. Cloudflare assigns a `*.workers.dev` URL when that subdomain is enabled.
5. Open `/api/health` and confirm both secret flags are true.

Example Wrangler commands from this directory:

```sh
npm install --save-dev wrangler
npx wrangler secret put OPENAI_API_KEY
npx wrangler secret put MANA_DEVICE_TOKEN
npx wrangler deploy
```

Set `MANA_GPT_MODEL` in `wrangler.toml` to a model available to the OpenAI API account. API availability, pricing, and rate limits depend on that account.

## API contract

Request:

```http
POST /api/gpt
Authorization: Bearer <MANA_DEVICE_TOKEN>
Content-Type: application/json
```

```json
{
  "input": "Привет, MANA",
  "local_context": "Необязательный выбранный контекст локальной модели",
  "instructions": "Необязательные дополнительные инструкции"
}
```

The endpoint returns `output_text` plus the upstream response object. The server uses `store: false`; do not treat this as a guarantee that the upstream provider retains no operational metadata. Do not send sensitive data unless the user explicitly permits it.

## Security status and limitations

- The OpenAI API key is read only from Worker secrets and is never sent to the browser.
- HTTPS/TLS is provided by Cloudflare at the public endpoint.
- The prototype uses one shared bearer token. This is suitable only for a private first-device test. Do not distribute the same token in a public APK or publish it in GitHub. A production multi-device release must implement device enrollment, per-device credentials, rotation/revocation, rate limiting, abuse controls, and audit policy.
- This starter does not store conversation history, provide account registration, or deploy a custom purchased domain.
- Free hosting has platform limits and is not a guaranteed production SLA.
- This source code is prepared for deployment; it is not itself a live deployed website or server.
