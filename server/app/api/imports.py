"""Recipe-import inbox: the browser extension pushes a scraped schema.org/Recipe
(+ optional image) here; the next app to sync claims and materializes it.

Trust model is the same as sync: the ``X-Household-Code`` invite code scopes every
call to a household (LAN/VPN, no per-user auth). The recipe is already structured, so
there is no extraction worker — an app drains the inbox during its normal sync cycle.
Claiming is atomic so exactly one device materializes each import even with several
devices online; a stale claim (app crashed mid-drain) is reclaimable after a timeout.
"""

import json
import uuid
from datetime import datetime, timedelta, timezone
from pathlib import Path

from fastapi import APIRouter, Depends, File, Form, Header, HTTPException, UploadFile
from sqlalchemy import select, update
from sqlalchemy.orm import Session

from app.api._deps import resolve_household
from app.config import get_settings
from app.db import get_session
from app.models import ImportRecord, ImportStatus
from app.schemas import ImportCreatedResponse, PendingImport, PendingImportsResponse

router = APIRouter(prefix="/imports", tags=["imports"])

# A claim older than this is treated as abandoned (app crashed mid-drain) and the
# record becomes claimable again on the next /pending request.
_CLAIM_TTL = timedelta(minutes=5)


def _utcnow() -> datetime:
    return datetime.now(timezone.utc)


@router.post("", response_model=ImportCreatedResponse, status_code=201)
async def create_import(
    recipe: str = Form(...),
    image: UploadFile | None = File(default=None),
    source_url: str | None = Form(default=None),
    x_household_code: str | None = Header(default=None, alias="X-Household-Code"),
    session: Session = Depends(get_session),
) -> ImportCreatedResponse:
    """Accept a scraped recipe (schema.org JSON as a form field) + optional image."""
    household = resolve_household(x_household_code, session)

    # Validate the JSON early so a bad scrape fails here, not silently on the device.
    try:
        json.loads(recipe)
    except json.JSONDecodeError as exc:
        raise HTTPException(status_code=400, detail=f"Invalid recipe JSON: {exc}") from exc

    # Burst guard: if the same page is pushed again while an earlier import of it is still
    # pending (not yet drained by an app), reuse it instead of queuing a duplicate. The app's
    # name-based dedup is the real catch-all (also for already-saved recipes).
    if source_url:
        existing = session.scalars(
            select(ImportRecord).where(
                ImportRecord.household_id == household.id,
                ImportRecord.status == ImportStatus.PENDING,
                ImportRecord.source_url == source_url,
            )
        ).first()
        if existing is not None:
            return ImportCreatedResponse(import_id=existing.id)

    image_name: str | None = None
    if image is not None:
        suffix = Path(image.filename or "image.jpg").suffix or ".jpg"
        image_name = f"{uuid.uuid4()}{suffix}"
        (get_settings().images_dir / image_name).write_bytes(await image.read())

    record = ImportRecord(
        household_id=household.id,
        recipe_json=recipe,
        image_name=image_name,
        source_url=source_url,
    )
    session.add(record)
    session.commit()
    return ImportCreatedResponse(import_id=record.id)


def _reclaim_stale(household_id: str, session: Session) -> None:
    """Return long-claimed-but-unconsumed records to pending so a drain can retry."""
    cutoff = _utcnow() - _CLAIM_TTL
    session.execute(
        update(ImportRecord)
        .where(
            ImportRecord.household_id == household_id,
            ImportRecord.status == ImportStatus.CLAIMED,
            ImportRecord.claimed_at < cutoff,
        )
        .values(status=ImportStatus.PENDING, claimed_at=None)
    )
    session.commit()


@router.get("/pending", response_model=PendingImportsResponse)
def list_pending(
    x_household_code: str | None = Header(default=None, alias="X-Household-Code"),
    session: Session = Depends(get_session),
) -> PendingImportsResponse:
    household = resolve_household(x_household_code, session)
    _reclaim_stale(household.id, session)
    records = session.scalars(
        select(ImportRecord).where(
            ImportRecord.household_id == household.id,
            ImportRecord.status == ImportStatus.PENDING,
        )
    ).all()
    return PendingImportsResponse(
        imports=[
            PendingImport(
                id=r.id,
                recipe=json.loads(r.recipe_json),
                image_name=r.image_name,
                source_url=r.source_url,
            )
            for r in records
        ]
    )


@router.post("/{import_id}/claim", status_code=204)
def claim_import(
    import_id: str,
    x_household_code: str | None = Header(default=None, alias="X-Household-Code"),
    session: Session = Depends(get_session),
) -> None:
    """Atomically take ownership. Exactly one caller wins (204); others get 409."""
    household = resolve_household(x_household_code, session)
    result = session.execute(
        update(ImportRecord)
        .where(
            ImportRecord.id == import_id,
            ImportRecord.household_id == household.id,
            ImportRecord.status == ImportStatus.PENDING,
        )
        .values(status=ImportStatus.CLAIMED, claimed_at=_utcnow())
    )
    session.commit()
    if result.rowcount != 1:
        raise HTTPException(status_code=409, detail="Import already claimed or gone")


@router.post("/{import_id}/consume", status_code=204)
def consume_import(
    import_id: str,
    x_household_code: str | None = Header(default=None, alias="X-Household-Code"),
    session: Session = Depends(get_session),
) -> None:
    """Mark a claimed import as fully materialized so it stops appearing as pending."""
    household = resolve_household(x_household_code, session)
    record = session.get(ImportRecord, import_id)
    if record is None or record.household_id != household.id:
        raise HTTPException(status_code=404, detail="Import not found")
    record.status = ImportStatus.CONSUMED
    session.commit()
