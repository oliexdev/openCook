"""Password-gated server administration: backup & restore over HTTP.

Trust model is LAN/VPN (no per-user auth). A single server-wide admin password —
set at household creation or via ``POST /admin/password`` — gates every endpoint here
except ``GET /admin/status``. The password is sent per request in the ``X-Admin-Password``
header and verified constant-time against the stored salted hash (``ServerConfig``).

Backups reuse :mod:`app.backup`; the same archives the CLI/cron path produces.
"""

import enum
import json
import shutil
import tempfile
import threading
from datetime import datetime, timezone
from pathlib import Path

from fastapi import APIRouter, Depends, File, Header, HTTPException, Query, UploadFile
from fastapi.responses import FileResponse
from sqlalchemy import distinct, func, select
from sqlalchemy.orm import Session

from app.backup import _PREFIX, create_backup, restore_backup
from app.config import get_settings
from app.db import Base, engine, get_session
from app.models import Household, ImportRecord, Job, ServerConfig, SyncMessage
from app.schemas import (
    AdminHouseholdPatch,
    AdminPasswordChangeRequest,
    AdminStatusResponse,
    BackupInfo,
    BackupListResponse,
    DbColumn,
    DbSyncColumnCount,
    DbSyncDataset,
    DbSyncDatasetList,
    DbSyncRow,
    DbSyncRows,
    DbSyncStats,
    DbTableInfo,
    DbTableListResponse,
    DbTablePage,
    HouseholdSummary,
    RestoreRequest,
    RestoreResult,
)
from app.security import hash_secret, make_salt, verify_secret

router = APIRouter(prefix="/admin", tags=["admin"])

# Serialize restores so a concurrent sync write can't race the DB-file swap.
_restore_lock = threading.Lock()

# Where the embedded DB-admin web page lives (served by GET /admin/db).
_STATIC_DIR = Path(__file__).resolve().parent.parent / "static"

# (table, column) pairs whose values are secrets (salted hashes) — masked in the
# read-only DB viewer so password/PIN hashes never reach the browser.
_REDACTED_COLUMNS: set[tuple[str, str]] = {
    ("server_config", "admin_pw_hash"),
    ("server_config", "admin_pw_salt"),
    ("households", "pin_hash"),
    ("households", "pin_salt"),
}

# Hard cap for a single page of the read-only DB viewer.
_MAX_PAGE_LIMIT = 500

# The sync tombstone column (mirror of app SyncDatasets.COLUMN_DELETED): a row is
# deleted across devices when this flips to true.
_DELETED_COL = "_deleted"


def _is_truthy(value: str) -> bool:
    """Whether a JSON-encoded sync value means boolean true ("true" / true)."""
    try:
        return json.loads(value) is True
    except (ValueError, TypeError):
        return value.strip().lower() == "true"


def _get_config(session: Session) -> ServerConfig | None:
    return session.get(ServerConfig, "singleton")


def require_admin(
    x_admin_password: str | None = Header(default=None, alias="X-Admin-Password"),
    session: Session = Depends(get_session),
) -> None:
    """Reject unless the request carries the correct admin password."""
    cfg = _get_config(session)
    if cfg is None or cfg.admin_pw_hash is None:
        raise HTTPException(status_code=409, detail="Admin password not set")
    if not verify_secret(x_admin_password, cfg.admin_pw_salt, cfg.admin_pw_hash):
        raise HTTPException(status_code=401, detail="Wrong admin password")


def _resolve_backup(backup_id: str) -> Path:
    """Resolve a backup id to a file inside the backups dir, guarding traversal."""
    if "/" in backup_id or "\\" in backup_id or backup_id in ("", ".", ".."):
        raise HTTPException(status_code=400, detail="Invalid backup name")
    if not backup_id.startswith(_PREFIX) or not backup_id.endswith(".tar.gz"):
        raise HTTPException(status_code=400, detail="Not an openCook backup")
    backups_dir = get_settings().backups_dir.resolve()
    path = (backups_dir / backup_id).resolve()
    if backups_dir not in path.parents or not path.is_file():
        raise HTTPException(status_code=404, detail="Backup not found")
    return path


