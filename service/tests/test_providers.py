from __future__ import annotations

import httpx
import pytest
import respx

from app.config import Settings
from app.providers.acrcloud_provider import AcrCloudProvider
from app.providers.audd_provider import AUDD_ENDPOINT, AudDProvider
from app.providers.dejavu_provider import DejavuProvider
from app.providers.ollama_provider import OllamaProvider
from app.providers.selfhosted_peer_provider import SelfHostedPeerProvider


# --- AudD -------------------------------------------------------------
@pytest.mark.asyncio
async def test_audd_disabled_when_no_token():
    provider = AudDProvider(api_token="", enabled=True)
    assert await provider.is_available() is False
    result = await provider.recognize(b"audio", 16000)
    assert result.matched is False


@pytest.mark.asyncio
@respx.mock
async def test_audd_successful_match():
    respx.post(AUDD_ENDPOINT).mock(
        return_value=httpx.Response(
            200,
            json={
                "status": "success",
                "result": {
                    "title": "Test Song",
                    "artist": "Test Artist",
                    "album": "Test Album",
                    "release_date": "2020-01-01",
                    "spotify": {"id": "abc123"},
                },
            },
        )
    )
    provider = AudDProvider(api_token="tok", enabled=True)
    result = await provider.recognize(b"audio-bytes", 16000)
    assert result.matched is True
    assert result.title == "Test Song"
    assert result.external_ids["spotify"] == "abc123"


@pytest.mark.asyncio
@respx.mock
async def test_audd_no_match():
    respx.post(AUDD_ENDPOINT).mock(return_value=httpx.Response(200, json={"status": "success", "result": None}))
    provider = AudDProvider(api_token="tok", enabled=True)
    result = await provider.recognize(b"audio-bytes", 16000)
    assert result.matched is False


# --- ACRCloud -----------------------------------------------------------
@pytest.mark.asyncio
async def test_acrcloud_unavailable_when_not_configured():
    provider = AcrCloudProvider(host="", access_key="", access_secret="", enabled=True)
    assert await provider.is_available() is False


@pytest.mark.asyncio
@respx.mock
async def test_acrcloud_successful_match():
    respx.post("https://example-acr-host/v1/identify").mock(
        return_value=httpx.Response(
            200,
            json={
                "status": {"code": 0},
                "metadata": {
                    "music": [
                        {
                            "title": "ACR Song",
                            "artists": [{"name": "ACR Artist"}],
                            "album": {"name": "ACR Album"},
                            "score": 95,
                            "external_ids": {"isrc": "US123"},
                        }
                    ]
                },
            },
        )
    )
    provider = AcrCloudProvider(
        host="example-acr-host", access_key="key", access_secret="secret", enabled=True
    )
    result = await provider.recognize(b"audio-bytes", 16000)
    assert result.matched is True
    assert result.title == "ACR Song"
    assert result.confidence == pytest.approx(0.95)


@pytest.mark.asyncio
@respx.mock
async def test_acrcloud_no_match_status_code():
    respx.post("https://example-acr-host/v1/identify").mock(
        return_value=httpx.Response(200, json={"status": {"code": 1001, "msg": "No result"}})
    )
    provider = AcrCloudProvider(host="example-acr-host", access_key="key", access_secret="secret", enabled=True)
    result = await provider.recognize(b"audio-bytes", 16000)
    assert result.matched is False


# --- Ollama ---------------------------------------------------------------
@pytest.mark.asyncio
async def test_ollama_no_transcript_hint_returns_unmatched():
    provider = OllamaProvider(base_url="http://localhost:11434", model="llama3.1", enabled=True)
    result = await provider.recognize(b"audio", 16000)
    assert result.matched is False


@pytest.mark.asyncio
@respx.mock
async def test_ollama_disabled_is_unavailable():
    provider = OllamaProvider(base_url="http://localhost:11434", model="llama3.1", enabled=False)
    assert await provider.is_available() is False


@pytest.mark.asyncio
@respx.mock
async def test_ollama_available_checks_tags_endpoint():
    respx.get("http://localhost:11434/api/tags").mock(return_value=httpx.Response(200, json={}))
    provider = OllamaProvider(base_url="http://localhost:11434", model="llama3.1", enabled=True)
    assert await provider.is_available() is True


@pytest.mark.asyncio
@respx.mock
async def test_ollama_transcript_guess_success():
    respx.post("http://localhost:11434/api/generate").mock(
        return_value=httpx.Response(
            200, json={"response": '{"title": "Guessed Song", "artist": "Guessed Artist", "confidence": 0.8}'}
        )
    )
    provider = OllamaProvider(base_url="http://localhost:11434", model="llama3.1", enabled=True)
    provider.config["extra_context"] = {"transcript": "some lyric fragment"}
    result = await provider.recognize(b"audio", 16000)
    assert result.matched is True
    assert result.title == "Guessed Song"


# --- Dejavu (import-guarded) --------------------------------------------
@pytest.mark.asyncio
async def test_dejavu_unavailable_when_package_missing():
    provider = DejavuProvider(db_config={"database": {}})
    # In the test environment the optional `dejavu` package is not installed,
    # so availability should gracefully report False rather than raising.
    assert await provider.is_available() is False
    result = await provider.recognize(b"audio", 16000)
    assert result.matched is False


def test_dejavu_from_settings_builds_expected_config():
    settings = Settings(
        dejavu_db_host="myhost", dejavu_db_port=1234, dejavu_db_name="db", dejavu_db_user="u", dejavu_db_password="p"
    )
    provider = DejavuProvider.from_settings(settings)
    assert provider.db_config["database"]["host"] == "myhost"
    assert provider.db_config["database"]["port"] == 1234


# --- Peer service ---------------------------------------------------------
@pytest.mark.asyncio
async def test_peer_provider_unavailable_without_url():
    provider = SelfHostedPeerProvider(peer_url="")
    assert await provider.is_available() is False


@pytest.mark.asyncio
@respx.mock
async def test_peer_provider_delegates_to_remote_service():
    respx.post("http://peer.local/api/v1/recognize").mock(
        return_value=httpx.Response(
            200,
            json={
                "result": {"matched": True, "title": "Peer Song", "confidence": 0.99, "provider": "dejavu"},
                "providers_tried": ["dejavu"],
                "processing_time_ms": 12,
            },
        )
    )
    provider = SelfHostedPeerProvider(peer_url="http://peer.local", api_key="peer-key")
    result = await provider.recognize(b"audio", 16000)
    assert result.matched is True
    assert result.title == "Peer Song"
    assert result.provider == "selfhosted_peer:http://peer.local"
