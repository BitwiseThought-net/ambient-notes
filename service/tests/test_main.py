from __future__ import annotations

import base64


def test_health_ok(client):
    resp = client.get("/api/v1/health")
    assert resp.status_code == 200
    body = resp.json()
    assert body["status"] == "ok"
    assert "dejavu" in body["providers_configured"]


def test_list_providers(client):
    resp = client.get("/api/v1/providers")
    assert resp.status_code == 200
    names = resp.json()["available_provider_types"]
    for expected in ("dejavu", "acrcloud", "audd", "ollama", "selfhosted_peer"):
        assert expected in names


def test_recognize_requires_auth(client, sample_audio_b64):
    resp = client.post("/api/v1/recognize", json={"audio_base64": sample_audio_b64})
    assert resp.status_code == 401


def test_recognize_rejects_bad_key(client, sample_audio_b64):
    resp = client.post(
        "/api/v1/recognize",
        json={"audio_base64": sample_audio_b64},
        headers={"Authorization": "Bearer wrong-key"},
    )
    assert resp.status_code == 401


def test_recognize_rejects_malformed_base64(client, auth_headers):
    resp = client.post(
        "/api/v1/recognize",
        json={"audio_base64": "not-valid-base64!!!"},
        headers=auth_headers,
    )
    assert resp.status_code == 400


def test_recognize_rejects_empty_audio(client, auth_headers):
    resp = client.post(
        "/api/v1/recognize",
        json={"audio_base64": base64.b64encode(b"").decode()},
        headers=auth_headers,
    )
    assert resp.status_code == 400


def test_recognize_no_match_with_empty_provider_chain(client, auth_headers, sample_audio_b64):
    # conftest wires an orchestrator with an empty provider list, so this
    # exercises the "no provider could confirm a match" path end-to-end.
    resp = client.post(
        "/api/v1/recognize",
        json={"audio_base64": sample_audio_b64},
        headers=auth_headers,
    )
    assert resp.status_code == 200
    body = resp.json()
    assert body["result"]["matched"] is False
    assert body["providers_tried"] == []


def test_recognize_missing_api_keys_configured(client, sample_audio_b64, settings):
    settings.api_keys = ""
    resp = client.post(
        "/api/v1/recognize",
        json={"audio_base64": sample_audio_b64},
        headers={"Authorization": "Bearer anything"},
    )
    assert resp.status_code == 503


def test_recognize_audio_too_large(client, auth_headers, settings):
    settings.max_audio_seconds = 1
    huge_audio = base64.b64encode(b"\x00" * 300_000).decode()
    resp = client.post(
        "/api/v1/recognize",
        json={"audio_base64": huge_audio, "sample_rate_hz": 16000},
        headers=auth_headers,
    )
    assert resp.status_code == 413
