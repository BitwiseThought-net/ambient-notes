"""
Local audio-fingerprinting provider backed by Dejavu
(https://github.com/worldveil/dejavu-style fingerprint DB).

Dejavu matches short audio clips against a pre-fingerprinted library the
self-hoster has ingested (e.g. their own music collection). It is fast, free,
and fully local, but can only recognize songs it has been fingerprinted with.
That's why it's typically first in the provider chain, with cloud/LLM
fallbacks behind it for songs outside the local library.

This module intentionally isolates the `dejavu` import so the rest of the
service can be unit-tested without the (heavier, audio-processing) dependency
installed. If the optional dependency is missing, `is_available()` returns
False and the orchestrator will just skip to the next provider.
"""
from __future__ import annotations

import io
import logging

from app.config import Settings
from app.models import RecognitionResult
from app.providers.base import RecognitionProvider
from app.providers.registry import register_provider

logger = logging.getLogger(__name__)


@register_provider("dejavu")
class DejavuProvider(RecognitionProvider):
    def __init__(self, db_config: dict, min_confidence: float = 0.55):
        super().__init__(db_config=db_config)
        self.db_config = db_config
        self.min_confidence = min_confidence
        self._djv = None

    @classmethod
    def from_settings(cls, settings: Settings) -> "DejavuProvider":
        return cls(
            db_config={
                "database": {
                    "host": settings.dejavu_db_host,
                    "port": settings.dejavu_db_port,
                    "user": settings.dejavu_db_user,
                    "password": settings.dejavu_db_password,
                    "database": settings.dejavu_db_name,
                }
            },
            min_confidence=settings.min_confidence,
        )

    def _get_engine(self):
        if self._djv is None:
            try:
                from dejavu import Dejavu  # type: ignore
            except ImportError as exc:  # pragma: no cover - exercised via availability test
                raise RuntimeError(
                    "The 'dejavu' package is not installed. It is included in the Docker "
                    "image by default; if running locally, `pip install -r requirements.txt`."
                ) from exc
            self._djv = Dejavu(self.db_config)
        return self._djv

    async def is_available(self) -> bool:
        try:
            self._get_engine()
            return True
        except Exception:  # noqa: BLE001
            return False

    async def recognize(self, audio_bytes: bytes, sample_rate_hz: int) -> RecognitionResult:
        try:
            djv = self._get_engine()
        except RuntimeError:
            return RecognitionResult(matched=False, provider=self.name)

        match = djv.recognize_bytes(io.BytesIO(audio_bytes))  # pragma: no cover - I/O heavy path
        if not match:
            return RecognitionResult(matched=False, provider=self.name)

        confidence = float(match.get("confidence", 0.0))
        if confidence < self.min_confidence:
            return RecognitionResult(matched=False, provider=self.name, confidence=confidence)

        return RecognitionResult(
            matched=True,
            title=match.get("song_name"),
            confidence=confidence,
            provider=self.name,
            raw_provider_response=match,
        )
