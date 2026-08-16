from __future__ import annotations

import logging

from app.config import Settings
from app.models import RecognitionResult
from app.providers.base import RecognitionProvider
from app.providers.registry import build_provider

logger = logging.getLogger(__name__)


class RecognitionOrchestrator:
    """Walks the configured provider chain in order, returning the first
    confident match. Providers that error out or report themselves
    unavailable are skipped and logged, never fatal to the request."""

    def __init__(self, settings: Settings, providers: list[RecognitionProvider] | None = None):
        self.settings = settings
        self._providers = providers if providers is not None else self._build_default_chain(settings)

    @staticmethod
    def _build_default_chain(settings: Settings) -> list[RecognitionProvider]:
        chain: list[RecognitionProvider] = []
        for name in settings.provider_chain_list:
            try:
                chain.append(build_provider(name, settings))
            except KeyError:
                logger.warning("Skipping unknown provider in PROVIDER_CHAIN: %s", name)
        # Fan out one provider instance per configured peer, appended after
        # the primary chain so local/cloud options are always tried first.
        for peer_url in settings.peer_service_url_list:
            from app.providers.selfhosted_peer_provider import SelfHostedPeerProvider

            chain.append(SelfHostedPeerProvider(peer_url=peer_url))
        return chain

    async def recognize(
        self, audio_bytes: bytes, sample_rate_hz: int, requested_providers: list[str] | None = None
    ) -> tuple[RecognitionResult, list[str]]:
        providers_tried: list[str] = []
        active_chain = self._providers
        if requested_providers:
            wanted = set(requested_providers)
            active_chain = [p for p in self._providers if p.name in wanted]

        for provider in active_chain:
            try:
                if not await provider.is_available():
                    logger.info("Provider %s unavailable, skipping", provider.name)
                    continue
            except Exception:  # noqa: BLE001
                logger.exception("Provider %s availability check raised, skipping", provider.name)
                continue

            providers_tried.append(provider.name)
            try:
                result = await provider.recognize(audio_bytes, sample_rate_hz)
            except Exception:  # noqa: BLE001
                logger.exception("Provider %s raised during recognize(), trying next", provider.name)
                continue

            if result.matched and result.confidence >= self.settings.min_confidence:
                return result, providers_tried

        return RecognitionResult(matched=False, provider=None), providers_tried

    async def aclose(self) -> None:
        for provider in self._providers:
            await provider.aclose()