def _to_info(path: Path) -> BackupInfo:
    stat = path.stat()
    return BackupInfo(
        id=path.name,
        created_at=datetime.fromtimestamp(stat.st_mtime, tz=timezone.utc),
        size_bytes=stat.st_size,
    )


@router.get("/status", response_model=AdminStatusResponse)
def admin_status(session: Session = Depends(get_session)) -> AdminStatusResponse:
    cfg = _get_config(session)
    return AdminStatusResponse(configured=cfg is not None and cfg.admin_pw_hash is not None)


@router.post("/password", status_code=204)
def set_admin_password(
    body: AdminPasswordChangeRequest, session: Session = Depends(get_session)
) -> None:
    """Set the admin password (no current needed when unset) or change it (current
    password required and must verify)."""
    cfg = _get_config(session)
    if cfg is None:
        cfg = ServerConfig(id="singleton")
        session.add(cfg)
    if cfg.admin_pw_hash is not None:
        if not verify_secret(body.current_password, cfg.admin_pw_salt, cfg.admin_pw_hash):
            raise HTTPException(status_code=401, detail="Wrong current password")
    if not body.new_password:
        raise HTTPException(status_code=400, detail="New password must not be empty")
    cfg.admin_pw_salt = make_salt()
    cfg.admin_pw_hash = hash_secret(body.new_password, cfg.admin_pw_salt)
    session.commit()


@router.post("/verify", status_code=204, dependencies=[Depends(require_admin)])
def verify_admin() -> None:
    """No-op 204 used by the app to validate the password before unlocking the screen."""
    return None


@router.get("/backups", response_model=BackupListResponse, dependencies=[Depends(require_admin)])
def list_backups() -> BackupListResponse:
    backups_dir = get_settings().backups_dir
    archives = sorted(backups_dir.glob(f"{_PREFIX}*.tar.gz"), reverse=True)
    return BackupListResponse(backups=[_to_info(p) for p in archives])


@router.post("/backups", response_model=BackupInfo, dependencies=[Depends(require_admin)])
def make_backup() -> BackupInfo:
    settings = get_settings()
    archive = create_backup(
        settings.db_path, settings.images_dir, settings.backups_dir, settings.backup_keep
    )
    return _to_info(archive)


@router.get("/backups/{backup_id}", dependencies=[Depends(require_admin)])
def download_backup(backup_id: str) -> FileResponse:
    path = _resolve_backup(backup_id)
    return FileResponse(path, media_type="application/gzip", filename=path.name)


def _do_restore(archive: Path) -> None:
    """Restore then drop the connection pool so new sessions reopen the new DB file."""
    settings = get_settings()
    with _restore_lock:
        restore_backup(archive, settings.db_path, settings.images_dir)
        # New connections re-open the (now replaced) file; without this the running
        # server keeps reading the old DB. A full uvicorn restart is still safest.
        engine.dispose()


@router.post("/restore", response_model=RestoreResult, dependencies=[Depends(require_admin)])
def restore_from_id(body: RestoreRequest) -> RestoreResult:
    """Restore from an existing server-side backup. Replaces ALL current data."""
    archive = _resolve_backup(body.backup_id)
    _do_restore(archive)
    return RestoreResult(restored=True, restart_recommended=True)


@router.post(
    "/restore/upload", response_model=RestoreResult, dependencies=[Depends(require_admin)]
)
def restore_from_upload(file: UploadFile = File(...)) -> RestoreResult:
    """Restore from an uploaded ``.tar.gz`` archive. Replaces ALL current data."""
    if not (file.filename or "").endswith(".tar.gz"):
        raise HTTPException(status_code=400, detail="Expected a .tar.gz file")
    with tempfile.NamedTemporaryFile(suffix=".tar.gz", delete=False) as tmp:
        shutil.copyfileobj(file.file, tmp)
        tmp_path = Path(tmp.name)
    try:
        _do_restore(tmp_path)
    finally:
        tmp_path.unlink(missing_ok=True)
    return RestoreResult(restored=True, restart_recommended=True)


