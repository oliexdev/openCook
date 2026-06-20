"""Delta-sync endpoint: push the client's new messages, pull what it's missing.

The Merkle trie over message timestamps lets us return only messages from the
point the two logs diverge, instead of the whole household history.
"""

import json

from fastapi import APIRouter, Depends, Header
from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.api._deps import resolve_household
from app.db import get_session
from app.models import SyncMessage
from app.schemas import SyncMessageDto, SyncRequest, SyncResponse
from app.sync import Hlc, Merkle, merkle_build, merkle_diff, merkle_from_dict, merkle_to_dict

router = APIRouter(tags=["sync"])

# Per-household cached Merkle trie, keyed by message count. The log is append-only
# (deletes are appended tombstones), so the count only ever grows and is a perfect,
# stale-proof cache key: same count ⟺ identical message set. This avoids rebuilding the
# whole trie (~200 ms on a large household) on every poll. Single-process server → one
# shared cache; a count mismatch (new messages, restore, reset) triggers a rebuild.
_merkle_cache: dict[str, tuple[int, Merkle]] = {}


@router.post("/sync", response_model=SyncResponse)
def sync(
    request: SyncRequest,
    x_household_code: str | None = Header(default=None, alias="X-Household-Code"),
    session: Session = Depends(get_session),
) -> SyncResponse:
    household = resolve_household(x_household_code, session)

    # 1. Store pushed messages (idempotent by timestamp).
    for message in request.messages:
        if session.get(SyncMessage, message.timestamp) is None:
            session.add(
                SyncMessage(
                    timestamp=message.timestamp,
                    household_id=household.id,
                    dataset=message.dataset,
                    row_id=message.row_id,
                    col_key=message.column,
                    value=message.value,
                )
            )
    session.commit()

    # 2. Build (or reuse a cached) server trie. The cache is keyed by message count; on a
    #    steady-state poll nothing changed, so we skip both the row load and the rebuild.
    count = session.scalar(
        select(func.count())
        .select_from(SyncMessage)
        .where(SyncMessage.household_id == household.id)
    )
    cached = _merkle_cache.get(household.id)
    if cached is not None and cached[0] == count:
        server_merkle = cached[1]
    else:
        timestamps = session.scalars(
            select(SyncMessage.timestamp).where(SyncMessage.household_id == household.id)
        ).all()
        server_merkle = merkle_build(list(timestamps))
        _merkle_cache[household.id] = (count, server_merkle)

    # 3. Diff against the client's trie to find where it's behind. Only when there *is* a
    #    divergence do we load the rows to return — the common (caught-up) poll loads none.
    cursor = merkle_diff(server_merkle, merkle_from_dict(request.merkle))
    missing: list[SyncMessage] = []
    if cursor is not None:
        rows = session.scalars(
            select(SyncMessage).where(SyncMessage.household_id == household.id)
        ).all()
        missing = [m for m in rows if Hlc.parse(m.timestamp).millis >= cursor]

    return SyncResponse(
        messages=[
            SyncMessageDto(
                timestamp=m.timestamp,
                dataset=m.dataset,
                row_id=m.row_id,
                column=m.col_key,
                value=m.value,
            )
            for m in missing
        ],
        merkle=merkle_to_dict(server_merkle),
        household_name=household.name,
        household_settings=json.loads(household.settings_json),
    )
