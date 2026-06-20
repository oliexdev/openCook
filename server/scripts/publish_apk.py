#!/usr/bin/env python3
"""Publish a signed release APK so the app can self-update against this server.

    PYTHONPATH=server server/.venv/bin/python server/scripts/publish_apk.py \
        path/to/app-release.apk --version-code 2 --version-name 1.1 [--notes "..."]

Copies the APK into OPENCOOK_DATA_DIR/apks and writes latest.json next to it. The
app's `GET /app/latest` then advertises this version; devices on an older
versionCode offer "Update verfügbar". Re-run per release with a higher
--version-code (must match what was built into app/build.gradle.kts).
"""

import argparse
import json
import shutil
from pathlib import Path

from app.config import get_settings


def main() -> None:
    parser = argparse.ArgumentParser(description="Publish a release APK for in-app update.")
    parser.add_argument("apk", type=Path, help="Path to the signed release .apk")
    parser.add_argument("--version-code", type=int, required=True, help="Must match the built versionCode")
    parser.add_argument("--version-name", required=True, help="Human version, e.g. 1.1")
    parser.add_argument("--notes", default=None, help="Optional release notes shown in the app")
    args = parser.parse_args()

    if not args.apk.is_file() or args.apk.suffix != ".apk":
        parser.error(f"Not an .apk file: {args.apk}")

    settings = get_settings()
    apks_dir = settings.apks_dir
    apks_dir.mkdir(parents=True, exist_ok=True)

    filename = f"opencook-{args.version_name}-{args.version_code}.apk"
    dest = apks_dir / filename
    shutil.copyfile(args.apk, dest)

    meta = {
        "versionCode": args.version_code,
        "versionName": args.version_name,
        "file": filename,
        "notes": args.notes,
    }
    (apks_dir / "latest.json").write_text(json.dumps(meta, indent=2))
    print(f"Published {dest} (versionCode={args.version_code}, versionName={args.version_name})")


if __name__ == "__main__":
    main()