@router.delete(
    "/households/{household_id}", status_code=204, dependencies=[Depends(require_admin)]
)
def delete_household(household_id: str, session: Session = Depends(get_session)) -> None:
    """Delete one household and its sync log + pending imports. Use to clean up stale or
    test households. (Images aren't household-scoped on disk, so any are left as harmless
    orphans; a full /reset clears the image dir.) Devices still on this household can no
    longer sync — their code stops resolving."""
    with _restore_lock:  # don't race an in-flight sync/restore
        household = session.get(Household, household_id)
        if household is None:
            raise HTTPException(status_code=404, detail="Household not found")
        session.query(SyncMessage).filter(SyncMessage.household_id == household_id).delete()
        session.query(ImportRecord).filter(ImportRecord.household_id == household_id).delete()
        session.delete(household)
        session.commit()


@router.post("/reset", status_code=204, dependencies=[Depends(require_admin)])
def reset_database(session: Session = Depends(get_session)) -> None:
    """Wipe everything — the append-only message log (recipes/shopping/plan/pantry
    history), jobs and households — plus stored images. For testing/starting fresh.
    Deleting households means any device with the old code can no longer push, so nothing
    repopulates the DB. Deliberately keeps the admin password (``ServerConfig``) and the
    backups directory. The triggering device also clears its local DB and returns to
    onboarding (its household is gone)."""
    with _restore_lock:  # don't race an in-flight sync/restore
        session.query(SyncMessage).delete()
        session.query(Job).delete()
        session.query(ImportRecord).delete()
        session.query(Household).delete()
        session.commit()
    # Empty the images dir contents but keep the directory (and never touch backups).
    images = get_settings().images_dir
    if images.exists():
        for child in images.iterdir():
            if child.is_dir():
                shutil.rmtree(child, ignore_errors=True)
            else:
                child.unlink(missing_ok=True)


# --------------------------------------------------------------------------- #
# Database viewer (web UI served at /admin/).
#
# A lightweight admin console: browse the SQLite tables, see the sync log as
# reconstructed entities, and manage households (rename, PIN, delete). All
# writes go through curated, password-gated endpoints (no free-form SQL). The
# HTML shell is public; every data endpoint is password-gated.
# --------------------------------------------------------------------------- #


def _serialize_value(table: str, column: str, value: object) -> object:
    """JSON-safe cell value, masking secret columns."""
    if value is not None and (table, column) in _REDACTED_COLUMNS:
        return "•••"
    if isinstance(value, datetime):
        return value.isoformat()
    if isinstance(value, enum.Enum):
        return value.value
    if isinstance(value, (str, int, float, bool)) or value is None:
        return value
    return str(value)


@router.get("", include_in_schema=False)
@router.get("/", include_in_schema=False)
@router.get("/db", include_in_schema=False)
def db_admin_page() -> FileResponse:
    """The admin web UI shell. Not gated — it holds no data; the page itself
    prompts for the admin password and sends it on every data request. Served at
    /admin, /admin/ and /admin/db (the latter kept for older bookmarks)."""
    page = _STATIC_DIR / "admin.html"
    if not page.is_file():
        raise HTTPException(status_code=404, detail="Admin UI not installed")
    return FileResponse(page, media_type="text/html")


@router.get(
    "/db/tables", response_model=DbTableListResponse, dependencies=[Depends(require_admin)]
)
def db_list_tables(session: Session = Depends(get_session)) -> DbTableListResponse:
    """List every known table (from the ORM metadata) with its columns and row count."""
    tables: list[DbTableInfo] = []
    for name, table in Base.metadata.tables.items():
        count = session.execute(select(func.count()).select_from(table)).scalar_one()
        cols = [
            DbColumn(name=c.name, type=str(c.type), primary_key=c.primary_key)
            for c in table.columns
        ]
        tables.append(DbTableInfo(name=name, row_count=count, columns=cols))
    tables.sort(key=lambda t: t.name)
    return DbTableListResponse(tables=tables)


