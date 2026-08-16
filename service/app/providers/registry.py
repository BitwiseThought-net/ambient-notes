"""
Plugin registry for recognition providers.

Providers register themselves here by name. `recognition_service.py` reads
the configured provider_chain (a list of names) and looks each one up in
this registry -- so adding/removing a fallback service is a config change
(`PROVIDER_CHAIN=dejavu,ollama,acrcloud`) plus dropping in the provider
module. Nothing else needs to change or be recompiled.
"""
from __future__ import annotations

from typing import Callable

from app.config import Settings
from app.providers.base import RecognitionProvider

# name -> factory(settings) -> RecognitionProvider instance
_FACTORIES: dict[str, Callable[[Settings], RecognitionProvider]] = {}


def register_provider(name: str):
    """Class decorator that registers a RecognitionProvider factory under `name`."""

    def _decorator(cls: type[RecognitionProvider]):
        def _factory(settings: Settings) -> RecognitionProvider:
            return cls.from_settings(settings)  # type: ignore[attr-defined]

        _FACTORIES[name] = _factory
        cls.name = name
        return cls

    return _decorator


def build_provider(name: str, settings: Settings) -> RecognitionProvider:
    if name not in _FACTORIES:
        raise KeyError(
            f"Unknown recognition provider '{name}'. Known providers: {sorted(_FACTORIES)}. "
            "Check PROVIDER_CHAIN in your .env, or add a new provider module."
        )
    return _FACTORIES[name](settings)


def available_provider_names() -> list[str]:
    return sorted(_FACTORIES)


def _import_builtin_providers() -> None:
    """Import provider modules so their @register_provider decorators run.
    Keeping this import in one place (rather than in __init__.py at module
    load time) makes unit testing the registry in isolation easier."""
    from app.providers import (  # noqa: F401
        acrcloud_provider,
        audd_provider,
        dejavu_provider,
        ollama_provider,
        selfhosted_peer_provider,
    )


_import_builtin_providers()
