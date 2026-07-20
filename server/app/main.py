"""FastAPI application entry point.

Starts the in-process job worker via the lifespan and wires the routers.
"""

import asyncio
import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.middleware.gzip import GZipMiddleware

from app.api import admin, households, images, imports, jobs, sync
from app.db import engine, init_db
from app.security import seed_admin_password
from app.discovery import MdnsAdvertiser
from app.worker import worker_loop

logging.basicConfig(level=logging.INFO)


@asynccontextmanager
async def lifespan(_app: FastAPI):
    init_db()
    # Fold the WAL back into the main DB and shrink the -wal file to 0 bytes. SQLite
    # auto-checkpoints but never truncates, so the WAL keeps its high-water mark; doing
    # it at startup (DB idle, server often restarts) keeps it bounded. No data loss.
    with engine.connect() as conn:
        conn.exec_driver_sql("PRAGMA wal_checkpoint(TRUNCATE)")
    seed_admin_password()
    mdns = MdnsAdvertiser()
    await mdns.start()
    stop_event = asyncio.Event()
    worker_task = asyncio.create_task(worker_loop(stop_event))
    try:
        yield
    finally:
        stop_event.set()
        await worker_task
        await mdns.stop()


app = FastAPI(title="openCook server", version="0.1.0", lifespan=lifespan)

# Compress responses (clients send Accept-Encoding: gzip — OkHttp does automatically and
# decompresses transparently). The initial-sync JSON is dominated by repeated keys/dataset
# names and shrinks ~10× (≈5 MB → ≈0.5 MB), cutting the download phase. minimum_size skips
# tiny responses; already-compressed JPEGs from /images gain little but cost negligibly.
app.add_middleware(GZipMiddleware, minimum_size=1000)

# Allow the browser extension (any chrome-extension:// origin) and other browser
# clients to call the API cross-origin. The server is LAN/VPN-only and uses a header
# (not cookies) as its credential, so a permissive policy is fine and avoids per-origin
# config. Handles the OPTIONS preflight a custom-header POST triggers; allow_private_network
# acknowledges Chrome's Private Network Access preflight (the server is a private LAN IP).
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
    allow_private_network=True,
)


app.include_router(jobs.router)
app.include_router(imports.router)
app.include_router(images.router)
app.include_router(households.router)
app.include_router(sync.router)
app.include_router(admin.router)


@app.get("/health", tags=["meta"])
def health() -> dict[str, str]:
    return {"status": "ok"}
