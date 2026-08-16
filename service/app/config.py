"""
Central configuration for AmbientNotesService.

All settings are sourced from environment variables (see .env.example).
Using pydantic-settings gives us validation + type coercion for free, and
keeps secrets out of source control.
"""
from __future__ import annotations

from functools import lru_cache
from typing import Literal

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    # --- Server / auth -----------------------------------------------------
    host: str = "0.0.0.0"
    port: int = 8080
    # Shared-secret API key(s) the Android app must present. Comma separated
    # to support rotation (accept old + new key during a rollover window).
    api_keys: str = Field(default="", description="Comma-separated list of valid API keys")
    require_tls: bool = Field(
        default=True,
        description="If true, refuses to serve plaintext HTTP outside of localhost. "
        "Set false only for local dev; production should sit behind a TLS-terminating "
        "reverse proxy (see docs/SECURITY.md).",
    )

    # --- Provider chain ------------------------------------------------------
    # Ordered, comma-separated list of provider names to try, in order, on each
    # recognition request. First provider to return a confident match wins.
    provider_chain: str = Field(default="dejavu,ollama")

    # Dejavu (local audio fingerprinting DB)
    dejavu_db_host: str = "dejavu-db"
    dejavu_db_port: int = 3306
    dejavu_db_name: str = "dejavu"
    dejavu_db_user: str = "dejavu"
    dejavu_db_password: str = "changeme"

    # Ollama (local LLM used as a last-resort / assistive classifier, e.g. for
    # lyric-based guesses transcribed via a speech model, or re-ranking
    # low-confidence fingerprint matches)
    ollama_base_url: str = "http://ollama:11434"
    ollama_model: str = "llama3.1"
    ollama_enabled: bool = True

    # ACRCloud (optional fallback to a cloud provider using the *server's*
    # credentials, distinct from the Android app's own ACRCloud account)
    acrcloud_enabled: bool = False
    acrcloud_host: str = ""
    acrcloud_access_key: str = ""
    acrcloud_access_secret: str = ""

    # AudD (optional cloud fallback)
    audd_enabled: bool = False
    audd_api_token: str = ""

    # Peer AmbientNotesService instances to fall back to (comma separated URLs)
    peer_service_urls: str = ""

    # --- Misc ------------------------------------------------------------
    log_level: Literal["DEBUG", "INFO", "WARNING", "ERROR"] = "INFO"
    max_audio_seconds: int = 20
    min_confidence: float = 0.55

    @property
    def api_key_set(self) -> set[str]:
        return {k.strip() for k in self.api_keys.split(",") if k.strip()}

    @property
    def provider_chain_list(self) -> list[str]:
        return [p.strip() for p in self.provider_chain.split(",") if p.strip()]

    @property
    def peer_service_url_list(self) -> list[str]:
        return [u.strip() for u in self.peer_service_urls.split(",") if u.strip()]


@lru_cache
def get_settings() -> Settings:
    return Settings()
