"""
Drop-in provider interface.

Every recognition backend (local fingerprinting, local LLM-assisted, or a
cloud fallback) implements `RecognitionProvider`. New providers can be added
by dropping a module into `app/providers/`, subclassing `RecognitionProvider`,
and registering it in `PROVIDER_REGISTRY` (see registry.py) -- no changes to
`recognition_service.py` are required, and nothing needs recompiling since
this is plain Python.
"""
from __future__ import annotations

from abc import ABC, abstractmethod

from app.models import RecognitionResult


class RecognitionProvider(ABC):
    """Base class all recognition backends must implement."""

    #: Short, unique, lowercase identifier used in provider_chain config
    name: str = "base"

    def __init__(self, **kwargs):
        self.config = kwargs

    @abstractmethod
    async def recognize(self, audio_bytes: bytes, sample_rate_hz: int) -> RecognitionResult:
        """Attempt to identify the song from raw audio bytes.

        Implementations must never raise for "no match" -- they should return
        a RecognitionResult(matched=False, provider=self.name). Exceptions
        are reserved for actual transport/config errors, which the
        orchestrator will catch and log before moving to the next provider
        in the chain.
        """
        raise NotImplementedError

    async def is_available(self) -> bool:
        """Cheap readiness check (e.g. can we reach the DB / API). Providers
        that are unavailable are skipped rather than treated as a match
        failure."""
        return True

    async def aclose(self) -> None:
        """Optional cleanup hook (closing HTTP clients, DB pools, etc.)."""
        return None
