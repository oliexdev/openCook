"""Provider selection for the recipe extraction pipeline."""

from typing import Protocol

from app.config import get_settings
from app.ollama_client import OllamaClient
from app.openrouter_client import OpenRouterClient


class VisionClient(Protocol):
    async def generate(self, prompt: str, image_bytes: bytes) -> str: ...


def make_vision_client() -> VisionClient:
    return OpenRouterClient() if get_settings().ai_provider == "openrouter" else OllamaClient()
