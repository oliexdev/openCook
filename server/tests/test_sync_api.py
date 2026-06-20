from fastapi.testclient import TestClient

from app.main import app
from app.sync import merkle_build, merkle_to_dict


def _new_household(client: TestClient) -> dict:
    code = client.post("/households", json={"name": "Test"}).json()["invite_code"]
    return {"X-Household-Code": code}


def _msg(ts: str, value: str) -> dict:
    return {"timestamp": ts, "dataset": "recipes", "row_id": "r1", "column": "name", "value": value}


def test_second_device_pulls_first_devices_message() -> None:
    with TestClient(app) as client:
        headers = _new_household(client)
        a_ts = "000000001000000-0000-A"
        client.post("/sync", json={"merkle": {}, "messages": [_msg(a_ts, '"A"')]}, headers=headers)

        # Device B with an empty trie pulls A's message.
        resp = client.post("/sync", json={"merkle": {}, "messages": []}, headers=headers).json()
        by_ts = {m["timestamp"]: m for m in resp["messages"]}
        assert a_ts in by_ts
        assert by_ts[a_ts]["value"] == '"A"'


def test_in_sync_returns_nothing() -> None:
    with TestClient(app) as client:
        headers = _new_household(client)
        a_ts = "000000002000000-0000-A"
        client.post("/sync", json={"merkle": {}, "messages": [_msg(a_ts, '"X"')]}, headers=headers)

        # Client whose trie already matches the server gets an empty response.
        client_merkle = merkle_to_dict(merkle_build([a_ts]))
        resp = client.post("/sync", json={"merkle": client_merkle, "messages": []}, headers=headers).json()
        assert resp["messages"] == []


def test_pushes_are_idempotent() -> None:
    with TestClient(app) as client:
        headers = _new_household(client)
        a_ts = "000000003000000-0000-A"
        body = {"merkle": {}, "messages": [_msg(a_ts, '"once"')]}
        client.post("/sync", json=body, headers=headers)
        client.post("/sync", json=body, headers=headers)  # same message again

        resp = client.post("/sync", json={"merkle": {}, "messages": []}, headers=headers).json()
        assert sum(1 for m in resp["messages"] if m["timestamp"] == a_ts) == 1


def test_missing_or_unknown_household() -> None:
    with TestClient(app) as client:
        assert client.post("/sync", json={"merkle": {}, "messages": []}).status_code == 400
        bad = {"X-Household-Code": "definitely-not-real"}
        assert client.post("/sync", json={"merkle": {}, "messages": []}, headers=bad).status_code == 404
