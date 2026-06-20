"""Serve — and accept — dish images.

The extractor writes crops into ``settings.images_dir`` and references them by
bare filename in the schema.org ``image`` field; the app fetches them via GET.
Clients also upload locally-sourced images (e.g. bundle-imported dish photos) via
POST so they reach the other household devices instead of staying device-local.
"""

import hashlib

from fastapi import APIRouter, Depends, Header, HTTPException, Request
from fastapi.responses import FileResponse
from sqlalchemy.orm import Session

from app.api._deps import resolve_household
from app.config import get_settings
from app.db import get_session

router = APIRouter(prefix="/images", tags=["images"])

# Generous cap — full-resolution cookbook crops can be a few MB.
_MAX_IMAGE_BYTES = 25 * 1024 * 1024


@router.post("")
async def upload_image(
    request: Request,
    x_household_code: str | None = Header(default=None, alias="X-Household-Code"),
    session: Session = Depends(get_session),
) -> dict[str, str]:
    """Store a client-uploaded image so it syncs to other devices via GET /images/{name}.

    Auth is the shared household code (same trust model as /sync). The filename is
    content-addressed (sha256) so re-uploading the same bytes is idempotent and
    duplicate images across recipes/devices collapse to one file. Returns the bare
    filename the caller then references as the recipe's ``imageRef``.
    """
    resolve_household(x_household_code, session)
    body = await request.body()
    if not body:
        raise HTTPException(status_code=400, detail="Empty image body")
    if len(body) > _MAX_IMAGE_BYTES:
        raise HTTPException(status_code=413, detail="Image too large")

    settings = get_settings()
    name = f"{hashlib.sha256(body).hexdigest()}.jpg"
    path = settings.images_dir / name
    if not path.exists():
        path.write_bytes(body)
    return {"name": name}


@router.get("/{name}")
def get_image(name: str) -> FileResponse:
    """Return a stored image by filename.

    ``name`` must be a bare filename (no path components) to prevent traversal
    outside the images directory.
    """
    if "/" in name or "\\" in name or name in ("", ".", ".."):
        raise HTTPException(status_code=400, detail="Invalid image name")

    settings = get_settings()
    path = (settings.images_dir / name).resolve()
    if settings.images_dir.resolve() not in path.parents or not path.is_file():
        raise HTTPException(status_code=404, detail="Image not found")
    return FileResponse(path, media_type="image/jpeg")
