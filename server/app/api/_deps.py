"""Shared API dependencies."""

from fastapi import HTTPException
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.models import Household


def resolve_household(code: str | None, session: Session) -> Household:
    """Map an ``X-Household-Code`` (invite code) to its household, or raise.

    The shared sync/import credential under the LAN/VPN trust model — there is no
    per-user auth. Missing header → 400; unknown code → 404."""
    if not code:
        raise HTTPException(status_code=400, detail="Missing X-Household-Code header")
    household = session.scalars(select(Household).where(Household.invite_code == code)).first()
    if household is None:
        raise HTTPException(status_code=404, detail="Unknown household")
    return household
