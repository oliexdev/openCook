# HTTP API reference

All endpoints are served by the FastAPI server (`server/app/api/`). Interactive docs are at
`/docs` (Swagger UI) on a running server.

**Trust model:** most endpoints are unauthenticated by design (trusted LAN/VPN). Sync, import and
image endpoints are scoped to a household via the `X-Household-Code` header (the invite code). Admin
endpoints require the `X-Admin-Password` header. **Do not expose the server to the internet.**

## Health

| Method | Path | Purpose |
|---|---|---|
| GET | `/health` | Liveness — `{"status": "ok"}` |

## Jobs — recipe extraction (`api/jobs.py`)

| Method | Path | Purpose |
|---|---|---|
| POST | `/jobs` | Create an extraction job from an uploaded image (multipart). → `201` `{job_id, status}`. Image stored under `data/images/`; `Job` created `pending`. |
| GET | `/jobs/{job_id}` | Job status/result: `{job_id, status, stage, result, error, created_at, updated_at}`. `result` is the schema.org/Recipe list when `status=done`. |

## Sync (`api/sync.py`)

| Method | Path | Headers | Purpose |
|---|---|---|---|
| POST | `/sync` | `X-Household-Code` | Delta sync. Body `{merkle, messages[]}`; response `{messages, merkle, household_name, household_settings}`. Pushed messages are stored idempotently (timestamp = PK); the Merkle diff returns only messages from the divergence point on. See [Sync engine](sync.md). |

## Imports — browser-extension inbox (`api/imports.py`)

| Method | Path | Headers | Purpose |
|---|---|---|---|
| POST | `/imports` | `X-Household-Code` | Push a schema.org/Recipe (form fields `recipe` JSON, optional `image`, `source_url`). → `201` `{import_id}`. Deduped by `source_url` while pending. |
| GET | `/imports/pending` | `X-Household-Code` | List pending imports (reclaims stale claims > 5 min). |
| POST | `/imports/{import_id}/claim` | `X-Household-Code` | Atomically claim one. `204`, or `409` if already claimed. |
| POST | `/imports/{import_id}/consume` | `X-Household-Code` | Mark materialised. `204`. |

## Households (`api/households.py`)

| Method | Path | Headers | Purpose |
|---|---|---|---|
| GET | `/households` | — | List households for the join picker (`id, name, settings, protected, created_at`). Invite codes are never exposed here. |
| POST | `/households` | — | Create a household (`name, settings, pin, admin_password`). → `201` `{household_id, invite_code, name, settings}`. Sets the server admin password only on first create. |
| POST | `/households/{household_id}/join` | — | Join (`pin` if protected). → `{… invite_code}`. |
| PATCH | `/households/{household_id}` | `X-Household-Code` | Partial update of `name`/`settings`/`pin`. |

## Images (`api/images.py`)

| Method | Path | Headers | Purpose |
|---|---|---|---|
| POST | `/images` | `X-Household-Code` | Upload raw image bytes (≤ 25 MB). → `{"name": "<sha256>.jpg"}` — content-addressed, so identical images dedupe across devices. |
| GET | `/images/{name}` | — | Serve a stored image (path-traversal guarded). |

## Admin (`api/admin.py`)

All require `X-Admin-Password` except `/admin/status` and the first `/admin/password` set.

| Method | Path | Purpose |
|---|---|---|
| GET | `/admin/status` | Whether an admin password is configured. |
| POST | `/admin/password` | Set/change the admin password (current required to change). |
| POST | `/admin/verify` | Validate the admin password (`204`). |
| GET | `/admin/backups` | List backups (newest first). |
| POST | `/admin/backups` | Create a backup (DB + images `.tar.gz`, rotates to `backup_keep`). |
| GET | `/admin/backups/{backup_id}` | Download a backup archive. |
| POST | `/admin/restore` | Restore from a server-side backup (safety snapshot first). |
| POST | `/admin/restore/upload` | Restore from an uploaded `.tar.gz`. |
| DELETE | `/admin/households/{household_id}` | Delete a household + its sync log & imports. |
| POST | `/admin/reset` | Wipe all data (keeps admin password + backups dir). |
