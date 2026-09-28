"""Check the provider boundary without sending images or API keys to the network."""

import asyncio
import base64
import json

import httpx
import pytest
from pydantic import ValidationError

from app.config import Settings
from app.openrouter_client import OpenRouterClient
from app.vision_client import make_vision_client


def test_openrouter_requires_key_and_model():
    assert Settings(_env_file=None).ai_provider == "ollama"
    with pytest.raises(ValidationError, match="OPENCOOK_OPENROUTER_API_KEY"):
        Settings(ai_provider="openrouter", _env_file=None)


def test_provider_selection(monkeypatch):
    monkeypatch.setattr("app.vision_client.get_settings", lambda: Settings(_env_file=None))
    assert type(make_vision_client()).__name__ == "OllamaClient"
    settings = Settings(ai_provider="openrouter", openrouter_api_key="private-key",
                        openrouter_model="qwen/qwen-2.5-vl-7b-instruct", _env_file=None)
    monkeypatch.setattr("app.vision_client.get_settings", lambda: settings)
    monkeypatch.setattr("app.openrouter_client.get_settings", lambda: settings)
    assert isinstance(make_vision_client(), OpenRouterClient)
    assert "private-key" not in repr(settings)


def test_openrouter_sends_private_jpeg_and_returns_text(monkeypatch):
    settings = Settings(ai_provider="openrouter", openrouter_api_key="private-key",
                        openrouter_model="example/vision", _env_file=None)
    monkeypatch.setattr("app.openrouter_client.get_settings", lambda: settings)
    requests = []

    def respond(request: httpx.Request) -> httpx.Response:
        requests.append(request)
        assert request.url == "https://openrouter.ai/api/v1/chat/completions"
        assert request.headers["Authorization"] == "Bearer private-key"
        body = json.loads(request.content)
        assert body["model"] == "example/vision"
        assert body["messages"][0]["content"][0] == {"type": "text", "text": "extract recipe"}
        assert body["messages"][0]["content"][1]["image_url"]["url"] == (
            "data:image/jpeg;base64," + base64.b64encode(b"sample-jpeg").decode()
        )
        return httpx.Response(200, json={"choices": [{"message": {"content": '{"recipes":[]}'}}]})

    transport = httpx.MockTransport(respond)
    real_client = httpx.AsyncClient
    monkeypatch.setattr("app.openrouter_client.httpx.AsyncClient",
                        lambda **kwargs: real_client(transport=transport, **kwargs))
    result = asyncio.run(OpenRouterClient().generate("extract recipe", b"sample-jpeg"))
    assert result == '{"recipes":[]}'
    assert len(requests) == 1


def test_openrouter_auth_error_is_not_retried(monkeypatch):
    settings = Settings(ai_provider="openrouter", openrouter_api_key="private-key",
                        openrouter_model="example/vision", _env_file=None)
    monkeypatch.setattr("app.openrouter_client.get_settings", lambda: settings)
    attempts = []

    def reject(request: httpx.Request) -> httpx.Response:
        attempts.append(request)
        return httpx.Response(401, json={"error": "invalid key"})

    real_client = httpx.AsyncClient
    monkeypatch.setattr("app.openrouter_client.httpx.AsyncClient",
                        lambda **kwargs: real_client(transport=httpx.MockTransport(reject), **kwargs))
    with pytest.raises(httpx.HTTPStatusError):
        asyncio.run(OpenRouterClient().generate("prompt", b"image"))
    assert len(attempts) == 1


def test_missing_model_endpoint_names_model_without_exposing_key(monkeypatch):
    settings = Settings(ai_provider="openrouter", openrouter_api_key="private-key",
                        openrouter_model="example/retired-vision", _env_file=None)
    monkeypatch.setattr("app.openrouter_client.get_settings", lambda: settings)
    real_client = httpx.AsyncClient
    monkeypatch.setattr("app.openrouter_client.httpx.AsyncClient", lambda **kwargs: real_client(
        transport=httpx.MockTransport(lambda _: httpx.Response(
            404, json={"error": {"message": "No endpoints found"}}
        )), **kwargs
    ))
    with pytest.raises(RuntimeError, match="example/retired-vision") as exc:
        asyncio.run(OpenRouterClient().generate("prompt", b"image"))
    assert "private-key" not in str(exc.value)
