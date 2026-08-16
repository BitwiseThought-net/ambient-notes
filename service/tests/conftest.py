from __future__ import annotations

import base64

import pytest
from fastapi.testclient import TestClient

from app.config import Settings, get_settings
from app.main import app, get_orchestrator
from app.recognition_service import RecognitionOrchestrator


@pytest.fixture
def settings() -> Settings:
    return Settings(
        api_keys="test-key-123",
        provider_chain="dejavu,ollama",
        ollama_enabled=False,
        acrcloud_enabled=False,
        audd_enabled=False,
        peer_service_urls="",
        min_confidence=0.55,
    )


@pytest.fixture
def client(settings: Settings) -> TestClient:
    app.dependency_overrides[get_settings] = lambda: settings
    app.dependency_overrides[get_orchestrator] = lambda: RecognitionOrchestrator(settings, providers=[])
    with TestClient(app) as c:
        yield c
    app.dependency_overrides.clear()


@pytest.fixture
def auth_headers(settings: Settings) -> dict:
    return {"Authorization": f"Bearer {next(iter(settings.api_key_set))}"}


@pytest.fixture
def sample_audio_b64() -> str:
    return base64.b64encode(b"\x00\x01" * 1000).decode("ascii")
