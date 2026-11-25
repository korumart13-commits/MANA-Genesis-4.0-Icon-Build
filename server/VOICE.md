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

This is a separate service component. The Android APK does not yet call this
endpoint; its current speech output remains Android TextToSpeech. An Android
audio client, cancellation, caching policy and lip-sync hookup are still needed.
