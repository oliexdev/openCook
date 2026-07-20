"""Shared secret hashing — salted SHA-256, pure stdlib.

Used for both the optional household PIN and the server admin password. No external
crypto dependency (keeps the GPLv3-compatible / permanently-free constraint). This is
a LAN/VPN trust model, not internet-facing auth.
"""

import hashlib
import logging
import secrets

logger = logging.getLogger(__name__)


def make_salt() -> str:
    return secrets.token_hex(8)


def hash_secret(secret: str, salt: str) -> str:
    return hashlib.sha256((salt + secret).encode()).hexdigest()


def verify_secret(secret: str | None, salt: str | None, expected_hash: str | None) -> bool:
    """Constant-time check. Returns False if anything is missing/blank."""
    if not secret or not salt or not expected_hash:
        return False
    return secrets.compare_digest(hash_secret(secret, salt), expected_hash)


def seed_admin_password() -> None:
    """Apply ``OPENCOOK_ADMIN_PASSWORD`` on startup — but only when no password is set.

    Deliberately never overwrites an existing one: otherwise a password changed in the
    web console would silently revert on the next restart, and an operator who had
    removed the env var would be locked into a stale secret. Together with the console's
    first-visit "set password" form, this is how a fresh server gets protected now that
    the Android app no longer has an admin screen.
    """
    # Imported here rather than at module scope: app.db imports this module, and a
    # top-level import back into app.db would be circular.
    from app.config import get_settings
    from app.db import SessionLocal
    from app.models import ServerConfig

    password = get_settings().admin_password
    if not password:
        return
    with SessionLocal() as session:
        cfg = session.get(ServerConfig, "singleton")
        if cfg is None:
            cfg = ServerConfig(id="singleton")
            session.add(cfg)
        if cfg.admin_pw_hash is not None:
            return
        cfg.admin_pw_salt = make_salt()
        cfg.admin_pw_hash = hash_secret(password, cfg.admin_pw_salt)
        session.commit()
        logger.info("Admin password initialised from OPENCOOK_ADMIN_PASSWORD")
