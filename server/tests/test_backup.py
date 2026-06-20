import sqlite3
import tarfile
from pathlib import Path

from app.backup import create_backup, restore_backup


def _make_db(path: Path) -> None:
    conn = sqlite3.connect(path)
    conn.execute("PRAGMA journal_mode=WAL")  # mirror the server's WAL mode
    conn.execute("CREATE TABLE t (id INTEGER PRIMARY KEY, val TEXT)")
    conn.execute("INSERT INTO t (val) VALUES ('hello')")
    conn.commit()
    conn.close()


def test_backup_restore_roundtrip(tmp_path: Path) -> None:
    data = tmp_path / "data"
    images = data / "images"
    images.mkdir(parents=True)
    db = data / "opencook.db"
    _make_db(db)
    (images / "orig.jpg").write_bytes(b"JPEGDATA")  # an original upload

    archive = create_backup(db, images, tmp_path / "backups", keep=14)
    assert archive.exists()
    with tarfile.open(archive) as tar:
        names = tar.getnames()
    assert "opencook.db" in names
    assert any(n.endswith("images/orig.jpg") for n in names)

    # Wipe the live data (as if a fresh OS), then restore.
    db.unlink()
    for sidecar in ("-wal", "-shm"):
        Path(f"{db}{sidecar}").unlink(missing_ok=True)
    (images / "orig.jpg").unlink()

    restore_backup(archive, db, images)

    assert (images / "orig.jpg").read_bytes() == b"JPEGDATA"
    conn = sqlite3.connect(db)
    try:
        assert conn.execute("SELECT val FROM t").fetchone()[0] == "hello"
        assert conn.execute("PRAGMA integrity_check").fetchone()[0] == "ok"
    finally:
        conn.close()


def test_rotation_keeps_newest(tmp_path: Path) -> None:
    data = tmp_path / "data"
    (data / "images").mkdir(parents=True)
    db = data / "opencook.db"
    _make_db(db)
    out = tmp_path / "backups"

    archives = [create_backup(db, data / "images", out, keep=2) for _ in range(3)]
    remaining = sorted(out.glob("opencook-backup-*.tar.gz"))

    assert len(remaining) == 2
    assert archives[-1] in remaining and archives[-2] in remaining
    assert archives[0] not in remaining  # oldest pruned
