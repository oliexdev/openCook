"""Recipe extraction pipeline: cookbook photo -> schema.org/Recipe objects.

Two focused qwen2.5vl calls (validated against real photos in scripts/):
  1. text + structure (title, ingredients, steps, servings, times, nutrition)
  2. dish-photo bounding boxes (for auto-cropping)
Boxes are matched to recipes by title and the dish images are cropped from the
original (full-resolution) photo with Pillow.

Lessons baked in (see memory/extraction-model.md):
  - qwen returns absolute pixel coords in the SENT image space -> we send a
    controlled size (multiple of 28) so coords map 1:1.
  - Ollama's hard `format: "json"` 500s on some images -> we parse leniently.
"""

import difflib
import json
import logging
import re
import uuid
from collections.abc import Callable
from dataclasses import dataclass
from functools import lru_cache
from io import BytesIO
from pathlib import Path

from PIL import Image, ImageOps

from app.ollama_client import OllamaClient

logger = logging.getLogger(__name__)

SENT_LONG_SIDE = 1008  # multiple of 28, within qwen2.5vl's pixel budget


# Stable category keys (universal across languages; mirror of the app's RecipeCategories).
_CATEGORY_KEYS = {"pasta", "meat", "fish", "soup", "vegetarian", "salad", "dessert", "other"}

_I18N_DIR = Path(__file__).parent / "i18n"


@dataclass(frozen=True)
class I18n:
    """Localized extraction resources for one content language (see app/i18n/*.json)."""

    text_prompt: str
    box_prompt: str
    duration_hours: tuple[str, ...]
    duration_minutes: tuple[str, ...]
    units: frozenset[str]
    category_aliases: dict[str, str]


def _read_catalog(lang: str) -> dict:
    path = _I18N_DIR / f"{lang}.json"
    if not path.is_file():
        path = _I18N_DIR / "en.json"
    return json.loads(path.read_text(encoding="utf-8"))


@lru_cache(maxsize=None)
def load_i18n(language: str | None) -> I18n:
    """Load extraction resources for ``language`` (e.g. "de"/"en"); unknown → English.
    Units/durations/aliases are unioned with English so universal tokens always match."""
    lang = (language or "en").strip().lower()[:2]
    base = _read_catalog("en")
    data = base if lang == "en" else _read_catalog(lang)

    def merged(key: str) -> list:
        return list(dict.fromkeys([*base.get(key, []), *data.get(key, [])]))

    return I18n(
        text_prompt=data.get("text_prompt") or base["text_prompt"],
        box_prompt=data.get("box_prompt") or base["box_prompt"],
        duration_hours=tuple(merged("duration_hours")),
        duration_minutes=tuple(merged("duration_minutes")),
        units=frozenset(u.lower() for u in merged("units")),
        category_aliases={
            **{k.lower(): v for k, v in base.get("category_aliases", {}).items()},
            **{k.lower(): v for k, v in data.get("category_aliases", {}).items()},
        },
    )


def _normalize_category(raw: object, aliases: dict[str, str]) -> str | None:
    """Map a model category (any language/case) to a stable key; unknown → "other"."""
    if not isinstance(raw, str) or not raw.strip():
        return None
    t = raw.strip().lower()
    if t in _CATEGORY_KEYS:
        return t
    return aliases.get(t, "other")


def parse_json_lenient(text: str) -> dict:
    """Parse JSON that may be wrapped in prose or ```json fences."""
    try:
        return json.loads(text)
    except json.JSONDecodeError:
        start, end = text.find("{"), text.rfind("}")
        if start != -1 and end > start:
            return json.loads(text[start:end + 1])
        raise


def _smart_resize(img: Image.Image, long_side: int = SENT_LONG_SIDE) -> Image.Image:
    """Resize so the longest side <= long_side and both dims are multiples of 28."""
    w, h = img.size
    scale = min(1.0, long_side / max(w, h))
    nw = max(28, round(w * scale / 28) * 28)
    nh = max(28, round(h * scale / 28) * 28)
    return img.resize((nw, nh))


def _duration_re(words: tuple[str, ...]) -> re.Pattern:
    # Longest-first so e.g. "hours" matches before the bare "h".
    alts = "|".join(re.escape(w) for w in sorted(set(words), key=len, reverse=True))
    return re.compile(rf"(\d+)\s*(?:{alts})\b", re.I)


def _iso_duration(text: str | None, i18n: "I18n") -> str | None:
    """Best-effort 'PT..' duration using the language's hour/minute words."""
    if not text:
        return None
    minutes = 0
    if m := _duration_re(i18n.duration_hours).search(text):
        minutes += int(m.group(1)) * 60
    if m := _duration_re(i18n.duration_minutes).search(text):
        minutes += int(m.group(1))
    return f"PT{minutes}M" if minutes else None


@dataclass
class _Box:
    title: str
    coords: tuple[int, int, int, int]  # in sent-image space


_TITLE_MATCH_MIN = 0.4