@router.get(
    "/db/tables/{table}", response_model=DbTablePage, dependencies=[Depends(require_admin)]
)
def db_table_rows(
    table: str,
    limit: int = Query(default=50, ge=1, le=_MAX_PAGE_LIMIT),
    offset: int = Query(default=0, ge=0),
    order_by: str | None = Query(default=None),
    direction: str = Query(default="asc", pattern="^(asc|desc)$"),
    session: Session = Depends(get_session),
) -> DbTablePage:
    """A page of rows from one table. Table and order column are validated against
    the ORM metadata, so identifiers are never built from raw user strings."""
    table_obj = Base.metadata.tables.get(table)
    if table_obj is None:
        raise HTTPException(status_code=404, detail="Unknown table")

    column_names = [c.name for c in table_obj.columns]
    stmt = select(table_obj)
    if order_by is not None:
        if order_by not in column_names:
            raise HTTPException(status_code=400, detail="Unknown order column")
        col = table_obj.c[order_by]
        stmt = stmt.order_by(col.desc() if direction == "desc" else col.asc())
    stmt = stmt.limit(limit).offset(offset)

    total = session.execute(select(func.count()).select_from(table_obj)).scalar_one()
    result = session.execute(stmt)
    rows = [
        {col: _serialize_value(table, col, val) for col, val in zip(column_names, row)}
        for row in result.all()
    ]
    return DbTablePage(
        table=table, columns=column_names, rows=rows, total=total, limit=limit, offset=offset
    )


@router.get("/db/sync", response_model=DbSyncDatasetList, dependencies=[Depends(require_admin)])
def db_sync_datasets(
    household_id: str | None = Query(default=None),
    session: Session = Depends(get_session),
) -> DbSyncDatasetList:
    """The sync log grouped by dataset — message count and distinct row count each,
    so the UI can show the log as entities (recipes, shopping, plan, …) instead of a
    flat field-change stream. Optionally scoped to one household."""
    stmt = select(
        SyncMessage.dataset,
        func.count(),
        func.count(distinct(SyncMessage.row_id)),
    )
    if household_id:
        stmt = stmt.where(SyncMessage.household_id == household_id)
    rows = session.execute(stmt.group_by(SyncMessage.dataset)).all()
    datasets = [
        DbSyncDataset(name=name, message_count=msgs, row_count=rows_)
        for name, msgs, rows_ in rows
    ]
    datasets.sort(key=lambda d: d.name)
    return DbSyncDatasetList(datasets=datasets)


