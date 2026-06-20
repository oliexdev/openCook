"""Serve self-hosted app updates.

A signed release APK plus a ``latest.json`` metadata file are published into
``settings.apks_dir`` (see ``server/scripts/publish_apk.py``). The app polls
``GET /app/latest``, compares ``versionCode`` against its own ``BuildConfig`` and,
if newer, downloads the APK from ``GET /app/download/{filename}`` and hands it to the
system installer. No auth — this is public version info on a LAN/VPN-only server,
same trust model as ``GET /images/{name}``.
"""

import json

from fastapi import APIRouter, HTTPException
from fastapi.responses import FileResponse

from app.config import get_settings

router = APIRouter(prefix="/app", tags=["updates"])

# Metadata sidecar written next to the published APK by publish_apk.py.
LATEST_JSON = "latest.json"


@router.get("/latest")
def latest() -> dict:
    """Return the published release's metadata, or 404 if nothing is published yet.

    Shape: ``{"versionCode": int, "versionName": str, "url": str, "notes": str|None}``
    where ``url`` is the relative ``/app/download/<file>`` path.
    """
    settings = get_settings()
    meta_path = settings.apks_dir / LATEST_JSON
    if not meta_path.is_file():
        raise HTTPException(status_code=404, detail="No release published")
    try:
        meta = json.loads(meta_path.read_text())
    except (ValueError, OSError) as exc:
        raise HTTPException(status_code=500, detail="Malformed release metadata") from exc
    return {
        "versionCode": int(meta["versionCode"]),
        "versionName": str(meta["versionName"]),
        "url": f"/app/download/{meta['file']}",
        "notes": meta.get("notes"),
    }


@router.get("/download/{filename}")
def download(filename: str) -> FileResponse:
    """Stream a published APK by bare filename (no path components → no traversal)."""
    if "/" in filename or "\\" in filename or filename in ("", ".", ".."):
        raise HTTPException(status_code=400, detail="Invalid APK name")

    settings = get_settings()
    path = (settings.apks_dir / filename).resolve()
    if settings.apks_dir.resolve() not in path.parents or not path.is_file():
        raise HTTPException(status_code=404, detail="APK not found")
    return FileResponse(
        path,
        media_type="application/vnd.android.package-archive",
        filename=filename,
    )
