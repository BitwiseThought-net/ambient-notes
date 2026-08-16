from __future__ import annotations

import pytest

from app.config import Settings
from app.models import RecognitionResult
from app.providers.base import RecognitionProvider
from app.recognition_service import RecognitionOrchestrator


class FakeProvider(RecognitionProvider):
    def __init__(self, name: str, available: bool = True, result: RecognitionResult | None = None, raises: bool = False):
        super().__init__()
        self.name = name
        self._available = available
        self._result = result or RecognitionResult(matched=False, provider=name)
        self._raises = raises
        self.recognize_calls = 0

    async def is_available(self) -> bool:
        return self._available

    async def recognize(self, audio_bytes: bytes, sample_rate_hz: int) -> RecognitionResult:
        self.recognize_calls += 1
        if self._raises:
            raise RuntimeError("boom")
        return self._result


@pytest.fixture
def settings() -> Settings:
    return Settings(min_confidence=0.6)


@pytest.mark.asyncio
async def test_first_confident_match_wins(settings):
    match = RecognitionResult(matched=True, title="Song A", confidence=0.9, provider="p1")
    p1 = FakeProvider("p1", result=match)
    p2 = FakeProvider("p2", result=RecognitionResult(matched=True, title="Song B", confidence=0.9, provider="p2"))
    orchestrator = RecognitionOrchestrator(settings, providers=[p1, p2])

    result, tried = await orchestrator.recognize(b"audio", 16000)

    assert result.matched is True
    assert result.title == "Song A"
    assert tried == ["p1"]
    assert p2.recognize_calls == 0  # short-circuited


@pytest.mark.asyncio
async def test_low_confidence_falls_through_to_next_provider(settings):
    weak = RecognitionResult(matched=True, title="Maybe", confidence=0.2, provider="p1")
    strong = RecognitionResult(matched=True, title="Definitely", confidence=0.95, provider="p2")
    p1 = FakeProvider("p1", result=weak)
    p2 = FakeProvider("p2", result=strong)
    orchestrator = RecognitionOrchestrator(settings, providers=[p1, p2])

    result, tried = await orchestrator.recognize(b"audio", 16000)

    assert result.title == "Definitely"
    assert tried == ["p1", "p2"]


@pytest.mark.asyncio
async def test_unavailable_provider_is_skipped_and_not_counted(settings):
    p1 = FakeProvider("p1", available=False)
    p2 = FakeProvider("p2", result=RecognitionResult(matched=True, title="X", confidence=0.9, provider="p2"))
    orchestrator = RecognitionOrchestrator(settings, providers=[p1, p2])

    result, tried = await orchestrator.recognize(b"audio", 16000)

    assert result.matched is True
    assert tried == ["p2"]  # p1 never counted as "tried" since it was unavailable


@pytest.mark.asyncio
async def test_provider_exception_does_not_abort_chain(settings):
    p1 = FakeProvider("p1", raises=True)
    p2 = FakeProvider("p2", result=RecognitionResult(matched=True, title="X", confidence=0.9, provider="p2"))
    orchestrator = RecognitionOrchestrator(settings, providers=[p1, p2])

    result, tried = await orchestrator.recognize(b"audio", 16000)

    assert result.matched is True
    assert tried == ["p1", "p2"]


@pytest.mark.asyncio
async def test_no_providers_match_returns_unmatched(settings):
    p1 = FakeProvider("p1")
    orchestrator = RecognitionOrchestrator(settings, providers=[p1])

    result, tried = await orchestrator.recognize(b"audio", 16000)

    assert result.matched is False
    assert tried == ["p1"]


@pytest.mark.asyncio
async def test_requested_providers_filters_chain(settings):
    p1 = FakeProvider("p1", result=RecognitionResult(matched=True, title="A", confidence=0.9, provider="p1"))
    p2 = FakeProvider("p2", result=RecognitionResult(matched=True, title="B", confidence=0.9, provider="p2"))
    orchestrator = RecognitionOrchestrator(settings, providers=[p1, p2])

    result, tried = await orchestrator.recognize(b"audio", 16000, requested_providers=["p2"])

    assert tried == ["p2"]
    assert result.title == "B"


def test_default_chain_built_from_settings_provider_chain():
    settings = Settings(provider_chain="audd,ollama", audd_enabled=True, audd_api_token="tok")
    orchestrator = RecognitionOrchestrator(settings)
    names = [p.name for p in orchestrator._providers]
    assert names == ["audd", "ollama"]


def test_default_chain_skips_unknown_provider_name(caplog):
    settings = Settings(provider_chain="audd,not-a-real-provider")
    orchestrator = RecognitionOrchestrator(settings)
    names = [p.name for p in orchestrator._providers]
    assert names == ["audd"]


def test_default_chain_appends_peer_providers():
    settings = Settings(provider_chain="audd", peer_service_urls="http://peer-a,http://peer-b")
    orchestrator = RecognitionOrchestrator(settings)
    peer_urls = [p.peer_url for p in orchestrator._providers if p.name == "selfhosted_peer"]
    assert peer_urls == ["http://peer-a", "http://peer-b"]


@pytest.mark.asyncio
async def test_availability_check_exception_skips_provider(settings):
    class ExplodingAvailability(FakeProvider):
        async def is_available(self):
            raise RuntimeError("network down")

    p1 = ExplodingAvailability("p1")
    p2 = FakeProvider("p2", result=RecognitionResult(matched=True, title="X", confidence=0.9, provider="p2"))
    orchestrator = RecognitionOrchestrator(settings, providers=[p1, p2])

    result, tried = await orchestrator.recognize(b"audio", 16000)

    assert result.matched is True
    assert tried == ["p2"]


@pytest.mark.asyncio
async def test_aclose_closes_all_providers(settings):
    closed = []

    class ClosingProvider(FakeProvider):
        async def aclose(self):
            closed.append(self.name)

    p1 = ClosingProvider("p1")
    orchestrator = RecognitionOrchestrator(settings, providers=[p1])
    await orchestrator.aclose()

    assert closed == ["p1"]
