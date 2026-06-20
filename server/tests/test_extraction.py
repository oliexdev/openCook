import asyncio
from io import BytesIO

from PIL import Image

from app.extraction import (
    RecipeExtractor,
    _assign_boxes,
    _Box,
    _clean_step,
    _fix_title_case,
    _iso_duration,
    load_i18n,
    parse_json_lenient,
    to_schema_org,
)


class FakeClient:
    """Stand-in for OllamaClient: returns canned JSON depending on the prompt."""

    async def generate(self, prompt: str, image_bytes: bytes) -> str:
        if "dish_photos" in prompt:
            return '{"dish_photos":[{"recipe_title":"Test Rezept","box":[10,10,100,80]}]}'
        return (
            '{"recipes":[{"title":"Test Rezept","servings":2,"category":"Pasta",'
            '"prep_time":"25 Min.","cook_time":null,'
            '"ingredients":[{"quantity":400,"unit":"g","name":"Nudeln"}],'
            '"steps":["Alles kochen."],'
            '"nutrition":{"basis":"pro Portion","calories_kcal":560,'
            '"protein_g":17,"fat_g":25,"carbs_g":68},"notes":["Ein Tipp"]}]}'
        )


def test_parse_json_lenient_handles_fences():
    assert parse_json_lenient('```json\n{"a": 1}\n```')["a"] == 1


def test_iso_duration():
    de = load_i18n("de")
    assert _iso_duration("25 Min.", de) == "PT25M"
    assert _iso_duration("1 Std 10 Min", de) == "PT70M"
    assert _iso_duration(None, de) is None
    assert _iso_duration("keine Zahl", de) is None
    # English words resolve via the en catalog (and the en-merge fallback).
    assert _iso_duration("1 hour 10 minutes", load_i18n("en")) == "PT70M"


def test_load_i18n_unknown_language_falls_back_to_english():
    en = load_i18n("en")
    ja = load_i18n("ja")  # no ja.json yet → English catalog
    assert ja.text_prompt == en.text_prompt
    assert ja.units == en.units
    # German is its own catalog; merged units are a superset of English.
    de = load_i18n("de")
    assert de.text_prompt != en.text_prompt
    assert en.units <= de.units


def test_to_schema_org_coerces_non_numeric_quantity():
    # The model sometimes returns a string quantity (e.g. shoves the name in);
    # it must not crash flatten() and must surface as null, not a string.
    recipe = {
        "title": "Robust",
        "ingredients": [
            {"quantity": "Salz", "unit": None, "name": "Salz"},
            {"quantity": 200, "unit": "g", "name": "Mehl"},
        ],
        "servings": "zwei",
        "steps": [],
    }
    s = to_schema_org(recipe, [])
    assert s["recipeIngredient"] == ["Salz", "200 g Mehl"]
    assert s["openCookIngredients"][0]["quantity"] is None
    assert s["openCookServings"] is None
    assert s["recipeYield"] is None


def test_to_schema_org_drops_bogus_unit():
    # The model often shoves the ingredient noun into `unit`, which flatten()
    # would render doubled ("8 Zucchini Zucchininudeln"). A unit that overlaps
    # the name and isn't a real unit must be dropped; real units stay.
    recipe = {
        "title": "Macken",
        "ingredients": [
            {"quantity": 8, "unit": "Zucchini", "name": "Zucchininudeln"},
            {"quantity": 2, "unit": "Barschfilets", "name": "Barschfilet"},
            {"quantity": None, "unit": "Salz", "name": "Salz"},
            {"quantity": 1, "unit": "schote", "name": "rote Paprikaschote"},
            {"quantity": 400, "unit": "g", "name": "Mango"},        # real unit, 'g' in 'manGo'
            {"quantity": 1, "unit": "Bund", "name": "Frühlingszwiebeln"},  # real unit kept
        ],
        "steps": [],
    }
    s = to_schema_org(recipe, [])
    assert s["recipeIngredient"] == [
        "8 Zucchininudeln",
        "2 Barschfilet",
        "Salz",
        "1 rote Paprikaschote",
        "400 g Mango",
        "1 Bund Frühlingszwiebeln",
    ]
    # Structured data (what the app consumes) is cleaned too.
    assert s["openCookIngredients"][0]["unit"] is None
    assert s["openCookIngredients"][4]["unit"] == "g"


def test_to_schema_org_passes_through_tags():
    recipe = {"title": "T", "tags": ["vegetarisch", "schnell"], "ingredients": [], "steps": []}
    s = to_schema_org(recipe, [])
    assert s["openCookTags"] == ["vegetarisch", "schnell"]


def test_clean_step_strips_leading_number():
    assert _clean_step("1. Den Backofen vorheizen.") == "Den Backofen vorheizen."
    assert _clean_step("2)  Gemüse waschen") == "Gemüse waschen"
    # No leading number: untouched. And "3-4 Min." must not be mistaken for one.
    assert _clean_step("Alles 3-4 Min. braten.") == "Alles 3-4 Min. braten."
    assert _clean_step("180 °C vorheizen") == "180 °C vorheizen"
    # The model sometimes emits a null step — must not crash, yields "".
    assert _clean_step(None) == ""


