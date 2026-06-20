"""Admin API: password gating + backup/restore over HTTP.

These share the (disposable) dev DB like the other API tests, so each test sets or
clears the admin password directly via the DB to be independent of prior state.
"""

import pytest
from fastapi.testclient import TestClient

from app.db import SessionLocal, init_db
from app.main import app
from app.models import ServerConfig
from app.security import hash_secret, make_salt


def _set_password(pw: str | None) -> None:
    """Force the server admin password to a known value (or clear it) via the DB."""
    init_db()  # ensure tables exist even before a TestClient lifespan has run
    session = SessionLocal()
    try:
        cfg = session.get(ServerConfig, "singleton") or ServerConfig(id="singleton")
        if pw is None:
            cfg.admin_pw_hash = cfg.admin_pw_salt = None
        else:
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


def test_status_and_verify(admin_pw: str) -> None:
    with TestClient(app) as client:
        assert client.get("/admin/status").json()["configured"] is True
        assert client.post("/admin/verify", headers=_auth(admin_pw)).status_code == 204
        assert client.post("/admin/verify", headers=_auth("falsch")).status_code == 401
        assert client.post("/admin/verify").status_code == 401  # no header


def test_set_password_when_unset_then_change() -> None:
    _set_password(None)
    with TestClient(app) as client:
        assert client.get("/admin/status").json()["configured"] is False
        # No current password needed while unset.
        assert client.post("/admin/password", json={"new_password": "erst"}).status_code == 204
        assert client.get("/admin/status").json()["configured"] is True
        assert client.post("/admin/verify", headers=_auth("erst")).status_code == 204

        # Changing requires the correct current password.
        assert client.post(
            "/admin/password", json={"current_password": "falsch", "new_password": "neu"}
        ).status_code == 401
        assert client.post(
            "/admin/password", json={"current_password": "erst", "new_password": "neu"}
        ).status_code == 204
        assert client.post("/admin/verify", headers=_auth("erst")).status_code == 401
        assert client.post("/admin/verify", headers=_auth("neu")).status_code == 204


def test_household_create_sets_admin_password_first_write_wins() -> None:
    _set_password(None)
    with TestClient(app) as client:
        client.post("/households", json={"name": "A", "admin_password": "pw1"})
        assert client.post("/admin/verify", headers=_auth("pw1")).status_code == 204
        # A later create carrying a different password must NOT take it over.
        client.post("/households", json={"name": "B", "admin_password": "pw2"})
        assert client.post("/admin/verify", headers=_auth("pw1")).status_code == 204
        assert client.post("/admin/verify", headers=_auth("pw2")).status_code == 401


def test_backups_require_auth_create_list_download(admin_pw: str) -> None:
    with TestClient(app) as client:
        assert client.get("/admin/backups").status_code == 401

        created = client.post("/admin/backups", headers=_auth(admin_pw))
        assert created.status_code == 200
        info = created.json()
        assert info["id"].startswith("opencook-backup-") and info["id"].endswith(".tar.gz")
        assert info["size_bytes"] > 0

        listed = client.get("/admin/backups", headers=_auth(admin_pw)).json()["backups"]
        assert any(b["id"] == info["id"] for b in listed)

        dl = client.get(f"/admin/backups/{info['id']}", headers=_auth(admin_pw))
        assert dl.status_code == 200 and dl.content[:2] == b"\x1f\x8b"  # gzip magic

        assert client.get("/admin/backups/../etc", headers=_auth(admin_pw)).status_code in (400, 404)
        assert client.get(
            "/admin/backups/not-a-backup.txt", headers=_auth(admin_pw)
        ).status_code == 400


def test_reset_wipes_data_keeps_backups_and_password(admin_pw: str) -> None:
    with TestClient(app) as client:
        # Seed a household + a synced message, and make a backup.
        body = client.post("/households", json={"name": "ResetMe"}).json()
        code = body["invite_code"]
        client.post(
            "/sync",
            json={"merkle": {}, "messages": [
                {"timestamp": "000000009000000-0000-A", "dataset": "recipes",
                 "row_id": "r1", "column": "name", "value": "\"X\""}]},
            headers={"X-Household-Code": code},
        )
        backup_id = client.post("/admin/backups", headers=_auth(admin_pw)).json()["id"]

        assert client.post("/admin/reset").status_code == 401  # needs auth
        assert client.post("/admin/reset", headers=_auth(admin_pw)).status_code == 204

        # Households are gone (the old code no longer resolves).
        assert client.get("/households").json() == [] or all(
            h["id"] != body["household_id"] for h in client.get("/households").json()
        )
        # Backups survive, and the admin password still works.
        listed = client.get("/admin/backups", headers=_auth(admin_pw)).json()["backups"]
        assert any(b["id"] == backup_id for b in listed)
        assert client.post("/admin/verify", headers=_auth(admin_pw)).status_code == 204


def test_delete_household_requires_auth_and_removes_it(admin_pw: str) -> None:
    with TestClient(app) as client:
        body = client.post("/households", json={"name": "DeleteMe"}).json()
        hid, code = body["household_id"], body["invite_code"]
        # Seed a sync message scoped to it.
        client.post(
            "/sync",
            json={"merkle": {}, "messages": [
                {"timestamp": "000000008000000-0000-A", "dataset": "recipes",
                 "row_id": "r1", "column": "name", "value": "\"Y\""}]},
            headers={"X-Household-Code": code},
        )
        assert client.delete(f"/admin/households/{hid}").status_code == 401  # needs auth
        assert client.delete(f"/admin/households/{hid}", headers=_auth(admin_pw)).status_code == 204
        # Gone: the code no longer resolves and it's absent from the list.
        assert client.get("/imports/pending", headers={"X-Household-Code": code}).status_code == 404
        assert all(h["id"] != hid for h in client.get("/households").json())
        # Deleting a missing one → 404.
        assert client.delete(f"/admin/households/{hid}", headers=_auth(admin_pw)).status_code == 404


def test_restore_requires_auth_and_roundtrips(admin_pw: str) -> None:
    with TestClient(app) as client:
        assert client.post("/admin/restore", json={"backup_id": "x"}).status_code == 401
        assert client.post(
            "/admin/restore", json={"backup_id": "opencook-backup-nope.tar.gz"},
            headers=_auth(admin_pw),
        ).status_code == 404

        backup_id = client.post("/admin/backups", headers=_auth(admin_pw)).json()["id"]
        restored = client.post(
            "/admin/restore", json={"backup_id": backup_id}, headers=_auth(admin_pw)
        )
        assert restored.status_code == 200
        assert restored.json() == {"restored": True, "restart_recommended": True}
        # Engine recovered after dispose(): the server still answers.
        assert client.get("/admin/status").status_code == 200
