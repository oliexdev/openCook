"""Shared secret hashing — salted SHA-256, pure stdlib.

Used for both the optional household PIN and the server admin password. No external
crypto dependency (keeps the GPLv3-compatible / permanently-free constraint). This is
a LAN/VPN trust model, not internet-facing auth.
"""

import hashlib
import secrets


def make_salt() -> str:
    return secrets.token_hex(8)


def hash_secret(secret: str, salt: str) -> str:
    return hashlib.sha256((salt + secret).encode()).hexdigest()


def verify_secret(secret: str | None, salt: str | None, expected_hash: str | None) -> bool:
    """Constant-time check. Returns False if anything is missing/blank."""
    if not secret or not salt or not expected_hash:
        return False
    return secrets.compare_digest(hash_secret(secret, salt), expected_hash)
