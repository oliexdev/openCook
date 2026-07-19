from fastapi.testclient import TestClient

from app.main import app


def test_create_lists_and_join_open_household() -> None:
    with TestClient(app) as client:
        created = client.post(
            "/households", json={"name": "Familie Müller", "settings": {"household_size": 4}}
        )
        assert created.status_code == 201
        body = created.json()
        assert body["name"] == "Familie Müller"
        assert body["settings"]["household_size"] == 4
        assert body["invite_code"]  # creator gets the sync credential
        hid = body["household_id"]

        # The picker lists it without exposing the code/PIN.
        listing = client.get("/households").json()
        entry = next(h for h in listing if h["id"] == hid)
        assert entry["name"] == "Familie Müller"
        assert entry["settings"]["household_size"] == 4
        assert entry["protected"] is False
        assert "invite_code" not in entry and "pin" not in entry

        # Joining an open household by id needs no PIN and returns the code.
        joined = client.post(f"/households/{hid}/join", json={})
        assert joined.status_code == 200
        assert joined.json()["invite_code"] == body["invite_code"]


def test_household_size_defaults_when_unset() -> None:
    with TestClient(app) as client:
        body = client.post("/households", json={"name": "Default"}).json()
        assert body["settings"]["household_size"] == 2


def test_join_unknown_household_404() -> None:
    with TestClient(app) as client:
        assert client.post("/households/nope/join", json={}).status_code == 404


def test_codes_are_unique() -> None:
    with TestClient(app) as client:
        a = client.post("/households", json={"name": "A"}).json()["invite_code"]
        b = client.post("/households", json={"name": "B"}).json()["invite_code"]
        assert a != b


def test_create_with_client_supplied_identity_is_idempotent() -> None:
    """Attach-a-server flow: a serverless household brings its own id + invite code."""
    with TestClient(app) as client:
        payload = {
            "name": "Serverlos",
            "settings": {"household_size": 3},
            "id": "phone-uuid-1",
            "invite_code": "phone-code-abc",
        }
        created = client.post("/households", json=payload).json()
        assert created["household_id"] == "phone-uuid-1"
        assert created["invite_code"] == "phone-code-abc"

        # A second member repeating the attach gets the same household back.
        again = client.post("/households", json=payload)
        assert again.status_code == 201
        assert again.json()["household_id"] == "phone-uuid-1"

        # Same id with a different code must not take the household over.
        stolen = dict(payload, invite_code="wrong")
        assert client.post("/households", json=stolen).status_code == 409

        # A fresh id may not squat on an existing invite code either.
        clash = {"name": "Clash", "id": "phone-uuid-2", "invite_code": "phone-code-abc"}
        assert client.post("/households", json=clash).status_code == 409

        # The synced credential works exactly like a server-minted one.
        resp = client.post(
            "/sync", json={"merkle": {}, "messages": []},
            headers={"X-Household-Code": "phone-code-abc"},
        )
        assert resp.status_code == 200


def test_protected_household_requires_correct_pin() -> None:
    with TestClient(app) as client:
        hid = client.post(
            "/households", json={"name": "WG", "pin": "1234"}
        ).json()["household_id"]

        assert next(h for h in client.get("/households").json() if h["id"] == hid)["protected"]

        assert client.post(f"/households/{hid}/join", json={}).status_code == 403
        assert client.post(f"/households/{hid}/join", json={"pin": "0000"}).status_code == 403
        assert client.post(f"/households/{hid}/join", json={"pin": "1234"}).status_code == 200


def test_patch_merges_settings_and_keeps_unknown_keys() -> None:
    with TestClient(app) as client:
        body = client.post(
            "/households",
            json={"name": "X", "settings": {"household_size": 2, "vegetarian_days": 3}},
        ).json()
        hid, code = body["household_id"], body["invite_code"]

        # Patch only household_size; the forward-compatible key must survive.
        patched = client.patch(
            f"/households/{hid}",
            json={"settings": {"household_size": 5}},
            headers={"X-Household-Code": code},
        )
        assert patched.status_code == 200
        settings = patched.json()["settings"]
        assert settings["household_size"] == 5
        assert settings["vegetarian_days"] == 3


def test_patch_requires_matching_code() -> None:
    with TestClient(app) as client:
        hid = client.post("/households", json={"name": "A"}).json()["household_id"]
        assert client.patch(f"/households/{hid}", json={"name": "B"}).status_code == 400
        assert client.patch(
            f"/households/{hid}", json={"name": "B"}, headers={"X-Household-Code": "nope"}
        ).status_code == 404


def test_sync_response_carries_household_name_and_settings() -> None:
    with TestClient(app) as client:
        body = client.post(
            "/households", json={"name": "Sync", "settings": {"household_size": 3}}
        ).json()
        resp = client.post(
            "/sync", json={"merkle": {}, "messages": []},
            headers={"X-Household-Code": body["invite_code"]},
        ).json()
        assert resp["household_name"] == "Sync"
        assert resp["household_settings"]["household_size"] == 3
