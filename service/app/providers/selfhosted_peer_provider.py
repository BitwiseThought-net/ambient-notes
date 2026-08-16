"""
Fallback to another AmbientNotesService instance (e.g. a friend's larger
Dejavu library, or a beefier GPU box running Ollama). Configured via
PEER_SERVICE_URLS. Each configured peer becomes its own registered provider
instance at startup (see recognition_service.py), so multiple peers can be
tried in order, each attributed distinctly in `providers_tried`.
"""
from __future__ import annotations

import base64
import logging

import httpx

from app.config import Settings
from app.models import RecognitionResult
from app.providers.base import RecognitionProvider
from app.providers.registry import register_provider

logger = logging.getLogger(__name__)


@register_provider("selfhosted_peer")
class SelfHostedPeerProvider(RecognitionProvider):
    def __init__(self, peer_url: str, api_key: str = ""):
        super().__init__()
        self.peer_url = peer_url.rstrip("/")
        self.api_key = api_key

    @classmethod
    def from_settings(cls, settings: Settings) -> "SelfHostedPeerProvider":
        # Default factory targets the first configured peer; recognition_service
        # constructs one instance per URL in peer_service_url_list directly.
        urls = settings.peer_service_url_list
        return cls(peer_url=urls[0] if urls else "")

    async def is_available(self) -> bool:
        return bool(self.peer_url)

    async def recognize(self, audio_bytes: bytes, sample_rate_hz: int) -> RecognitionResult:
        if not self.peer_url:
            return RecognitionResult(matched=False, provider=self.name)

        headers = {"Authorization": f"Bearer {self.api_key}"} if self.api_key else {}
        body = {
            "audio_base64": base64.b64encode(audio_bytes).decode("ascii"),
            "audio_format": "wav",
            "sample_rate_hz": sample_rate_hz,
        }
        async with httpx.AsyncClient(timeout=20) as client:
            resp = await client.post(f"{self.peer_url}/api/v1/recognize", json=body, headers=headers)
            resp.raise_for_status()
            payload = resp.json()

        result = payload.get("result", {})
        result["provider"] = f"{self.name}:{self.peer_url}"
        return RecognitionResult(**result)
