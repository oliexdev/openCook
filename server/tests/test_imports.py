"""Recipe-import inbox API: push, list pending, exclusive claim, consume.

Shares the disposable dev DB like the other API tests; each test creates its own
household so it is independent of prior state.
"""

import json

from fastapi.testclient import TestClient

from app.config import get_settings
from app.main import app

RECIPE = {
    "@context": "https://schema.org",
    "@type": "Recipe",
    "name": "Spaghetti Carbonara",
    "recipeYield": "4 Portionen",
    "recipeIngredient": ["400 g Spaghetti", "200 g Pancetta", "4 Eier"],
    "recipeInstructions": [{"@type": "HowToStep", "text": "Kochen."}],
}


def _new_household(client: TestClient, name: str = "ImportTest") -> str:
    return client.post("/households", json={"name": name}).json()["invite_code"]


def _hdr(code: str) -> dict:
    return {"X-Household-Code": code}


def test_create_then_pending_lists_it() -> None:
    with TestClient(app) as client:
        code = _new_household(client)
        created = client.post("/imports", data={"recipe": json.dumps(RECIPE)}, headers=_hdr(code))
        assert created.status_code == 201
        import_id = created.json()["import_id"]

        pending = client.get("/imports/pending", headers=_hdr(code)).json()["imports"]
        assert [p["id"] for p in pending] == [import_id]
        assert pending[0]["recipe"]["name"] == "Spaghetti Carbonara"
        assert pending[0]["image_name"] is None
        assert pending[0]["source_url"] is None


def test_pending_includes_source_url() -> None:
    # The app derives a source-site cookbook from this, so /pending must surface it.
    with TestClient(app) as client:
        code = _new_household(client)
        url = "https://www.chefkoch.de/rezepte/123/Kartoffelsalat.html"
        client.post(
            "/imports", data={"recipe": json.dumps(RECIPE), "source_url": url}, headers=_hdr(code)
        )
        pending = client.get("/imports/pending", headers=_hdr(code)).json()["imports"]
        assert pending[0]["source_url"] == url


def test_image_is_stored_and_referenced() -> None:
    with TestClient(app) as client:
        code = _new_household(client)
        created = client.post(
            "/imports",
            data={"recipe": json.dumps(RECIPE)},
            files={"image": ("dish.jpg", b"\xff\xd8\xff\x00fakejpeg", "image/jpeg")},
            headers=_hdr(code),
        )
        assert created.status_code == 201
        pending = client.get("/imports/pending", headers=_hdr(code)).json()["imports"]
        name = pending[0]["image_name"]
        assert name and name.endswith(".jpg")
        assert (get_settings().images_dir / name).is_file()


def test_claim_is_exclusive() -> None:
    with TestClient(app) as client:
        code = _new_household(client)
        import_id = client.post(
            "/imports", data={"recipe": json.dumps(RECIPE)}, headers=_hdr(code)
        ).json()["import_id"]

        assert client.post(f"/imports/{import_id}/claim", headers=_hdr(code)).status_code == 204
        # A second claim loses.
        assert client.post(f"/imports/{import_id}/claim", headers=_hdr(code)).status_code == 409
        # A claimed record is no longer pending.
        pending = client.get("/imports/pending", headers=_hdr(code)).json()["imports"]
        assert all(p["id"] != import_id for p in pending)


def test_consume_removes_from_pending() -> None:
    with TestClient(app) as client:
        code = _new_household(client)
        import_id = client.post(
            "/imports", data={"recipe": json.dumps(RECIPE)}, headers=_hdr(code)
        ).json()["import_id"]
        client.post(f"/imports/{import_id}/claim", headers=_hdr(code))
        assert client.post(f"/imports/{import_id}/consume", headers=_hdr(code)).status_code == 204
        pending = client.get("/imports/pending", headers=_hdr(code)).json()["imports"]
        assert all(p["id"] != import_id for p in pending)


def test_same_source_url_reuses_pending_import() -> None:
    with TestClient(app) as client:
        code = _new_household(client)
        url = "https://example.com/rezept/1"
        first = client.post(
            "/imports", data={"recipe": json.dumps(RECIPE), "source_url": url}, headers=_hdr(code)
        ).json()["import_id"]
        # Second push of the same page while the first is still pending → same id, no duplicate.
        second = client.post(
            "/imports", data={"recipe": json.dumps(RECIPE), "source_url": url}, headers=_hdr(code)
        ).json()["import_id"]
        assert first == second
        pending = client.get("/imports/pending", headers=_hdr(code)).json()["imports"]
        assert len([p for p in pending if p["id"] == first]) == 1
        assert len(pending) == 1


def test_invalid_recipe_json_rejected() -> None:
    with TestClient(app) as client:
        code = _new_household(client)
        assert client.post(
            "/imports", data={"recipe": "{not json"}, headers=_hdr(code)
        ).status_code == 400


def test_household_scoping_and_auth() -> None:
    with TestClient(app) as client:
        # Missing header → 400; unknown code → 404.
        assert client.post("/imports", data={"recipe": json.dumps(RECIPE)}).status_code == 400
        assert client.get("/imports/pending", headers=_hdr("nope")).status_code == 404

        code_a = _new_household(client, "A")
        code_b = _new_household(client, "B")
        client.post("/imports", data={"recipe": json.dumps(RECIPE)}, headers=_hdr(code_a))
        # B sees none of A's imports.
        assert client.get("/imports/pending", headers=_hdr(code_b)).json()["imports"] == []
