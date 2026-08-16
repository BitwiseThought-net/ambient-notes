from __future__ import annotations

import logging

import httpx

from app.config import Settings
from app.models import RecognitionResult
from app.providers.base import RecognitionProvider
from app.providers.registry import register_provider

logger = logging.getLogger(__name__)

AUDD_ENDPOINT = "https://api.audd.io/"


@register_provider("audd")
class AudDProvider(RecognitionProvider):
    def __init__(self, api_token: str, enabled: bool = True):
        super().__init__()
        self.api_token = api_token
        self.enabled = enabled

    @classmethod
    def from_settings(cls, settings: Settings) -> "AudDProvider":
        return cls(api_token=settings.audd_api_token, enabled=settings.audd_enabled)

    async def is_available(self) -> bool:
        return bool(self.enabled and self.api_token)

    async def recognize(self, audio_bytes: bytes, sample_rate_hz: int) -> RecognitionResult:
        if not await self.is_available():
            return RecognitionResult(matched=False, provider=self.name)

        files = {"file": ("sample.wav", audio_bytes, "audio/wav")}
        data = {"api_token": self.api_token, "return": "apple_music,spotify"}

        async with httpx.AsyncClient(timeout=15) as client:
            resp = await client.post(AUDD_ENDPOINT, data=data, files=files)
            resp.raise_for_status()
            payload = resp.json()

        if payload.get("status") != "success" or not payload.get("result"):
            return RecognitionResult(matched=False, provider=self.name, raw_provider_response=payload)

        result = payload["result"]
        external_ids = {}
        if spotify := result.get("spotify"):
            external_ids["spotify"] = spotify.get("id", "")
        if apple := result.get("apple_music"):
            external_ids["apple_music"] = apple.get("url", "")

        return RecognitionResult(
            matched=True,
            title=result.get("title"),
            artist=result.get("artist"),
            album=result.get("album"),
            release_date=result.get("release_date"),
            confidence=0.9,  # AudD does not return a numeric confidence score
            provider=self.name,
            external_ids=external_ids,
            raw_provider_response=payload,
        )
