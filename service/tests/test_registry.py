from __future__ import annotations

import pytest

from app.config import Settings
from app.providers import registry


def test_available_provider_names_includes_builtins():
    names = registry.available_provider_names()
    assert names == sorted(names)
    for expected in ("acrcloud", "audd", "dejavu", "ollama", "selfhosted_peer"):
        assert expected in names


def test_build_provider_unknown_raises():
    with pytest.raises(KeyError, match="Unknown recognition provider"):
        registry.build_provider("not-a-real-provider", Settings())


def test_build_provider_returns_correct_type():
    provider = registry.build_provider("audd", Settings(audd_api_token="tok", audd_enabled=True))
    assert provider.name == "audd"


def test_register_provider_sets_name_attribute():
    from app.providers.base import RecognitionProvider

    @registry.register_provider("_test_only")
    class _TestProvider(RecognitionProvider):
        @classmethod
        def from_settings(cls, settings):
            return cls()

        async def recognize(self, audio_bytes, sample_rate_hz):
            raise NotImplementedError

    assert _TestProvider.name == "_test_only"
    assert "_test_only" in registry.available_provider_names()
