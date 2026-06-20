"""Full server backup & restore — pure stdlib (sqlite3 + tarfile + shutil).

The complete server state is the SQLite DB (households + append-only sync log +
jobs) plus the images directory (original uploads + crops). A backup is a single
portable ``.tar.gz`` containing a consistent ``opencook.db`` snapshot + ``images/``,
so a restore on a fresh OS brings everything back (phones then re-sync from it).

The DB runs in WAL mode, so we use SQLite's online-backup API for a transactionally
consistent single-file snapshot — no need to stop the server or copy -wal/-shm.
"""

import logging
import shutil
import sqlite3
import tarfile
import tempfile
from datetime import datetime
from pathlib import Path

logger = logging.getLogger(__name__)

_PREFIX = "opencook-backup-"


def _snapshot_db(db_path: Path, dest: Path) -> None:
    """Write a consistent copy of [db_path] to [dest] via SQLite's online backup."""
    src = sqlite3.connect(db_path)
    try:
        dst = sqlite3.connect(dest)
        try:
            src.backup(dst)
        finally:
            dst.close()
    finally:
        src.close()


def _assert_integrity(db_path: Path) -> None:
    conn = sqlite3.connect(db_path)
    try:
        result = conn.execute("PRAGMA integrity_check").fetchone()
        if not result or result[0] != "ok":
            raise ValueError(f"DB integrity check failed: {result}")
    finally:
        conn.close()


def _rotate(out_dir: Path, keep: int) -> None:
    if keep <= 0:
        return
    archives = sorted(out_dir.glob(f"{_PREFIX}*.tar.gz"))
    for old in archives[:-keep]:
        old.unlink(missing_ok=True)


def create_backup(db_path: Path, images_dir: Path, out_dir: Path, keep: int = 14) -> Path:
    """Create a timestamped ``.tar.gz`` of the DB snapshot + images in [out_dir],
    prune to the newest [keep] archives, and return the new archive's path."""
    out_dir.mkdir(parents=True, exist_ok=True)
    # Microseconds keep the name unique even for back-to-back backups.
    stamp = datetime.now().strftime("%Y%m%d-%H%M%S-%f")
    archive = out_dir / f"{_PREFIX}{stamp}.tar.gz"

    with tempfile.TemporaryDirectory() as tmp:
        snapshot = Path(tmp) / "opencook.db"
        _snapshot_db(db_path, snapshot)
        with tarfile.open(archive, "w:gz") as tar:
            tar.add(snapshot, arcname="opencook.db")
            if images_dir.exists():
                tar.add(images_dir, arcname="images")

    _rotate(out_dir, keep)
    logger.info("Backup written: %s", archive)
    return archive


def restore_backup(archive: Path, db_path: Path, images_dir: Path) -> None:
    """Restore [archive] over the live data dir. Stop the server first — SQLite holds
    the DB file open. A safety snapshot of the current state is taken before replacing."""
    with tempfile.TemporaryDirectory() as tmp:
        tmp_dir = Path(tmp)
        with tarfile.open(archive, "r:gz") as tar:
            tar.extractall(tmp_dir, filter="data")  # 'data' filter blocks path traversal (py3.12+)

        extracted_db = tmp_dir / "opencook.db"
        if not extracted_db.exists():
            raise ValueError("Archive does not contain opencook.db — not an openCook backup?")
        _assert_integrity(extracted_db)

        # Safety: snapshot the current state before we overwrite it.
        if db_path.exists():
            create_backup(db_path, images_dir, db_path.parent / "backups" / "pre-restore", keep=5)

        # Replace the DB (drop stale WAL/SHM sidecars so they can't shadow the restore).
        db_path.parent.mkdir(parents=True, exist_ok=True)
        for suffix in ("", "-wal", "-shm"):
            Path(f"{db_path}{suffix}").unlink(missing_ok=True)
        shutil.copy2(extracted_db, db_path)

        # Replace images wholesale.
        if images_dir.exists():
            shutil.rmtree(images_dir)
        extracted_images = tmp_dir / "images"
        if extracted_images.exists():
            shutil.copytree(extracted_images, images_dir)
        else:
            images_dir.mkdir(parents=True, exist_ok=True)

    logger.info("Restore complete from %s", archive)
