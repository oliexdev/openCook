#!/usr/bin/env python3
"""Create a full server backup (DB snapshot + images) as a portable .tar.gz.

    PYTHONPATH=server server/.venv/bin/python server/scripts/backup.py

Writes to OPENCOOK_BACKUP_DIR (default <data_dir>/backups) and keeps the newest
OPENCOOK_BACKUP_KEEP archives. Safe to run while the server is running.
"""

from app.backup import create_backup
from app.config import get_settings


def main() -> None:
    settings = get_settings()
    archive = create_backup(
        db_path=settings.db_path,
        images_dir=settings.images_dir,
        out_dir=settings.backups_dir,
        keep=settings.backup_keep,
    )
    print(f"Backup written: {archive}")


if __name__ == "__main__":
    main()
