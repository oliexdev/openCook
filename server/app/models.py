"""SQLAlchemy ORM models.

Phase 0 only defines the async-job table. Recipes, households and the sync
message-log are added in Phase 1/2.
"""

import enum
import uuid
from datetime import datetime, timezone

from sqlalchemy import DateTime, Enum, String, Text
from sqlalchemy.orm import Mapped, mapped_column

from app.db import Base


def _utcnow() -> datetime:
    return datetime.now(timezone.utc)


def _new_id() -> str:
    return str(uuid.uuid4())


class JobStatus(str, enum.Enum):
    PENDING = "pending"
    PROCESSING = "processing"
    DONE = "done"
    ERROR = "error"


class Household(Base):
    """A sync scope. The app discovers the server on the LAN and lists households
    to join (no per-user auth; LAN/VPN trust). ``invite_code`` is kept as the sync
    credential (header ``X-Household-Code``) but is no longer typed by the user.
    A household may optionally be PIN-protected."""

    __tablename__ = "households"

    id: Mapped[str] = mapped_column(String, primary_key=True, default=_new_id)
    invite_code: Mapped[str] = mapped_column(String, unique=True, index=True, nullable=False)
    # Human-readable name shown in the join picker.
    name: Mapped[str] = mapped_column(String, nullable=False, default="")
    # Extensible household-wide settings as a JSON dict (e.g. {"household_size": 2}).
    # New options become new keys — no schema change needed (forward-compatible).
    settings_json: Mapped[str] = mapped_column(Text, nullable=False, default="{}")
    # Optional PIN: salted SHA-256, never stored in clear. Both null = open household.
    pin_hash: Mapped[str | None] = mapped_column(String, nullable=True)
    pin_salt: Mapped[str | None] = mapped_column(String, nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=_utcnow, nullable=False)


class ServerConfig(Base):
    """Singleton server-wide config (``id`` is always ``"singleton"``). Currently holds
    the admin password (salted SHA-256) that gates the backup/restore admin API. Backup
    and restore are server-wide (the whole DB + images), so the password lives here rather
    than on any one household row."""

    __tablename__ = "server_config"

    id: Mapped[str] = mapped_column(String, primary_key=True, default="singleton")
    admin_pw_hash: Mapped[str | None] = mapped_column(String, nullable=True)
    admin_pw_salt: Mapped[str | None] = mapped_column(String, nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=_utcnow, nullable=False)
    updated_at: Mapped[datetime] = mapped_column(
        DateTime, default=_utcnow, onupdate=_utcnow, nullable=False
    )


class SyncMessage(Base):
    """One append-only sync message, scoped to a household. Mirror of the app's
    Room `messages` table. The packed-HLC [timestamp] is globally unique (it embeds
    the node), so it is the primary key and makes pushes idempotent.

    The DB column is `col_key` because `column` is reserved."""

    __tablename__ = "messages"

    timestamp: Mapped[str] = mapped_column(String, primary_key=True)
    household_id: Mapped[str] = mapped_column(String, index=True, nullable=False)
    dataset: Mapped[str] = mapped_column(String, nullable=False)
    row_id: Mapped[str] = mapped_column(String, nullable=False)
    col_key: Mapped[str] = mapped_column(String, nullable=False)
    value: Mapped[str] = mapped_column(Text, nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=_utcnow, nullable=False)


class ImportStatus(str, enum.Enum):
    PENDING = "pending"
    CLAIMED = "claimed"
    CONSUMED = "consumed"


class ImportRecord(Base):
    """A recipe pushed by the browser extension, waiting for an app to materialize it.

    Unlike a photo :class:`Job`, the recipe is already structured (schema.org/Recipe
    JSON-LD scraped from a web page), so no extraction worker runs. The first app to
    sync claims it (atomic, exactly-once), parses + saves it through the normal recipe
    pipeline (which appends sync messages → propagates to all devices), then consumes
    it. Scoped to a household via the same X-Household-Code as sync."""

    __tablename__ = "imports"

    id: Mapped[str] = mapped_column(String, primary_key=True, default=_new_id)
    household_id: Mapped[str] = mapped_column(String, index=True, nullable=False)
    status: Mapped[ImportStatus] = mapped_column(
        Enum(ImportStatus), default=ImportStatus.PENDING, nullable=False
    )
    # The raw schema.org/Recipe object as received (the app's tolerant parser reads it).
    recipe_json: Mapped[str] = mapped_column(Text, nullable=False)
    # Bare filename of the saved image in images_dir (served via GET /images/{name}); the
    # bytes sync out-of-band exactly like AI photo crops. Null if no image was sent.
    image_name: Mapped[str | None] = mapped_column(String, nullable=True)
    # Original page URL — kept server-side only (debug/future dedup); never synced/shown.
    source_url: Mapped[str | None] = mapped_column(String, nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=_utcnow, nullable=False)
    # When an app claimed it; a stale claim (app crashed mid-drain) reverts to pending.
    claimed_at: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)


class Job(Base):
    """An asynchronous recipe-extraction job."""

    __tablename__ = "jobs"

    id: Mapped[str] = mapped_column(String, primary_key=True, default=_new_id)
    status: Mapped[JobStatus] = mapped_column(
        Enum(JobStatus), default=JobStatus.PENDING, nullable=False
    )
    image_path: Mapped[str | None] = mapped_column(String, nullable=True)
    # Coarse processing stage for UI feedback (e.g. "reading_text",
    # "detecting_photos"); null when queued or finished.
    stage: Mapped[str | None] = mapped_column(String, nullable=True)
    # schema.org/Recipe JSON (list of recipes) once processing completes.
    result_json: Mapped[str | None] = mapped_column(Text, nullable=True)
    error: Mapped[str | None] = mapped_column(Text, nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=_utcnow, nullable=False)
    updated_at: Mapped[datetime] = mapped_column(
        DateTime, default=_utcnow, onupdate=_utcnow, nullable=False
    )
