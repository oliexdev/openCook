"""Household endpoints: discover-and-join flow.

The app lists households (``GET``), then joins one by id (open ones directly,
PIN-protected ones with a PIN) or creates a new one. The ``invite_code`` is still
the sync credential but is handed back only to members, never shown in the list.
"""

import json
import secrets

from fastapi import APIRouter, Depends, Header, HTTPException
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.db import get_session
from app.models import Household, ServerConfig
from app.schemas import (
    HouseholdCreateRequest,
    HouseholdJoinRequest,
    HouseholdPatchRequest,
    HouseholdResponse,
    HouseholdSettings,
    HouseholdSummary,
)
from app.security import hash_secret, make_salt, verify_secret

router = APIRouter(prefix="/households", tags=["households"])


def _normalize_settings(raw: dict) -> dict:
    """Apply defaults (household_size) while keeping any extra/forward-compatible keys."""
    return HouseholdSettings(**(raw or {})).model_dump()


def _set_pin(household: Household, pin: str | None) -> None:
    """Set (or leave) the PIN. A blank pin clears protection."""
    if pin is None:
        return
    if pin == "":
        household.pin_hash = household.pin_salt = None
        return
    household.pin_salt = make_salt()
    household.pin_hash = hash_secret(pin, household.pin_salt)


def _verify_pin(household: Household, pin: str | None) -> bool:
    if household.pin_hash is None:
        return True  # open household
    return verify_secret(pin, household.pin_salt, household.pin_hash)


def _set_admin_password_if_unset(password: str | None, session: Session) -> None:
    """Set the server-wide admin password the first time one is provided. If one
    already exists, this is a no-op (use POST /admin/password to change it)."""
    if not password:
        return
    cfg = session.get(ServerConfig, "singleton")
    if cfg is None:
        cfg = ServerConfig(id="singleton")
        session.add(cfg)
    if cfg.admin_pw_hash is not None:
        return  # already set — don't let a later create take it over
    cfg.admin_pw_salt = make_salt()
    cfg.admin_pw_hash = hash_secret(password, cfg.admin_pw_salt)


def _to_response(household: Household) -> HouseholdResponse:
    return HouseholdResponse(
        household_id=household.id,
        invite_code=household.invite_code,
        name=household.name,
        settings=json.loads(household.settings_json),
    )


def _resolve_by_code(code: str | None, session: Session) -> Household:
    if not code:
        raise HTTPException(status_code=400, detail="Missing X-Household-Code header")
    household = session.scalars(select(Household).where(Household.invite_code == code)).first()
    if household is None:
        raise HTTPException(status_code=404, detail="Unknown household")
    return household


@router.get("", response_model=list[HouseholdSummary])
def list_households(session: Session = Depends(get_session)) -> list[HouseholdSummary]:
    households = session.scalars(select(Household).order_by(Household.created_at)).all()
    return [
        HouseholdSummary(
            id=h.id,
            name=h.name,
            settings=json.loads(h.settings_json),
            protected=h.pin_hash is not None,
            created_at=h.created_at,
        )
        for h in households
    ]


@router.post("", response_model=HouseholdResponse, status_code=201)
def create_household(
    body: HouseholdCreateRequest, session: Session = Depends(get_session)
) -> HouseholdResponse:
    # Attach-a-server flow: a serverless household brings its own id + invite code so
    # existing members stay valid. Idempotent when the id already exists (a second
    # member repeating the attach), guarded by the matching code.
    if body.id is not None:
        existing = session.get(Household, body.id)
        if existing is not None:
            if body.invite_code != existing.invite_code:
                raise HTTPException(status_code=409, detail="Household exists with a different code")
            return _to_response(existing)
        if body.invite_code is not None:
            clash = session.scalars(
                select(Household).where(Household.invite_code == body.invite_code)
            ).first()
            if clash is not None:
                raise HTTPException(status_code=409, detail="Invite code already in use")

    # token_urlsafe(12) -> ~16 chars of entropy: long/random, as the code is the
    # shared sync credential (don't expose to the internet without VPN/TLS).
    household = Household(
        invite_code=body.invite_code or secrets.token_urlsafe(12),
        name=body.name.strip(),
        settings_json=json.dumps(_normalize_settings(body.settings)),
    )
    if body.id is not None:
        household.id = body.id
    _set_pin(household, body.pin)
    session.add(household)
    _set_admin_password_if_unset(body.admin_password, session)
    session.commit()
    return _to_response(household)


@router.post("/{household_id}/join", response_model=HouseholdResponse)
def join_household(
    household_id: str,
    body: HouseholdJoinRequest,
    session: Session = Depends(get_session),
) -> HouseholdResponse:
    household = session.get(Household, household_id)
    if household is None:
        raise HTTPException(status_code=404, detail="Unknown household")
    if not _verify_pin(household, body.pin):
        raise HTTPException(status_code=403, detail="Wrong or missing PIN")
    return _to_response(household)


@router.patch("/{household_id}", response_model=HouseholdResponse)
def patch_household(
    household_id: str,
    body: HouseholdPatchRequest,
    x_household_code: str | None = Header(default=None, alias="X-Household-Code"),
    session: Session = Depends(get_session),
) -> HouseholdResponse:
    household = _resolve_by_code(x_household_code, session)
    if household.id != household_id:
        raise HTTPException(status_code=403, detail="Code does not match household")
    if body.name is not None:
        household.name = body.name.strip()
    if body.settings is not None:
        # Merge partial settings into the existing dict (don't clobber other keys).
        merged = {**json.loads(household.settings_json), **body.settings}
        household.settings_json = json.dumps(_normalize_settings(merged))
    _set_pin(household, body.pin)
    session.commit()
    return _to_response(household)
