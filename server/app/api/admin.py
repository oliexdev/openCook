"""Password-gated server administration: backup & restore over HTTP.

Trust model is LAN/VPN (no per-user auth). A single server-wide admin password —
set at household creation or via ``POST /admin/password`` — gates every endpoint here
except ``GET /admin/status``. The password is sent per request in the ``X-Admin-Password``
header and verified constant-time against the stored salted hash (``ServerConfig``).

Backups reuse :mod:`app.backup`; the same archives the CLI/cron path produces.
"""

import shutil
import tempfile
import threading
from datetime import datetime, timezone
from pathlib import Path

from fastapi import APIRouter, Depends, File, Header, HTTPException, UploadFile
from fastapi.responses import FileResponse
from sqlalchemy.orm import Session

from app.backup import _PREFIX, create_backup, restore_backup
from app.config import get_settings
from app.db import engine, get_session
from app.models import Household, ImportRecord, Job, ServerConfig, SyncMessage
from app.schemas import (
    AdminPasswordChangeRequest,
    AdminStatusResponse,
    BackupInfo,
    BackupListResponse,
    RestoreRequest,
    RestoreResult,
)
from app.security import hash_secret, make_salt, verify_secret

router = APIRouter(prefix="/admin", tags=["admin"])

# Serialize restores so a concurrent sync write can't race the DB-file swap.
_restore_lock = threading.Lock()


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
