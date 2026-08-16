# Provider Plugin Architecture

AmbientNotesService recognizes a song by walking an ordered chain of **providers**, each implementing the
same interface. This lets you add, remove, or reorder recognition backends by editing `PROVIDER_CHAIN` in
`.env` and dropping in a Python module — no changes to the request-handling code, and no recompilation.

## The interface

Every provider subclasses `RecognitionProvider` (`app/providers/base.py`):

```python
class RecognitionProvider(ABC):
    name: str  # set automatically by @register_provider

    @abstractmethod
    async def recognize(self, audio_bytes: bytes, sample_rate_hz: int) -> RecognitionResult:
        ...

    async def is_available(self) -> bool:
        return True  # override to add a cheap readiness check

    async def aclose(self) -> None:
        return None  # override to release resources (HTTP clients, DB pools)
```

`recognize()` must never raise for "no match" — return `RecognitionResult(matched=False, provider=self.name)`
instead. Exceptions are reserved for real transport/config failures, which the orchestrator
(`app/recognition_service.py`) catches, logs, and treats as "move to the next provider in the chain."

## Built-in providers

| Name | Module | What it does |
|---|---|---|
| `dejavu` | `dejavu_provider.py` | Matches against a local fingerprint DB you own (see SETUP.md §5). Fast, free, fully local; only knows songs you've fingerprinted. |
| `ollama` | `ollama_provider.py` | Local LLM re-ranking / best-effort guess from a lyric transcript, if supplied via `extra_context`. Always available, low precision on its own. |
| `acrcloud` | `acrcloud_provider.py` | Cloud fallback using the *operator's* ACRCloud project. |
| `audd` | `audd_provider.py` | Cloud fallback using the *operator's* AudD account. |
| `selfhosted_peer` | `selfhosted_peer_provider.py` | Delegates to another AmbientNotesService instance (e.g. a friend's bigger library). One instance is created per URL in `PEER_SERVICE_URLS`. |

## Adding your own provider

1. Create `app/providers/my_provider.py`:

```python
from app.config import Settings
from app.models import RecognitionResult
from app.providers.base import RecognitionProvider
from app.providers.registry import register_provider

@register_provider("my_provider")
class MyProvider(RecognitionProvider):
    def __init__(self, api_key: str):
        super().__init__()
        self.api_key = api_key

    @classmethod
    def from_settings(cls, settings: Settings) -> "MyProvider":
        return cls(api_key=settings.my_provider_api_key)  # add this field to app/config.py

    async def recognize(self, audio_bytes: bytes, sample_rate_hz: int) -> RecognitionResult:
        ...  # call your service, map its response onto RecognitionResult
        return RecognitionResult(matched=True, title="...", confidence=0.9, provider=self.name)
```

2. Add any new settings (API keys, URLs) to `Settings` in `app/config.py`, and document them in
   `.env.example`.
3. Import the module in `app/providers/registry.py::_import_builtin_providers()` (or, for a truly external
   plugin you don't want to fork this repo for, install it as a separate pip package and import it from
   your own `app/providers/__init__.py` override — the registry doesn't care where a provider class comes
   from, only that `@register_provider` ran before `build_provider()` is called).
4. Add `my_provider` to `PROVIDER_CHAIN` in `.env`.
5. Write tests in `tests/test_providers.py` following the existing `respx`-mocked HTTP examples — no live
   network calls in the test suite.

That's the whole contract. The orchestrator, the API endpoint, and the Android app don't need to know your
provider exists beyond its name in the chain.

## Fallback ordering & confidence

`RecognitionOrchestrator.recognize()` tries providers strictly in the order given by `PROVIDER_CHAIN`
(peers from `PEER_SERVICE_URLS` are appended after). For each provider it:

1. Calls `is_available()`; skips (without counting as "tried") if `False` or if it raises.
2. Calls `recognize()`; on exception, logs and moves to the next provider.
3. If the result is `matched=True` **and** `confidence >= MIN_CONFIDENCE`, returns immediately.
4. Otherwise continues to the next provider.
5. If nothing matches confidently, returns `matched=False` with the full list of providers actually
   attempted (`providers_tried` in the API response) so you can debug why.
