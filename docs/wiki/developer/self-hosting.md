# Self-hosting

The server is optional (only needed for photo scanning + sync) and designed for a trusted home
network. The recommended deployment is Docker; a plain Python run also works.

## Prerequisites

- A Linux machine on the same LAN/Wi-Fi as the phones.
- **[Ollama](https://ollama.com) running on the host** with the vision model pulled — it needs the
  GPU, so it stays on the host even when the server runs in a container:
  ```bash
  ollama pull qwen2.5vl:7b
  ```

## Docker (recommended)

```bash
cd server
docker compose up -d --build
docker compose ps                    # STATUS should become "healthy"
curl http://localhost:8000/health    # {"status":"ok"}
docker compose logs -f               # "Job worker started", mDNS advertise line
```

The container runs **only the server** (FastAPI + SQLite + the job worker + mDNS); it calls the
host's Ollama over HTTP. `network_mode: host` is used so mDNS reaches the LAN and the container can
reach Ollama at `localhost:11434`. `restart: unless-stopped` brings it back after a reboot.

Hardening (already set in `docker-compose.yml`): non-root user (uid 10001), `read_only` root
filesystem with a `/tmp` tmpfs, `cap_drop: ALL`, `no-new-privileges`. Only `/data` is writable. No
Docker socket, no `--privileged`.

### Connecting the app

On the same LAN the app **auto-discovers** the server via mDNS and lists it as "openCook". Give it a
friendlier name with `OPENCOOK_SERVER_NAME=Kueche docker compose up -d`. No discovery (VPN/emulator)?
Enter `http://<server-lan-ip>:8000` by hand in onboarding.

## Plain Python

```bash
cd server
python -m venv .venv && source .venv/bin/activate
pip install -e ".[dev]"
uvicorn app.main:app            # add --reload for development
```

## Configuration

Environment variables, all prefixed `OPENCOOK_` (a `server/.env` file is read too):

| Variable | Default | Purpose |
|---|---|---|
| `OPENCOOK_DATA_DIR` | `./data` | DB + images + backups root |
| `OPENCOOK_HOST` | `0.0.0.0` | HTTP bind address |
| `OPENCOOK_PORT` | `8000` | HTTP port |
| `OPENCOOK_SERVER_NAME` | hostname | Name advertised over mDNS |
| `OPENCOOK_OLLAMA_BASE_URL` | `http://localhost:11434` | Ollama endpoint |
| `OPENCOOK_OLLAMA_MODEL` | `qwen2.5vl:7b` | Vision model |
| `OPENCOOK_WORKER_POLL_INTERVAL` | `2.0` | Job worker poll interval (s) |
| `OPENCOOK_BACKUP_DIR` | `<data_dir>/backups` | Backup archive location |
| `OPENCOOK_BACKUP_KEEP` | `14` | Backups to retain (rotation) |

## Data

Everything lives under `/data` (the Docker named volume `opencook-data`):

```
/data/opencook.db (+ -wal/-shm)   # SQLite
/data/images/                      # uploaded photos + AI crops
/data/backups/                     # *.tar.gz backups (default)
/data/apks/                        # published release APKs + latest.json (in-app updates)
```

It survives restarts and `docker compose up --build`, but **not** deleting the volume or losing the
disk — so keep durable backups off the data volume.

## Backup & restore

A backup is one portable `.tar.gz` with a consistent `opencook.db` snapshot (SQLite online-backup —
safe while running) plus the whole `images/` tree. Phones re-sync from the server after a restore,
so they need no backup of their own.

**From the app:** the admin screen can create, download and restore backups (admin password
required).

**From the CLI:**
```bash
# Create (safe while the server runs)
PYTHONPATH=server server/.venv/bin/python server/scripts/backup.py

# Restore — STOP THE SERVER FIRST (a safety snapshot is taken before replacing anything)
PYTHONPATH=server server/.venv/bin/python server/scripts/restore.py <archive.tar.gz>
```

**Daily cron** (rotation keeps the newest `OPENCOOK_BACKUP_KEEP`):
```cron
15 3 * * * cd /path/to/openCook && PYTHONPATH=server server/.venv/bin/python server/scripts/backup.py >> server/data/backups/backup.log 2>&1
```

To survive an OS reinstall, point `OPENCOOK_BACKUP_DIR` at an external/network drive (or copy the
archives off-box). For Docker, mount a host dir / NAS at the backup path and `chown` it to uid
`10001`, or bind-mount all of `/data`.

## App updates

Devices can update themselves from the server — no app store or USB cable. Build a signed release
and bump `versionCode`, then publish it:

```bash
PYTHONPATH=server server/.venv/bin/python server/scripts/publish_apk.py \
    app-release.apk --version-code 2 --version-name 1.1
```

This stores the APK under `/data/apks/` and writes `latest.json`. In the app, **Admin → App update →
"Nach Update suchen"** offers the download and install (each device grants openCook the one-time
"install unknown apps" permission once). Full release steps:
[Building → Releasing app updates](building.md#releasing-app-updates-self-hosted).

## Security notes

- Most endpoints are **unauthenticated by design** (trusted LAN/VPN). **Never expose port 8000 to
  the internet.** Admin endpoints are password-protected; households can also set a join PIN.
- The server reaches no filesystem beyond `/data` (in Docker) and contacts no third party except the
  host's Ollama.