def _assign_boxes(recipe_titles: list[str], boxes: list[_Box]) -> dict[int, _Box]:
    """Assign at most one dish photo to each recipe, strictly 1:1.

    Builds the full recipe×box title-similarity matrix and greedily takes the
    strongest pairs, removing both partners once used. This prevents two recipes
    from claiming the same photo — the per-recipe greedy it replaces could hand
    the same box to multiple recipes, which is how photos got swapped between
    recipes on multi-recipe pages.
    """
    if not boxes or not recipe_titles:
        return {}
    pairs: list[tuple[float, int, int]] = []
    for ri, title in enumerate(recipe_titles):
        for bi, box in enumerate(boxes):
            ratio = difflib.SequenceMatcher(
                None, title.lower(), box.title.lower()
            ).ratio()
            pairs.append((ratio, ri, bi))
    pairs.sort(key=lambda p: p[0], reverse=True)

    assigned: dict[int, _Box] = {}
    used_boxes: set[int] = set()
    for ratio, ri, bi in pairs:
        if ratio < _TITLE_MATCH_MIN:
            break
        if ri in assigned or bi in used_boxes:
            continue
        assigned[ri] = boxes[bi]
        used_boxes.add(bi)
    return assigned


class RecipeExtractor:
    def __init__(self, client: OllamaClient, images_dir: Path) -> None:
        self._client = client
        self._images_dir = images_dir

    async def extract(
        self,
        image_path: Path,
        on_stage: Callable[[str], None] | None = None,
        language: str | None = None,
    ) -> list[dict]:
        """Extract recipes. ``on_stage`` (if given) is called with a coarse stage
        key before each model call, so callers can surface progress. ``language``
        (e.g. "de"/"en") selects the extraction prompt."""
        def stage(name: str) -> None:
            if on_stage is not None:
                on_stage(name)

        # Apply the camera's EXIF orientation up front: phone photos are often
        # stored sideways with an Orientation tag the model would otherwise see
        # rotated (which wrecks OCR). Transposing here keeps the sent image and
        # the crop coordinates in the same upright space.
        original = ImageOps.exif_transpose(Image.open(image_path)).convert("RGB")
        sent = _smart_resize(original)
        sent_bytes = _to_jpeg(sent)

        i18n = load_i18n(language)
        stage("reading_text")
        text_raw = await self._client.generate(i18n.text_prompt, sent_bytes)
        recipes = parse_json_lenient(text_raw).get("recipes", [])

        boxes: list[_Box] = []
        try:
            stage("detecting_photos")
            box_raw = await self._client.generate(i18n.box_prompt, sent_bytes)
            boxes = _parse_boxes(box_raw, sent.size)
        except Exception:  # noqa: BLE001 - cropping is best-effort, never fatal
            logger.warning("Dish-photo detection failed; recipes saved without images.")

        scale_x = original.width / sent.width
        scale_y = original.height / sent.height
        titles = [r.get("title", "") for r in recipes]
        assigned = _assign_boxes(titles, boxes)
        results = []
        for i, recipe in enumerate(recipes):
            image_paths = []
            box = assigned.get(i)
            if box is not None:
                crop_path = self._crop(original, box.coords, scale_x, scale_y)
                if crop_path is not None:
                    image_paths.append(crop_path)
            results.append(
                to_schema_org(recipe, image_paths, source_photo=image_path.name, i18n=i18n)
            )
        return results

    def _crop(self, original: Image.Image, coords, scale_x: float, scale_y: float) -> str | None:
        x1, y1, x2, y2 = coords
        bx1, by1 = int(x1 * scale_x), int(y1 * scale_y)
        bx2, by2 = int(x2 * scale_x), int(y2 * scale_y)
        bx1, bx2 = sorted((max(0, bx1), min(original.width, bx2)))
        by1, by2 = sorted((max(0, by1), min(original.height, by2)))
        if bx2 - bx1 < 10 or by2 - by1 < 10:
            return None
        name = f"{uuid.uuid4()}.jpg"
        original.crop((bx1, by1, bx2, by2)).save(self._images_dir / name, quality=88)
        return name


def _to_jpeg(img: Image.Image) -> bytes:
    buf = BytesIO()
    img.save(buf, format="JPEG", quality=90)
    return buf.getvalue()


def _parse_boxes(raw: str, sent_size: tuple[int, int]) -> list[_Box]:
    sw, sh = sent_size
    out: list[_Box] = []
    for item in parse_json_lenient(raw).get("dish_photos", []):
        box = item.get("box") or []
        if len(box) != 4:
            continue

        def px(v: float, dim: int) -> int:
            return int(v * dim) if v <= 1.5 else int(v)

        out.append(_Box(
            title=str(item.get("recipe_title", "")),
            coords=(px(box[0], sw), px(box[1], sh), px(box[2], sw), px(box[3], sh)),
        ))
    return out


_STEP_NUMBER_RE = re.compile(r"^\s*\d{1,2}\s*[.)]\s*")


def _clean_step(text: str) -> str:
    """Strip a leading printed step number ("1.", "2)") from an instruction.

    The UI numbers steps itself, so a number left in the text renders as
    "1. 1. ...". Only strips when text remains, never blanking a step.
    The model occasionally emits a null/non-string step → coerce to "".
    """
    if not isinstance(text, str):
        return ""
    stripped = _STEP_NUMBER_RE.sub("", text).strip()
    return stripped or text


