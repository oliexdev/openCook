#!/usr/bin/env python3
"""Phase 1 experiment: run the recipe-extraction prompt against a real photo.

Standalone (stdlib only) so it can be run without the server venv:
    python3 server/scripts/extract_experiment.py testimages/20260523_142204.jpg
    python3 server/scripts/extract_experiment.py <img> --rotate 270   # try upright

It prints timing and the raw model response so we can judge extraction quality
before building the parsing pipeline around it.
"""

import argparse
import base64
import json
import sys
import time
import urllib.request

OLLAMA_URL = "http://localhost:11434/api/generate"
MODEL = "llama3.2-vision:11b"

PROMPT = """Du bist ein Extraktionssystem für Kochbuch-Rezepte. Analysiere das Foto \
einer Kochbuchseite und gib AUSSCHLIESSLICH gültiges JSON zurück.

Regeln:
- Erfasse nur Rezepte, die vollständig oder überwiegend sichtbar sind. Ignoriere \
am Seitenrand angeschnittene Rezept-Fragmente.
- Erfinde KEINE Werte. Was nicht auf der Seite steht, auf null setzen. Das gilt \
besonders für Nährwerte: nur übernehmen, wenn sie gedruckt sind.
- Mengen und Einheiten getrennt erfassen, deutsche Einheiten beibehalten.
- Nährwert-Kürzel: EW oder E = Eiweiß (Protein), F = Fett, KH = Kohlenhydrate, \
kcal = Kalorien. Basis angeben ("pro Portion" oder "pro 100 g").

Antworte mit einem JSON-Objekt dieser Form:
{"recipes": [{
  "title": string,
  "servings": string|null,
  "prep_time": string|null,
  "cook_time": string|null,
  "ingredients": [{"quantity": string|null, "unit": string|null, "name": string}],
  "steps": [string],
  "nutrition": {"basis": string|null, "calories_kcal": number|null,
                "protein_g": number|null, "fat_g": number|null,
                "carbs_g": number|null} | null,
  "notes": [string]
}]}
Gib nur das JSON-Objekt zurück, ohne Erklärungen."""


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("image")
    parser.add_argument("--model", default=MODEL, help="Ollama model tag to use")
    parser.add_argument("--rotate", type=int, default=0,
                        help="Rotate degrees clockwise before sending (needs Pillow)")
    parser.add_argument("--max-side", type=int, default=0,
                        help="Downscale so the longest side is at most this (needs Pillow)")
    args = parser.parse_args()

    with open(args.image, "rb") as fh:
        image_bytes = fh.read()

    if args.rotate or args.max_side:
        from io import BytesIO
        from PIL import Image
        img = Image.open(BytesIO(image_bytes))
        if args.rotate:
            img = img.rotate(-args.rotate, expand=True)
        if args.max_side and max(img.size) > args.max_side:
            scale = args.max_side / max(img.size)
            img = img.resize((int(img.width * scale), int(img.height * scale)))
        buf = BytesIO()
        img.convert("RGB").save(buf, format="JPEG", quality=90)
        image_bytes = buf.getvalue()

    payload = {
        "model": args.model,
        "prompt": PROMPT,
        "images": [base64.b64encode(image_bytes).decode("ascii")],
        "stream": False,
        "format": "json",
        "options": {"temperature": 0},
    }
    req = urllib.request.Request(
        OLLAMA_URL,
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json"},
    )
    started = time.time()
    with urllib.request.urlopen(req, timeout=900) as resp:
        body = json.loads(resp.read())
    elapsed = time.time() - started

    print(f"=== {args.model}  {args.image}  ({len(image_bytes) // 1024} KB sent, {elapsed:.1f}s) ===")
    response_text = body.get("response", "")
    try:
        parsed = json.loads(response_text)
        print(json.dumps(parsed, ensure_ascii=False, indent=2))
    except json.JSONDecodeError:
        print("[response was not valid JSON]\n" + response_text)
    return 0


if __name__ == "__main__":
    sys.exit(main())
