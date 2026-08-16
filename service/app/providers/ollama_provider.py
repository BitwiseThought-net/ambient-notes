"""
Local LLM-assisted provider.

Ollama isn't an audio-fingerprinting engine, so it can't identify a song from
raw waveform data alone. What it *can* usefully do, entirely locally:

1. Re-rank / sanity-check a low-confidence Dejavu match (e.g. "given these
   3 candidate title/artist pairs and their fingerprint scores, which is
   most plausible?").
2. Take a text transcript of any spoken/sung lyrics (produced upstream by a
   speech-to-text step, out of scope for this provider) and attempt a
   best-effort guess.

This provider is deliberately conservative: on its own, given only raw
audio bytes, it returns `matched=False` unless a transcript hint was
supplied via `extra_context`. It exists primarily as an extensibility point
and as the "free, always-available" tail of the fallback chain.
"""
from __future__ import annotations

import json
import logging

import httpx

from app.config import Settings
from app.models import RecognitionResult
from app.providers.base import RecognitionProvider
from app.providers.registry import register_provider

logger = logging.getLogger(__name__)


@register_provider("ollama")
class OllamaProvider(RecognitionProvider):
    def __init__(self, base_url: str, model: str, enabled: bool = True):
        super().__init__()
        self.base_url = base_url.rstrip("/")
        self.model = model
        self.enabled = enabled

    @classmethod
    def from_settings(cls, settings: Settings) -> "OllamaProvider":
        return cls(base_url=settings.ollama_base_url, model=settings.ollama_model, enabled=settings.ollama_enabled)

    async def is_available(self) -> bool:
        if not self.enabled:
            return False
        try:
            async with httpx.AsyncClient(timeout=3) as client:
                resp = await client.get(f"{self.base_url}/api/tags")
                return resp.status_code == 200
        except httpx.HTTPError:
            return False

    async def recognize(self, audio_bytes: bytes, sample_rate_hz: int) -> RecognitionResult:
        transcript_hint = self.config.get("extra_context", {}).get("transcript") if self.config else None
        if not transcript_hint:
            # No lyric/transcript context to reason over -- nothing useful to do.
            return RecognitionResult(matched=False, provider=self.name)

        prompt = (
            "You identify songs from partial, possibly garbled lyric transcripts. "
            "Respond ONLY with JSON: {\"title\": str, \"artist\": str, \"confidence\": float 0-1}. "
            f"Transcript: {transcript_hint!r}"
        )
        async with httpx.AsyncClient(timeout=20) as client:
            resp = await client.post(
                f"{self.base_url}/api/generate",
                json={"model": self.model, "prompt": prompt, "stream": False},
            )
            resp.raise_for_status()
            payload = resp.json()

        try:
            guess = json.loads(payload.get("response", "{}"))
        except json.JSONDecodeError:
            return RecognitionResult(matched=False, provider=self.name, raw_provider_response=payload)

        confidence = float(guess.get("confidence", 0))
        if not guess.get("title") or confidence <= 0:
            return RecognitionResult(matched=False, provider=self.name, confidence=confidence)

        return RecognitionResult(
            matched=True,
            title=guess.get("title"),
            artist=guess.get("artist"),
            confidence=confidence,
            provider=self.name,
            raw_provider_response=payload,
        )
