"""Admin password bootstrap.

The Android app has no admin screen, so a fresh server gets its password either from
``OPENCOOK_ADMIN_PASSWORD`` at startup or from the web console's first-visit form
(``POST /admin/password`` without a current password). Both paths are covered here.
"""

import pytest
from fastapi.testclient import TestClient

from app.config import get_settings
from app.db import SessionLocal, init_db
from app.main import app
from app.models import ServerConfig
from app.security import hash_secret, make_salt, seed_admin_password, verify_secret


def _set_password(pw: str | None) -> None:
    init_db()
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


def _config() -> ServerConfig | None:
    session = SessionLocal()
    try:
        return session.get(ServerConfig, "singleton")
    finally:
        session.close()


@pytest.fixture
def env_password(monkeypatch: pytest.MonkeyPatch):
    """Point the cached settings at a known OPENCOOK_ADMIN_PASSWORD."""
    settings = get_settings()
    monkeypatch.setattr(settings, "admin_password", "vom-env-123", raising=False)
    yield "vom-env-123"


def test_env_var_sets_the_password_when_none_exists(env_password: str) -> None:
    _set_password(None)
    seed_admin_password()
    cfg = _config()
    assert cfg is not None
    assert verify_secret(env_password, cfg.admin_pw_salt, cfg.admin_pw_hash)


def test_env_var_never_overwrites_an_existing_password(env_password: str) -> None:
    """A password changed in the web console must survive a restart."""
    _set_password("selbst-gesetzt")
    seed_admin_password()
    cfg = _config()
    assert cfg is not None
    assert verify_secret("selbst-gesetzt", cfg.admin_pw_salt, cfg.admin_pw_hash)
    assert not verify_secret(env_password, cfg.admin_pw_salt, cfg.admin_pw_hash)


def test_seeding_is_a_no_op_without_the_env_var(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr(get_settings(), "admin_password", None, raising=False)
    _set_password(None)
    seed_admin_password()
    cfg = _config()
    assert cfg is None or cfg.admin_pw_hash is None


def test_console_can_set_the_first_password_without_a_current_one() -> None:
    _set_password(None)
    with TestClient(app) as client:
        # The gate reads this to decide between "log in" and "set a password".
        assert client.get("/admin/status").json()["configured"] is False
        assert client.post("/admin/password", json={"new_password": "erstes-pw"}).status_code == 204
        assert client.get("/admin/status").json()["configured"] is True
        assert client.post("/admin/verify", headers={"X-Admin-Password": "erstes-pw"}).status_code == 204


def test_changing_a_set_password_still_requires_the_current_one() -> None:
    _set_password("altes-pw")
    with TestClient(app) as client:
        assert client.post("/admin/password", json={"new_password": "neues"}).status_code == 401
        assert client.post(
            "/admin/password",
            json={"current_password": "altes-pw", "new_password": "neues"},
        ).status_code == 204
