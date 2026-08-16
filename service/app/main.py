from __future__ import annotations

import base64
import binascii
import logging
import time
from contextlib import asynccontextmanager

from fastapi import Depends, FastAPI, HTTPException, status
from fastapi.middleware.cors import CORSMiddleware

from app.auth import require_api_key
from app.config import Settings, get_settings
from app.logging_config import configure_logging
from app.models import HealthResponse, RecognizeRequest, RecognizeResponse
from app.providers.registry import available_provider_names
from app.recognition_service import RecognitionOrchestrator

logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(_app: FastAPI):
    settings = get_settings()
    configure_logging(settings.log_level)
    logger.info("AmbientNotesService starting. Provider chain: %s", settings.provider_chain_list)
    yield
    if _orchestrator is not None:
        await _orchestrator.aclose()


app = FastAPI(
    title="AmbientNotesService",
    description="Self-hosted audio recognition backend for the AmbientNotes Android app.",
    version="1.0.0",
    lifespan=lifespan,
)

# CORS is permissive by default because this API is not browser-facing (the
# Android app talks to it directly); tighten via a reverse proxy if you also
# expose a browser client. See docs/SECURITY.md.
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["GET", "POST"],
    allow_headers=["*"],
)

_orchestrator: RecognitionOrchestrator | None = None


def get_orchestrator(settings: Settings = Depends(get_settings)) -> RecognitionOrchestrator:
    global _orchestrator
    if _orchestrator is None:
        _orchestrator = RecognitionOrchestrator(settings)
    return _orchestrator


@app.get("/api/v1/health", response_model=HealthResponse, tags=["meta"])
async def health(settings: Settings = Depends(get_settings)) -> HealthResponse:
    return HealthResponse(status="ok", providers_configured=settings.provider_chain_list)


@app.get("/api/v1/providers", tags=["meta"])
async def list_providers() -> dict:
    return {"available_provider_types": available_provider_names()}


@app.post(
    "/api/v1/recognize",
    response_model=RecognizeResponse,
    tags=["recognition"],
    dependencies=[Depends(require_api_key)],
)
async def recognize(
    body: RecognizeRequest,
    settings: Settings = Depends(get_settings),
    orchestrator: RecognitionOrchestrator = Depends(get_orchestrator),
) -> RecognizeResponse:
    try:
        audio_bytes = base64.b64decode(body.audio_base64, validate=True)
    except (binascii.Error, ValueError) as exc:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="audio_base64 is not valid base64") from exc

    if not audio_bytes:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="audio_base64 decoded to empty bytes")

    max_bytes = settings.max_audio_seconds * body.sample_rate_hz * 2  # rough bound for 16-bit PCM
    if len(audio_bytes) > max_bytes * 4:  # generous multiplier to allow for compressed formats decoding larger
        raise HTTPException(
            status_code=status.HTTP_413_REQUEST_ENTITY_TOO_LARGE,
            detail=f"Audio sample exceeds max_audio_seconds={settings.max_audio_seconds}",
        )

    start = time.monotonic()
    result, providers_tried = await orchestrator.recognize(
        audio_bytes, body.sample_rate_hz, requested_providers=body.requested_providers
    )
    elapsed_ms = int((time.monotonic() - start) * 1000)

    return RecognizeResponse(result=result, providers_tried=providers_tried, processing_time_ms=elapsed_ms)
