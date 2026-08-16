# API Reference - AmbientNotesService

Base URL: `https://<your-host>:8080` (see SECURITY.md for why this should be HTTPS/VPN, not plain HTTP, in
production).

## Authentication

All endpoints except `/api/v1/health` and `/api/v1/providers` require:

```
Authorization: Bearer <API_KEYS value>
```

## `POST /api/v1/recognize`

Identify a song from a short audio sample.

**Request body**

```json
{
  "audio_base64": "<base64-encoded audio bytes>",
  "audio_format": "pcm16_16k",
  "sample_rate_hz": 16000,
  "device_id": "optional-opaque-client-id",
  "requested_providers": null
}
```

| Field | Type | Notes |
|---|---|---|
| `audio_base64` | string | Required. Base64-encoded raw audio. |
| `audio_format` | enum | One of `pcm16_16k`, `wav`, `mp3`, `aac`, `flac`. Default `pcm16_16k`. |
| `sample_rate_hz` | int | Default `16000`. |
| `device_id` | string \| null | Optional; echoed into server logs for debugging, not stored. |
| `requested_providers` | string[] \| null | Optional per-request override restricting which configured providers to try, e.g. `["dejavu"]` to skip cloud fallback for this one call. |

**Response `200`**

```json
{
  "result": {
    "matched": true,
    "title": "Song Title",
    "artist": "Artist Name",
    "album": "Album Name",
    "release_date": "2020-01-01",
    "confidence": 0.93,
    "provider": "dejavu",
    "external_ids": {"spotify": "abc123"},
    "raw_provider_response": { "...": "..." }
  },
  "providers_tried": ["dejavu"],
  "processing_time_ms": 184
}
```

If nothing matched confidently, `result.matched` is `false` and `providers_tried` lists everything that was
actually attempted (useful for debugging a misconfigured chain).

**Errors**

| Status | Meaning |
|---|---|
| `400` | `audio_base64` missing/invalid, or decoded to zero bytes |
| `401` | Missing/malformed/invalid `Authorization` header |
| `413` | Audio sample exceeds `MAX_AUDIO_SECONDS` |
| `503` | Server has no `API_KEYS` configured (operator setup incomplete) |

## `GET /api/v1/health`

Unauthenticated. Returns `{"status": "ok", "providers_configured": ["dejavu", "ollama"]}`. Suitable for an
uptime monitor or the Docker `HEALTHCHECK`.

## `GET /api/v1/providers`

Unauthenticated. Returns the list of provider *types* built into this deployment (not the same as the
active chain) - useful when writing your `.env` to see valid `PROVIDER_CHAIN` values.