@router.get(
    "/db/sync/{dataset}", response_model=DbSyncRows, dependencies=[Depends(require_admin)]
)
def db_sync_rows(
    dataset: str,
    household_id: str | None = Query(default=None),
    session: Session = Depends(get_session),
) -> DbSyncRows:
    """Reconstruct one dataset's current state from the append-only log: per row_id,
    the latest value of each column (per-field last-write-wins), JSON-decoded. This
    turns the raw field-change messages into readable entity rows. Optionally scoped
    to one household."""
    stmt = (
        select(
            SyncMessage.row_id,
            SyncMessage.col_key,
            SyncMessage.value,
            SyncMessage.timestamp,
            SyncMessage.household_id,
        )
        .where(SyncMessage.dataset == dataset)
        .order_by(SyncMessage.timestamp)  # ascending → later writes overwrite earlier
    )
    if household_id:
        stmt = stmt.where(SyncMessage.household_id == household_id)
    msgs = session.execute(stmt).all()

    rows: dict[str, dict] = {}
    seen_fields: dict[str, set] = {}   # row_id -> columns already written (first vs later)
    edited_rows: set[str] = set()      # rows that received a later write to some field
    deleted_latest: dict[str, bool] = {}  # row_id -> latest _deleted value
    msg_count: dict[str, int] = {}
    by_column: dict[str, int] = {}
    adds = edits = deletes = 0

    for row_id, col, value, ts, household in msgs:  # ascending → later writes win
        by_column[col] = by_column.get(col, 0) + 1
        msg_count[row_id] = msg_count.get(row_id, 0) + 1
        cols = seen_fields.setdefault(row_id, set())
        first_write = col not in cols
        cols.add(col)
        # Classify into exactly one bucket so adds + edits + deletes == total.
        if col == _DELETED_COL and _is_truthy(value):
            deletes += 1
        elif first_write:
            adds += 1
        else:
            edits += 1
            edited_rows.add(row_id)
        if col == _DELETED_COL:
            deleted_latest[row_id] = _is_truthy(value)

        row = rows.get(row_id)
        if row is None:
            row = {"row_id": row_id, "household_id": household, "updated": ts, "fields": {}}
            rows[row_id] = row
        try:
            row["fields"][col] = json.loads(value)
        except (ValueError, TypeError):
            row["fields"][col] = value
        row["updated"] = ts
        row["household_id"] = household

    # Recipes carry a sourcePhotoId (the extraction Job whose original photo made
    # them). Resolve those to the job's original-scan filename and attach it as a
    # synthetic "originalScan" field so the UI can show the original next to the crop.
    if dataset == "recipes":
        photo_ids = {
            r["fields"].get("sourcePhotoId")
            for r in rows.values()
            if isinstance(r["fields"].get("sourcePhotoId"), str) and r["fields"].get("sourcePhotoId")
        }
        if photo_ids:
            job_image = {
                jid: Path(path).name
                for jid, path in session.execute(
                    select(Job.id, Job.image_path).where(Job.id.in_(photo_ids))
                ).all()
                if path
            }
            for r in rows.values():
                name = job_image.get(r["fields"].get("sourcePhotoId"))
                if name:
                    r["fields"]["originalScan"] = name

    out_rows: list[DbSyncRow] = []
    for row_id, r in rows.items():
        deleted = deleted_latest.get(row_id, False)
        edited = row_id in edited_rows
        status = "deleted" if deleted else ("edited" if edited else "created")
        out_rows.append(
            DbSyncRow(
                row_id=row_id,
                household_id=r["household_id"],
                updated=r["updated"],
                status=status,
                edited=edited,
                message_count=msg_count[row_id],
                fields=r["fields"],
            )
        )
    out_rows.sort(key=lambda r: r.updated, reverse=True)

    rows_deleted = sum(1 for v in deleted_latest.values() if v)
    by_column_sorted = sorted(
        (DbSyncColumnCount(column=c, count=n) for c, n in by_column.items()),
        key=lambda c: c.count,
        reverse=True,
    )
    stats = DbSyncStats(
        total_messages=len(msgs),
        adds=adds,
        edits=edits,
        deletes=deletes,
        rows_total=len(rows),
        rows_live=len(rows) - rows_deleted,
        rows_deleted=rows_deleted,
        by_column=by_column_sorted,
    )
    return DbSyncRows(dataset=dataset, stats=stats, rows=out_rows)


def _household_summary(h: Household) -> HouseholdSummary:
    return HouseholdSummary(
        id=h.id,
        name=h.name,
        settings=json.loads(h.settings_json),
        protected=h.pin_hash is not None,
        created_at=h.created_at,
    )


@router.patch(
    "/households/{household_id}",
    response_model=HouseholdSummary,
    dependencies=[Depends(require_admin)],
)
def admin_patch_household(
    household_id: str, body: AdminHouseholdPatch, session: Session = Depends(get_session)
) -> HouseholdSummary:
    """Admin-edit a household: rename it and/or set/change/clear its PIN. The plaintext
    PIN is stored only as a salted hash and can't be read back, so this only writes a new
    one. ``pin``: null leaves it, "" clears protection, any other value sets it."""
    household = session.get(Household, household_id)
    if household is None:
        raise HTTPException(status_code=404, detail="Household not found")
    if body.name is not None:
        household.name = body.name.strip()
    if body.pin is not None:
        if body.pin == "":
            household.pin_hash = household.pin_salt = None
        else:
            household.pin_salt = make_salt()
            household.pin_hash = hash_secret(body.pin, household.pin_salt)
    session.commit()
    return _household_summary(household)
