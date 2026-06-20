#!/usr/bin/env python3
"""Phase 1 experiment: can qwen2.5vl locate the dish photos for auto-cropping?

Asks the model for normalised bounding boxes of the finished-dish photographs on
a cookbook page, then draws them on the image and saves the crops so we can judge
visually whether auto-cropping is feasible.

Run with the server venv (has Pillow):
    server/.venv/bin/python server/scripts/crop_experiment.py testimages/<img>.jpg

Outputs to /tmp/crops/: <name>_annotated.jpg (boxes drawn) and <name>_crop_N.jpg.
"""

import base64
import json
import sys
import time
import urllib.request
from pathlib import Path

from PIL import Image, ImageDraw

OLLAMA_URL = "http://localhost:11434/api/generate"
MODEL = "qwen2.5vl:7b"
OUT_DIR = Path("/tmp/crops")

PROMPT = """Auf diesem Foto einer Kochbuchseite sind ein oder mehrere FOTOS VON \
FERTIGEN GERICHTEN abgebildet (echte Speisefotos, KEIN Text, KEINE Symbole/Icons).

Finde jedes Gerichtsfoto und gib AUSSCHLIESSLICH dieses JSON zurück:
{"dish_photos": [
  {"recipe_title": "<Titel des Rezepts, zu dem das Foto gehört>",
   "box": [x1, y1, x2, y2]}
]}
Die Box-Koordinaten sind NORMALISIERT von 0.0 bis 1.0: x1,y1 = linke obere Ecke, \
x2,y2 = rechte untere Ecke, relativ zu Bildbreite und Bildhöhe.
Gib nur tatsächlich vorhandene Gerichtsfotos zurück, erfinde keine Boxen. Nur das JSON."""


def _smart_resize(img: Image.Image, long_side: int = 1008) -> Image.Image:
    """Resize so the longest side <= long_side and both dims are multiples of 28.

    Qwen2.5-VL reports box coordinates relative to its processor-resized image.
    By sending dimensions that are already multiples of 28 (within its pixel
    budget), the processor keeps our size, so returned pixels map 1:1 to what
    we send -> no unknown scale factor.
    """
    w, h = img.size
    scale = min(1.0, long_side / max(w, h))
    nw = max(28, round(w * scale / 28) * 28)
    nh = max(28, round(h * scale / 28) * 28)
    return img.resize((nw, nh))


def main() -> int:
    image_path = Path(sys.argv[1])
    OUT_DIR.mkdir(parents=True, exist_ok=True)

    from io import BytesIO
    sent = _smart_resize(Image.open(image_path).convert("RGB"))
    sw, sh = sent.size
    buf = BytesIO()
    sent.save(buf, format="JPEG", quality=90)

    payload = {
        "model": MODEL,
        "prompt": PROMPT,
        "images": [base64.b64encode(buf.getvalue()).decode("ascii")],
        "stream": False,
        # NOTE: not using Ollama's hard "format": "json" — its constraint grammar
        # can 500 on some images. We prompt for JSON and parse leniently instead.
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

    response_text = body.get("response", "")
    print(f"=== {image_path.name}  sent {sw}x{sh}  ({elapsed:.1f}s) ===")
    print(response_text)

    def parse_json_lenient(text: str) -> dict:
        try:
            return json.loads(text)
        except json.JSONDecodeError:
            start, end = text.find("{"), text.rfind("}")
            if start != -1 and end > start:
                return json.loads(text[start:end + 1])
            raise

    try:
        boxes = parse_json_lenient(response_text).get("dish_photos", [])
    except json.JSONDecodeError:
        print("[invalid JSON]")
        return 1

    def to_px(v: float, dim: int) -> int:
        # Model may emit absolute pixels (our sent space) or normalised 0..1.
        return int(v * dim) if v <= 1.5 else int(v)

    annotated = sent.copy()
    draw = ImageDraw.Draw(annotated)
    stem = image_path.stem

    for i, item in enumerate(boxes):
        box = item.get("box") or []
        if len(box) != 4:
            continue
        x1 = max(0, min(sw, to_px(box[0], sw)))
        y1 = max(0, min(sh, to_px(box[1], sh)))
        x2 = max(0, min(sw, to_px(box[2], sw)))
        y2 = max(0, min(sh, to_px(box[3], sh)))
        x1, x2 = sorted((x1, x2))
        y1, y2 = sorted((y1, y2))
        draw.rectangle([x1, y1, x2, y2], outline=(255, 0, 0), width=6)
        draw.text((x1 + 6, y1 + 6), item.get("recipe_title", "?")[:30], fill=(255, 0, 0))
        if x2 - x1 > 5 and y2 - y1 > 5:
            sent.crop((x1, y1, x2, y2)).save(OUT_DIR / f"{stem}_crop_{i}.jpg", quality=88)

    annotated.save(OUT_DIR / f"{stem}_annotated.jpg", quality=88)
    print(f"-> wrote {len(boxes)} crop(s) + annotated image to {OUT_DIR}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
