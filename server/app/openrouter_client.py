"""OpenRouter vision client using chat completions and a private JPEG data URL."""

import asyncio
import base64
import logging

import httpx

from app.config import get_settings

logger = logging.getLogger(__name__)
_ENDPOINT = "https://openrouter.ai/api/v1/chat/completions"


class OpenRouterClient:
    def __init__(self, max_retries: int = 2) -> None:
        settings = get_settings()
        if not settings.openrouter_api_key or not settings.openrouter_model:
            raise ValueError("OpenRouter requires an API key and model")
        self._key = settings.openrouter_api_key.get_secret_value()
        self._model = settings.openrouter_model
        self._max_retries = max_retries

    async def generate(self, prompt: str, image_bytes: bytes) -> str:
        image_b64 = base64.b64encode(image_bytes).decode("ascii")
        payload = {
            "model": self._model,
            "messages": [{
                "role": "user",
                "content": [
                    {"type": "text", "text": prompt},
                    {"type": "image_url", "image_url": {"url": f"data:image/jpeg;base64,{image_b64}"}},
                ],
            }],
            "stream": False,
        }
        headers = {"Authorization": f"Bearer {self._key}"}
        async with httpx.AsyncClient(timeout=httpx.Timeout(600.0)) as client:
            for attempt in range(self._max_retries + 1):
                try:
                    response = await client.post(_ENDPOINT, json=payload, headers=headers)
                    response.raise_for_status()
                    content = response.json()["choices"][0]["message"]["content"]
                    if not isinstance(content, str) or not content.strip():
                        raise ValueError("OpenRouter returned no text content")
                    return content
                except (httpx.HTTPStatusError, httpx.TransportError) as exc:
                    retryable = isinstance(exc, httpx.TransportError) or (
                        exc.response.status_code == 429 or exc.response.status_code >= 500
                    )
                    if not retryable or attempt == self._max_retries:
                        if isinstance(exc, httpx.HTTPStatusError) and exc.response.status_code == 404:
                            raise RuntimeError(
                                f"OpenRouter returned HTTP 404 for model {self._model}; "
                                "check that it has active endpoints and your provider settings"
                            ) from exc
                        raise
                    logger.warning("OpenRouter request failed (attempt %d, status %s), retrying",
                                   attempt + 1,
                                   exc.response.status_code if isinstance(exc, httpx.HTTPStatusError) else "transport")
                    await asyncio.sleep(2.0 * (attempt + 1))
        raise RuntimeError("Unreachable OpenRouter retry state")