def test_to_schema_org_drops_null_steps():
    recipe = {"title": "T", "ingredients": [], "steps": ["Kochen.", None, "  ", "Servieren."]}
    s = to_schema_org(recipe, [])
    assert [i["text"] for i in s["recipeInstructions"]] == ["Kochen.", "Servieren."]


def test_fix_title_case_normalizes_all_caps():
    assert _fix_title_case("GEMÜSEPAELLA MIT GELBEN LINSEN") == "Gemüsepaella mit gelben linsen"
    # Already mixed-case titles are left untouched.
    assert _fix_title_case("Spaghetti Carbonara") == "Spaghetti Carbonara"
    assert _fix_title_case("Gemüsepaella mit Linsen") == "Gemüsepaella mit Linsen"
    assert _fix_title_case(None) is None


def test_assign_boxes_is_one_to_one():
    # Two recipes, two photos: each recipe must get its OWN matching photo,
    # never the same one (the bug that swapped photos between recipes).
    recipes = ["Tomatensuppe", "Apfelkuchen"]
    boxes = [
        _Box(title="Apfelkuchen", coords=(0, 0, 10, 10)),
        _Box(title="Tomatensuppe", coords=(20, 20, 30, 30)),
    ]
    assigned = _assign_boxes(recipes, boxes)
    assert assigned[0].title == "Tomatensuppe"
    assert assigned[1].title == "Apfelkuchen"


def test_assign_boxes_no_duplicate_box():
    # Two similar titles but only one photo: exactly one recipe gets it.
    recipes = ["Schokokuchen", "Schokotorte"]
    boxes = [_Box(title="Schokokuchen", coords=(0, 0, 10, 10))]
    assigned = _assign_boxes(recipes, boxes)
    assert len(assigned) == 1
    assert 0 in assigned and 1 not in assigned


def test_to_schema_org_maps_fields():
    recipe = {
        "title": "X",
        "servings": 2,
        "category": "Pasta",
        "ingredients": [{"quantity": 400, "unit": "g", "name": "Nudeln"}],
        "steps": ["Schritt eins"],
        "nutrition": {"basis": "pro Portion", "calories_kcal": 560,
                      "protein_g": 17, "fat_g": 25, "carbs_g": 68},
        "notes": [],
    }
    s = to_schema_org(recipe, ["dish.jpg"], source_photo="page-uuid.jpg")
    assert s["@type"] == "Recipe"
    assert s["openCookSourcePhoto"] == "page-uuid.jpg"
    assert s["openCookTags"] == []  # absent in input → empty list
    assert s["recipeIngredient"] == ["400 g Nudeln"]
    assert s["recipeYield"] is None
    assert s["openCookServings"] == 2
    assert s["openCookCategory"] == "pasta"
    assert s["recipeInstructions"][0] == {"@type": "HowToStep", "text": "Schritt eins"}
    assert s["nutrition"]["calories"] == "560 kcal"
    assert s["nutrition"]["proteinContent"] == "17 g"
    assert s["nutrition"]["openCookBasis"] == "pro Portion"
    assert s["image"] == ["dish.jpg"]


def test_extractor_applies_exif_orientation(tmp_path):
    # A landscape image tagged orientation=6 (rotate 90° CW to view) must reach
    # the model upright (portrait), or OCR is done on a sideways page.
    img = Image.new("RGB", (280, 140), (200, 200, 200))
    exif = img.getexif()
    exif[274] = 6
    path = tmp_path / "rot.jpg"
    img.save(path, exif=exif)

    received: list[tuple[int, int]] = []

    class CapturingClient:
        async def generate(self, prompt: str, image_bytes: bytes) -> str:
            received.append(Image.open(BytesIO(image_bytes)).size)
            return '{"dish_photos":[]}' if "dish_photos" in prompt else '{"recipes":[]}'

    images_dir = tmp_path / "images"
    images_dir.mkdir()
    asyncio.run(RecipeExtractor(CapturingClient(), images_dir).extract(path))

    width, height = received[0]
    assert height > width  # transposed to portrait before sending


def test_extractor_end_to_end(tmp_path):
    image = tmp_path / "page.jpg"
    Image.new("RGB", (200, 150), (210, 210, 210)).save(image)
    images_dir = tmp_path / "images"
    images_dir.mkdir()

    extractor = RecipeExtractor(FakeClient(), images_dir)
    recipes = asyncio.run(extractor.extract(image))

    assert len(recipes) == 1
    assert recipes[0]["name"] == "Test Rezept"
    # A dish crop was matched, written to disk and referenced on the recipe.
    assert recipes[0]["image"]
    assert (images_dir / recipes[0]["image"][0]).exists()
