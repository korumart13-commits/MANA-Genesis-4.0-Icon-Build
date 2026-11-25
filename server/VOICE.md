# Dedicated MANA Russian voice

The optional `voice_app.py` service uses the official Qwen3-TTS VoiceDesign model
`Qwen/Qwen3-TTS-12Hz-1.7B-VoiceDesign`. The official model card declares Apache-2.0,
supports Russian, and documents expressive voice design:
https://huggingface.co/Qwen/Qwen3-TTS-12Hz-1.7B-VoiceDesign

The voice prompt is original and descriptive; it does not clone a game character,
actor, or private person. Model weights are downloaded from Hugging Face on first
startup. The model is several gigabytes and should run on a GPU host for practical
latency. CPU mode is disabled unless `MANA_TTS_ALLOW_CPU=true`.

## Build and run

```sh
docker build -f Voice.Dockerfile -t mana-voice .
docker run --rm --gpus all -p 127.0.0.1:8081:8081 \
  -e MANA_DEVICE_BEARER_TOKEN="$MANA_DEVICE_BEARER_TOKEN" \
  -v mana-model-cache:/models/huggingface mana-voice
```

Put the service behind TLS and keep it private. Use the same device bearer token
as the gateway until a per-device enrollment system is implemented.

- `GET /health`: model-load status.
- `POST /v1/mana/voice/synthesize`: JSON body `{"text":"..." }`, bearer auth,
  returns bounded PCM WAV with `Cache-Control: no-store`.

The Android integration now contains an optional HTTPS audio client. When the trusted build variables `MANA_TTS_ENDPOINT` and `MANA_TTS_HOST` are configured and a device token has been provisioned through an owner-approved enrollment flow, the app requests this service, plays the returned WAV, and drives the avatar mouth animation. If any of those prerequisites is absent or the service fails, it falls back to Android TextToSpeech. The app does not silently configure a server or invent a token.
