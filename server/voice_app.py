"""Optional dedicated Russian MANA voice service using the licensed Qwen3-TTS VoiceDesign model."""
from __future__ import annotations

import io
import os
import secrets
from contextlib import asynccontextmanager
from typing import Any

import soundfile as sf
import torch
from fastapi import Depends, FastAPI, Header, HTTPException
from fastapi.responses import Response
from pydantic import BaseModel, ConfigDict, Field
from starlette.concurrency import run_in_threadpool
from qwen_tts import Qwen3TTSModel

MODEL_ID = os.getenv("MANA_TTS_MODEL", "Qwen/Qwen3-TTS-12Hz-1.7B-VoiceDesign")
VOICE_INSTRUCTION = (
    "A warm, soft, expressive adult female Russian voice. Clear natural Russian pronunciation, "
    "gentle anime-inspired dubbing energy without imitating any existing actor or character. "
    "Friendly, calm, lightly playful, emotionally nuanced, never childish or seductive. "
    "Stable timbre and identity across sentences."
)
_model: Qwen3TTSModel | None = None


@asynccontextmanager
async def lifespan(_: FastAPI):
    global _model
    if torch.cuda.is_available():
        _model = Qwen3TTSModel.from_pretrained(
            MODEL_ID, device_map="cuda:0", dtype=torch.bfloat16
        )
    elif os.getenv("MANA_TTS_ALLOW_CPU", "false").lower() == "true":
        _model = Qwen3TTSModel.from_pretrained(
            MODEL_ID, device_map="cpu", dtype=torch.float32
        )
    yield
    _model = None


app = FastAPI(title="MANA Voice Service", version="0.1.0", lifespan=lifespan)


class SpeechRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")
    text: str = Field(min_length=1, max_length=1200)


def require_device_token(authorization: str | None = Header(default=None)) -> None:
    expected = os.getenv("MANA_DEVICE_BEARER_TOKEN", "")
    if len(expected) < 32:
        raise HTTPException(status_code=503, detail="Voice authentication is not configured.")
    scheme, _, supplied = (authorization or "").partition(" ")
    if scheme.lower() != "bearer" or not secrets.compare_digest(supplied, expected):
        raise HTTPException(status_code=401, detail="Unauthorized")


@app.get("/health")
async def health() -> dict[str, Any]:
    return {
        "service": "mana-voice",
        "status": "ok" if _model is not None else "model_not_loaded",
        "model": MODEL_ID,
        "device": "cuda" if torch.cuda.is_available() else "cpu",
    }


def synthesize(text: str) -> bytes:
    if _model is None:
        raise RuntimeError("Voice model is not loaded.")
    wavs, sample_rate = _model.generate_voice_design(
        text=text,
        language="Russian",
        instruct=VOICE_INSTRUCTION,
        non_streaming_mode=True,
    )
    buffer = io.BytesIO()
    sf.write(buffer, wavs[0], sample_rate, format="WAV", subtype="PCM_16")
    return buffer.getvalue()


@app.post("/v1/mana/voice/synthesize")
async def voice(payload: SpeechRequest, _: None = Depends(require_device_token)) -> Response:
    if _model is None:
        raise HTTPException(status_code=503, detail="Dedicated MANA voice model is not loaded.")
    try:
        audio = await run_in_threadpool(synthesize, payload.text)
    except Exception:
        raise HTTPException(status_code=502, detail="Speech synthesis failed.")
    if len(audio) > 12 * 1024 * 1024:
        raise HTTPException(status_code=502, detail="Generated audio exceeded the response limit.")
    return Response(content=audio, media_type="audio/wav", headers={"Cache-Control": "no-store"})
