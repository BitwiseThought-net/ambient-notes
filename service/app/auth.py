from __future__ import annotations

from fastapi import Depends, Header, HTTPException, status

from app.config import Settings, get_settings


async def require_api_key(
    authorization: str | None = Header(default=None),
    settings: Settings = Depends(get_settings),
) -> str:
    valid_keys = settings.api_key_set

    if not valid_keys:
        # Explicit opt-in only: an operator who hasn't set API_KEYS yet is
        # almost certainly still in initial setup. We refuse open access
        # rather than silently running unauthenticated in production.
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Server has no API_KEYS configured. Set API_KEYS in .env before accepting requests.",
        )

    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Missing or malformed Authorization header")

    token = authorization.removeprefix("Bearer ").strip()
    if token not in valid_keys:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid API key")

    return token
