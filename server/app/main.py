"""
FastAPI application entry point.

Starts the in-process job worker via the lifespan and wires the routers.
"""

import asyncio
import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.middleware.gzip import GZipMiddleware
from fastapi.responses import HTMLResponse

from app.api import admin, households, images, imports, jobs, sync
from app.db import engine, init_db
from app.discovery import MdnsAdvertiser
from app.worker import worker_loop

logging.basicConfig(level=logging.INFO)


@asynccontextmanager
async def lifespan(_app: FastAPI):
    init_db()

    # Fold the WAL back into the main DB and shrink the -wal file to 0 bytes.
    # SQLite auto-checkpoints but never truncates, so the WAL keeps its high-water
    # mark; doing it at startup (DB idle, server often restarts) keeps it bounded.
    with engine.connect() as conn:
        conn.exec_driver_sql("PRAGMA wal_checkpoint(TRUNCATE)")

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

# Compress responses.
app.add_middleware(GZipMiddleware, minimum_size=1000)

# Allow browser clients and extensions to call the API cross-origin.
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


@app.get("/", tags=["meta"], response_class=HTMLResponse)
def root():
    return """
    <html>
      <head>
        <title>openCook server</title>
        <style>
          body {
            font-family: system-ui, sans-serif;
            margin: 2rem;
            line-height: 1.5;
          }
          .card {
            max-width: 720px;
            padding: 1.5rem;
            border: 1px solid #ddd;
            border-radius: 12px;
          }
          code {
            background: #f4f4f4;
            padding: 0.2rem 0.4rem;
            border-radius: 4px;
          }
        </style>
      </head>
      <body>
        <div class="card">
          <h1>openCook server is running</h1>
          <p>Visit <a href="/docs">/docs</a> for API documentation.</p>
          <p>Health check: <a href="/health">/health</a></p>
        </div>
      </body>
    </html>
    """


@app.get("/health", tags=["meta"])
def health() -> dict[str, str]:
    return {"status": "ok"}
