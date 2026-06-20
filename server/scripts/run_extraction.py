#!/usr/bin/env python3
"""Run the full extraction pipeline on a real image and print schema.org JSON.

    PYTHONPATH=server server/.venv/bin/python server/scripts/run_extraction.py <image>
"""

import asyncio
import json
import sys
from pathlib import Path

from app.config import get_settings
from app.extraction import RecipeExtractor
from app.ollama_client import OllamaClient


async def main() -> None:
    settings = get_settings()
    extractor = RecipeExtractor(OllamaClient(), settings.images_dir)
    recipes = await extractor.extract(Path(sys.argv[1]))
    print(json.dumps(recipes, ensure_ascii=False, indent=2))
    print(f"\n-> {len(recipes)} recipe(s); crops in {settings.images_dir}")


if __name__ == "__main__":
    asyncio.run(main())
