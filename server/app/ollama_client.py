"""Thin async client for the Ollama vision model."""

import asyncio
import base64
import logging

import httpx

from app.config import get_settings

logger = logging.getLogger(__name__)


class OllamaClient:
    def __init__(self, max_retries: int = 2) -> None:
        settings = get_settings()
        self._base_url = settings.ollama_base_url.rstrip("/")
        self._model = settings.ollama_model
        self._max_retries = max_retries

    async def generate(self, prompt: str, image_bytes: bytes) -> str:
        """Run the vision model on a single image and return the raw response text.

        Retries on 5xx / transport errors: the Ollama model runner can crash
        mid-generation (e.g. flaky GPU/ROCm backends), and a reload + retry often
        succeeds.
        """
        image_b64 = base64.b64encode(image_bytes).decode("ascii")
        payload = {
            "model": self._model,
            "prompt": prompt,
            "images": [image_b64],
            "stream": False,
            "format": "json",
        }
        last_error: Exception | None = None
        async with httpx.AsyncClient(timeout=httpx.Timeout(600.0)) as client:
            for attempt in range(self._max_retries + 1):
                try:
                    response = await client.post(f"{self._base_url}/api/generate", json=payload)
                    response.raise_for_status()
                    return response.json().get("response", "")
                except (httpx.HTTPStatusError, httpx.TransportError) as exc:
                    last_error = exc
                    if attempt < self._max_retries:
                        logger.warning("Ollama call failed (attempt %d), retrying: %s",
                                       attempt + 1, exc)
                        await asyncio.sleep(2.0 * (attempt + 1))
        raise last_error  # type: ignore[misc]
