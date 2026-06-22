"""Read-only DB viewer + structured sync-log views served under /admin/db.

Shares the disposable dev DB like the other API tests; each test seeds what it
needs and sets the admin password directly via the DB to be order-independent.
"""

import time
import uuid

import pytest
from fastapi.testclient import TestClient

from app.db import SessionLocal, init_db
from app.main import app
from app.models import Job, JobStatus, ServerConfig
from app.security import hash_secret, make_salt


def _set_password(pw: str) -> None:
    init_db()
    session = SessionLocal()
    try:
        cfg = session.get(ServerConfig, "singleton") or ServerConfig(id="singleton")
        cfg.admin_pw_salt = make_salt()
        cfg.admin_pw_hash = hash_secret(pw, cfg.admin_pw_salt)
        session.add(cfg)
        session.commit()
    finally:
        session.close()


@pytest.fixture
def admin_pw() -> str:
    pw = "geheim123"
    _set_password(pw)
    return pw


def _auth(pw: str) -> dict:
    return {"X-Admin-Password": pw}


def test_endpoints_require_auth(admin_pw: str) -> None:
    with TestClient(app) as client:
        assert client.get("/admin/db/tables").status_code == 401
        assert client.get("/admin/db/tables/jobs").status_code == 401
        assert client.get("/admin/db/sync").status_code == 401


def test_list_tables_covers_models_with_counts(admin_pw: str) -> None:
    with TestClient(app) as client:
        data = client.get("/admin/db/tables", headers=_auth(admin_pw)).json()
        names = {t["name"] for t in data["tables"]}
        assert {"households", "server_config", "messages", "imports", "jobs"} <= names
        for t in data["tables"]:
            assert isinstance(t["row_count"], int) and t["row_count"] >= 0
            assert t["columns"] and all("name" in c and "type" in c for c in t["columns"])


def test_table_rows_pagination_and_validation(admin_pw: str) -> None:
    with TestClient(app) as client:
        # Seed two households so there's something to page through.
        client.post("/households", json={"name": "Alpha"})
        client.post("/households", json={"name": "Beta"})

        page = client.get(
            "/admin/db/tables/households",
            params={"limit": 1, "offset": 0, "order_by": "name", "direction": "asc"},
            headers=_auth(admin_pw),
        ).json()
        assert page["limit"] == 1 and len(page["rows"]) == 1
        assert page["total"] >= 2
        assert "name" in page["columns"]

        # Unknown table → 404; bad order column → 400.
        assert client.get("/admin/db/tables/nope", headers=_auth(admin_pw)).status_code == 404
        assert client.get(
            "/admin/db/tables/households",
            params={"order_by": "no_such_col"}, headers=_auth(admin_pw),
        ).status_code == 400
        # limit over the cap → 422 (FastAPI validation).
        assert client.get(
            "/admin/db/tables/households", params={"limit": 99999}, headers=_auth(admin_pw)
        ).status_code == 422


def test_secret_columns_are_redacted(admin_pw: str) -> None:
    with TestClient(app) as client:
        page = client.get("/admin/db/tables/server_config", headers=_auth(admin_pw)).json()
        row = page["rows"][0]
        # The real hash/salt are set (admin_pw fixture) but must be masked, not leaked.
        assert row["admin_pw_hash"] == "•••"
        assert row["admin_pw_salt"] == "•••"


def test_sync_log_reconstructs_entities_with_stats(admin_pw: str) -> None:
    with TestClient(app) as client:
        body = client.post("/households", json={"name": "SyncHH"}).json()
        code, hid = body["invite_code"], body["household_id"]
        # Unique HLC timestamps + row id so this run's data is isolated on the shared
        # dev DB (avoids idempotent-by-timestamp skips and cross-test row_id clashes).
        node = uuid.uuid4().hex[:6].upper()
        base = int(time.time() * 1000)
        ts = lambda n: f"{base + n:015d}-0000-{node}"  # noqa: E731
        rid = f"r-{node}"
        client.post(
            "/sync",
            json={"merkle": {}, "messages": [
                {"timestamp": ts(1), "dataset": "recipes", "row_id": rid, "column": "name", "value": "\"Old\""},
                {"timestamp": ts(2), "dataset": "recipes", "row_id": rid, "column": "name", "value": "\"New\""},
                {"timestamp": ts(3), "dataset": "recipes", "row_id": rid, "column": "servings", "value": "4"}]},
            headers={"X-Household-Code": code},
        )

        # Scoped to this household → exactly our one row / three messages.
        page = client.get(
            "/admin/db/sync/recipes", params={"household_id": hid}, headers=_auth(admin_pw)
        ).json()
        row = next(r for r in page["rows"] if r["row_id"] == rid)
        assert row["fields"]["name"] == "New"   # latest value wins
        assert row["fields"]["servings"] == 4   # JSON-decoded to an int
        assert row["status"] == "edited" and row["edited"] is True
        assert row["message_count"] == 3

        st = page["stats"]
        assert st["total_messages"] == 3
        assert (st["adds"], st["edits"], st["deletes"]) == (2, 1, 0)  # 2 first-writes, 1 rewrite
        assert (st["rows_total"], st["rows_live"], st["rows_deleted"]) == (1, 1, 0)
        assert st["adds"] + st["edits"] + st["deletes"] == st["total_messages"]

        # Dataset summary, scoped.
        scoped = client.get(
            "/admin/db/sync", params={"household_id": hid}, headers=_auth(admin_pw)
        ).json()["datasets"]
        rec = next(d for d in scoped if d["name"] == "recipes")
        assert rec["row_count"] == 1 and rec["message_count"] == 3

        # A nonexistent household sees nothing; the endpoints are gated.
        assert client.get(
            "/admin/db/sync", params={"household_id": "no-such-hh"}, headers=_auth(admin_pw)
        ).json()["datasets"] == []
        assert client.get("/admin/db/sync").status_code == 401


