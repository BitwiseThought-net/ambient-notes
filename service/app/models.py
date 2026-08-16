from __future__ import annotations

from enum import Enum
from typing import Optional

from pydantic import BaseModel, Field


class AudioFormat(str, Enum):
    PCM16_16K = "pcm16_16k"
    WAV = "wav"
    MP3 = "mp3"
    AAC = "aac"
    FLAC = "flac"


class RecognizeRequest(BaseModel):
    """Incoming payload from the Android app. Audio is base64-encoded in the
    JSON body for simplicity; see docs/API.md for the multipart alternative
    used for larger clips."""

    audio_base64: str = Field(..., description="Base64-encoded audio sample")
    audio_format: AudioFormat = AudioFormat.PCM16_16K
    sample_rate_hz: int = 16000
    device_id: Optional[str] = Field(default=None, description="Opaque client identifier for logging/rate limiting")
    requested_providers: Optional[list[str]] = Field(
        default=None,
        description="Optional override of the server's configured provider chain, for this request only.",
    )


class RecognitionResult(BaseModel):
    matched: bool
    title: Optional[str] = None
    artist: Optional[str] = None
    album: Optional[str] = None
    release_date: Optional[str] = None
    confidence: float = 0.0
    provider: Optional[str] = None
    external_ids: dict[str, str] = Field(default_factory=dict)
    raw_provider_response: Optional[dict] = None


class RecognizeResponse(BaseModel):
    result: RecognitionResult
    providers_tried: list[str] = Field(default_factory=list)
    processing_time_ms: int = 0


class HealthResponse(BaseModel):
    status: str
    providers_configured: list[str]