def _fix_title_case(title: str | None) -> str | None:
    """Fall back to sentence case for ALL-CAPS titles the model left unnormalized.

    German noun casing can't be inferred reliably after the fact, so we only
    touch titles that have no lowercase letter at all — there, anything beats
    SHOUTING, and the user can refine it on the review screen.
    """
    if not title:
        return title
    letters = [c for c in title if c.isalpha()]
    if letters and not any(c.islower() for c in letters):
        return title.capitalize()
    return title


def _num(value: object) -> float | int | None:
    """Coerce a quantity/servings value to a real number, else None.

    The model occasionally emits a non-numeric ``quantity`` (e.g. it shoves the
    name in: ``"quantity": "Salz"``). Letting that through crashed flatten() and
    would also break the app's ``Double?`` parser, failing the whole job — so any
    non-number becomes null.
    """
    if isinstance(value, bool):  # bool is an int subclass; never a quantity
        return None
    return value if isinstance(value, (int, float)) else None


def _clean_unit(unit: object, name: object, known_units: frozenset[str]) -> str | None:
    """Drop a bogus ``unit`` that is really the ingredient noun.

    The model sometimes puts the ingredient (or part of it) in ``unit`` rather
    than a real measurement (e.g. {unit:"Barschfilets", name:"Barschfilet"} →
    "4 Barschfilets Barschfilet"). Keep genuine units; otherwise, if the unit
    word overlaps the name, it is a duplication artifact and is dropped.
    """
    if not isinstance(unit, str):
        return None
    u = unit.strip()
    if not u:
        return None
    ul, nl = u.lower(), (name or "").strip().lower() if isinstance(name, str) else ""
    if ul in known_units:
        return u
    if nl and (ul in nl or nl in ul):
        return None
    return u


def to_schema_org(
    recipe: dict, image_names: list[str], source_photo: str | None = None,
    i18n: "I18n | None" = None,
) -> dict:
    """Map an extracted recipe to schema.org/Recipe JSON-LD (+ openCook extensions).

    ``source_photo`` is the filename of the original uploaded page photo (kept on
    the server, never deleted) so a recipe can be re-extracted later with a better
    model — see openCookSourcePhoto below. ``i18n`` selects the language resources.
    """
    if i18n is None:
        i18n = load_i18n("en")
    # Coerce quantities to numbers up front so neither flatten() nor the app's
    # Double? parser ever sees a stray string.
    ingredients = [
        {**i, "quantity": _num(i.get("quantity")), "unit": _clean_unit(i.get("unit"), i.get("name"), i18n.units)}
        for i in (recipe.get("ingredients") or [])
    ]

    def flatten(i: dict) -> str:
        qty = i.get("quantity")
        qty_str = ("" if qty is None else (str(int(qty)) if float(qty).is_integer() else str(qty)))
        return " ".join(p for p in (qty_str, i.get("unit"), i.get("name")) if p).strip()

    servings = _num(recipe.get("servings"))
    result: dict = {
        "@context": "https://schema.org",
        "@type": "Recipe",
        "name": _fix_title_case(recipe.get("title")),
        # Leave recipeYield null — the numeric openCookServings below carries the count and
        # the app renders a localized "N servings" label (no language baked into the data).
        "recipeYield": None,
        "recipeIngredient": [flatten(i) for i in ingredients],
        "recipeInstructions": [
            {"@type": "HowToStep", "text": cleaned}
            for step in (recipe.get("steps") or [])
            if (cleaned := _clean_step(step)).strip()
        ],
        "image": image_names,
        # openCook extensions: structured data the app needs but schema.org flattens.
        "openCookIngredients": ingredients,
        "openCookServings": servings,
        "openCookCategory": _normalize_category(recipe.get("category"), i18n.category_aliases),
        "openCookNotes": recipe.get("notes") or [],
        "openCookTags": recipe.get("tags") or [],
        # Pointer to the retained original page photo for later re-extraction.
        "openCookSourcePhoto": source_photo,
    }
    if prep := _iso_duration(recipe.get("prep_time"), i18n):
        result["prepTime"] = prep
    if cook := _iso_duration(recipe.get("cook_time"), i18n):
        result["cookTime"] = cook

    nutrition = recipe.get("nutrition")
    if nutrition:
        info = {"@type": "NutritionInformation"}
        if (v := nutrition.get("calories_kcal")) is not None:
            info["calories"] = f"{v} kcal"
        if (v := nutrition.get("protein_g")) is not None:
            info["proteinContent"] = f"{v} g"
        if (v := nutrition.get("fat_g")) is not None:
            info["fatContent"] = f"{v} g"
        if (v := nutrition.get("carbs_g")) is not None:
            info["carbohydrateContent"] = f"{v} g"
        if nutrition.get("basis"):
            info["openCookBasis"] = nutrition["basis"]
        if len(info) > 1:
            result["nutrition"] = info
    return result