def test_recipe_resolves_original_scan_from_source_photo(admin_pw: str) -> None:
    with TestClient(app) as client:
        body = client.post("/households", json={"name": "ScanHH"}).json()
        code, hid = body["invite_code"], body["household_id"]
        node = uuid.uuid4().hex[:6].upper()
        base = int(time.time() * 1000)
        ts = lambda n: f"{base + n:015d}-0000-{node}"  # noqa: E731

        # A Job whose original photo lives in the images dir (served by /images/{name}).
        job_id = str(uuid.uuid4())
        original = f"{uuid.uuid4().hex}.jpg"
        session = SessionLocal()
        try:
            session.add(Job(id=job_id, status=JobStatus.DONE, image_path=f"/data/images/{original}"))
            session.commit()
        finally:
            session.close()

        rid = f"rec-{node}"
        client.post(
            "/sync",
            json={"merkle": {}, "messages": [
                {"timestamp": ts(1), "dataset": "recipes", "row_id": rid, "column": "name", "value": "\"Cake\""},
                {"timestamp": ts(2), "dataset": "recipes", "row_id": rid, "column": "sourcePhotoId",
                 "value": f"\"{job_id}\""}]},
            headers={"X-Household-Code": code},
        )
        page = client.get(
            "/admin/db/sync/recipes", params={"household_id": hid}, headers=_auth(admin_pw)
        ).json()
        row = next(r for r in page["rows"] if r["row_id"] == rid)
        # Synthetic field resolved from sourcePhotoId -> job -> original filename.
        assert row["fields"]["originalScan"] == original


def test_sync_log_marks_tombstoned_rows_deleted(admin_pw: str) -> None:
    with TestClient(app) as client:
        body = client.post("/households", json={"name": "TombHH"}).json()
        code, hid = body["invite_code"], body["household_id"]
        node = uuid.uuid4().hex[:6].upper()
        base = int(time.time() * 1000)
        ts = lambda n: f"{base + n:015d}-0000-{node}"  # noqa: E731
        rid = f"t-{node}"
        client.post(
            "/sync",
            json={"merkle": {}, "messages": [
                {"timestamp": ts(1), "dataset": "shopping", "row_id": rid, "column": "name", "value": "\"Milk\""},
                {"timestamp": ts(2), "dataset": "shopping", "row_id": rid, "column": "_deleted", "value": "true"}]},
            headers={"X-Household-Code": code},
        )
        page = client.get(
            "/admin/db/sync/shopping", params={"household_id": hid}, headers=_auth(admin_pw)
        ).json()
        row = next(r for r in page["rows"] if r["row_id"] == rid)
        assert row["status"] == "deleted"
        st = page["stats"]
        assert st["deletes"] == 1 and st["rows_deleted"] == 1 and st["rows_live"] == 0


def test_admin_set_change_and_clear_household_pin(admin_pw: str) -> None:
    with TestClient(app) as client:
        hid = client.post("/households", json={"name": "Pinned"}).json()["household_id"]

        # Set a PIN → protected.
        r = client.patch(f"/admin/households/{hid}", json={"pin": "1234"}, headers=_auth(admin_pw))
        assert r.status_code == 200 and r.json()["protected"] is True
        # The join flow now enforces it.
        assert client.post(f"/households/{hid}/join", json={"pin": "9999"}).status_code == 403
        assert client.post(f"/households/{hid}/join", json={"pin": "1234"}).status_code == 200

        # Rename via the same endpoint.
        assert client.patch(
            f"/admin/households/{hid}", json={"name": "Renamed"}, headers=_auth(admin_pw)
        ).json()["name"] == "Renamed"

        # Clear the PIN → open again.
        r = client.patch(f"/admin/households/{hid}", json={"pin": ""}, headers=_auth(admin_pw))
        assert r.json()["protected"] is False
        assert client.post(f"/households/{hid}/join", json={}).status_code == 200

        assert client.patch(f"/admin/households/{hid}", json={"pin": "1"}).status_code == 401  # gated
        assert client.patch(
            "/admin/households/nope", json={"name": "x"}, headers=_auth(admin_pw)
        ).status_code == 404


def test_ui_page_served_without_auth() -> None:
    with TestClient(app) as client:
        for path in ("/admin", "/admin/", "/admin/db"):
            res = client.get(path)
            assert res.status_code == 200, f"{path} -> {res.status_code}"
            assert "text/html" in res.headers["content-type"]
            assert "openCook" in res.text
