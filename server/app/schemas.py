"""Pydantic response models for the HTTP API."""

from datetime import datetime

from pydantic import BaseModel, ConfigDict

from app.models import JobStatus


class SyncMessageDto(BaseModel):
    timestamp: str
    dataset: str
    row_id: str
    column: str
    value: str


class SyncRequest(BaseModel):
    # The client's Merkle trie (nested {hash, children}) and any messages to push.
    merkle: dict = {}
    messages: list[SyncMessageDto] = []


class SyncResponse(BaseModel):
    # Server messages the client is missing (from the divergence point on).
    messages: list[SyncMessageDto]
    merkle: dict
    # Household-wide state pushed alongside every sync so all devices converge on
    # the name and settings (e.g. household_size) without a separate poll.
    household_name: str = ""
    household_settings: dict = {}


class HouseholdSettings(BaseModel):
    """Extensible household-wide settings. ``extra="allow"`` keeps unknown/future
    keys so a newer client can add options without a server change."""

    model_config = ConfigDict(extra="allow")
    household_size: int = 2


class HouseholdSummary(BaseModel):
    """One entry in the join picker. Never exposes invite_code or the PIN."""

    id: str
    name: str
    settings: dict
    protected: bool
    created_at: datetime


class HouseholdResponse(BaseModel):
    """Returned to the device that creates or joins — carries the invite_code it
    needs as the sync credential."""

    household_id: str
    invite_code: str
    name: str
    settings: dict


class HouseholdCreateRequest(BaseModel):
    name: str
    settings: dict = {}
    pin: str | None = None
    # Optional: sets the server-wide admin password (gates backup/restore) — but only
    # the first time one is provided; later creates carrying a password are ignored
    # for it (the household is still created). Change it via POST /admin/password.
    admin_password: str | None = None


class HouseholdJoinRequest(BaseModel):
    pin: str | None = None


class HouseholdPatchRequest(BaseModel):
    name: str | None = None
    settings: dict | None = None  # partial; merged into existing settings
    pin: str | None = None


class AdminStatusResponse(BaseModel):
    """Whether a server admin password has been set yet (so the app shows
    'set password' vs 'enter password')."""

    configured: bool


class AdminPasswordChangeRequest(BaseModel):
    # Required once a password exists; ignored on first set.
    current_password: str | None = None
    new_password: str


class BackupInfo(BaseModel):
    id: str  # archive filename, e.g. opencook-backup-20260524-101500-123456.tar.gz
    created_at: datetime
    size_bytes: int


class BackupListResponse(BaseModel):
    backups: list[BackupInfo]


class RestoreRequest(BaseModel):
    backup_id: str


class RestoreResult(BaseModel):
    restored: bool
    # SQLite restore swaps the DB file under the running server; a manual uvicorn
    # restart is the safest way to be fully consistent.
    restart_recommended: bool = True


class ImportCreatedResponse(BaseModel):
    import_id: str


class PendingImport(BaseModel):
    id: str
    # The raw schema.org/Recipe object; the app feeds it to its tolerant parser.
    recipe: dict
    # Bare image filename in images_dir, or null. App builds GET /images/{name}.
    image_name: str | None = None
    # Original page URL; the app derives a source-site cookbook from it. May be null.
    source_url: str | None = None


class PendingImportsResponse(BaseModel):
    imports: list[PendingImport]


class JobCreatedResponse(BaseModel):
    job_id: str
    status: JobStatus


class JobStatusResponse(BaseModel):
    job_id: str
    status: JobStatus
    # Coarse stage key while processing (e.g. "reading_text"); null otherwise.
    stage: str | None = None
    # Populated once status == DONE (parsed schema.org/Recipe JSON).
    result: list[dict] | None = None
    error: str | None = None
    created_at: datetime
    updated_at: datetime
