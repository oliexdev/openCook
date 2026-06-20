#!/usr/bin/env python3
"""Restore a server backup created by backup.py. STOP THE SERVER FIRST.

    PYTHONPATH=server server/.venv/bin/python server/scripts/restore.py <archive.tar.gz>

Replaces the live DB + images with the archive's contents (a safety snapshot of the
current state is taken first). On a fresh OS: set up repo+venv, set OPENCOOK_DATA_DIR,
run this, then start the server — phones re-sync the rest.
"""

import sys
from pathlib import Path

from app.backup import restore_backup
from app.config import get_settings


def main() -> None:
    if len(sys.argv) != 2:
        print("Usage: restore.py <archive.tar.gz>", file=sys.stderr)
        raise SystemExit(2)
    archive = Path(sys.argv[1])
    if not archive.is_file():
        print(f"No such archive: {archive}", file=sys.stderr)
        raise SystemExit(1)

    settings = get_settings()
    restore_backup(archive, db_path=settings.db_path, images_dir=settings.images_dir)
    print(f"Restored from {archive}. Start the server now.")


if __name__ == "__main__":
    main()
