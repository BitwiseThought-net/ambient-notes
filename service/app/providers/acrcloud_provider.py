"""
Optional cloud fallback using the *server operator's* ACRCloud project
(distinct from the per-user ACRCloud account the Android app can connect to
directly). Useful if the self-hoster wants "local library first, ACRCloud
for anything else" without asking every user to bring their own key.
"""
from __future__ import annotations

import base64
import hashlib
import hmac
import logging
import time

import httpx

from app.config import Settings
from app.models import RecognitionResult
from app.providers.base import RecognitionProvider
from app.providers.registry import register_provider

logger = logging.getLogger(__name__)


@register_provider("acrcloud")
class AcrCloudProvider(RecognitionProvider):
    def __init__(self, host: str, access_key: str, access_secret: str, enabled: bool = True):
        super().__init__()
        self.host = host
        self.access_key = access_key
        self.access_secret = access_secret
        self.enabled = enabled

    @classmethod
    def from_settings(cls, settings: Settings) -> "AcrCloudProvider":
        return cls(
            host=settings.acrcloud_host,
            access_key=settings.acrcloud_access_key,
            access_secret=settings.acrcloud_access_secret,
            enabled=settings.acrcloud_enabled,
        )

    async def is_available(self) -> bool:
        return bool(self.enabled and self.host and self.access_key and self.access_secret)

    def _build_signature(self, timestamp: str) -> str:
        string_to_sign = "\n".join(
            ["POST", "/v1/identify", self.access_key, "audio", "1", timestamp]
        )
        sig = hmac.new(
            self.access_secret.encode("utf-8"),
            string_to_sign.encode("utf-8"),
            digestmod=hashlib.sha1,
        ).digest()
        return base64.b64encode(sig).decode("utf-8")

    async def recognize(self, audio_bytes: bytes, sample_rate_hz: int) -> RecognitionResult:
        if not await self.is_available():
            return RecognitionResult(matched=False, provider=self.name)

        timestamp = str(int(time.time()))
        signature = self._build_signature(timestamp)
        data = {
            "access_key": self.access_key,
            "sample_bytes": str(len(audio_bytes)),
            "timestamp": timestamp,
            "signature": signature,
            "data_type": "audio",
            "signature_version": "1",
        }
        files = {"sample": ("sample.wav", audio_bytes, "audio/wav")}

        async with httpx.AsyncClient(timeout=15) as client:
            resp = await client.post(f"https://{self.host}/v1/identify", data=data, files=files)
            resp.raise_for_status()
            payload = resp.json()

        status = payload.get("status", {})
        if status.get("code") != 0:
            return RecognitionResult(matched=False, provider=self.name, raw_provider_response=payload)

        music = payload.get("metadata", {}).get("music", [{}])[0]
        return RecognitionResult(
            matched=True,
            title=music.get("title"),
            artist=", ".join(a.get("name", "") for a in music.get("artists", [])),
            album=music.get("album", {}).get("name"),
            release_date=music.get("release_date"),
            confidence=float(music.get("score", 0)) / 100.0,
            provider=self.name,
            external_ids={k: str(v) for k, v in music.get("external_ids", {}).items()},
            raw_provider_response=payload,
        )
