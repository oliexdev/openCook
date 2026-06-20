"""In-process async worker that drains the jobs table.

No external queue (Redis/Celery) — keeps self-hosting to a single process.
Phase 0 skeleton: it claims pending jobs but the actual Ollama call + cropping
are implemented in Phase 1.
"""

import asyncio
import json
import logging
from pathlib import Path

from sqlalchemy import select

from app.config import get_settings
from app.db import SessionLocal
from app.extraction import RecipeExtractor
from app.models import Job, JobStatus
from app.ollama_client import OllamaClient

logger = logging.getLogger(__name__)


async def _process_one(job_id: str) -> None:
    settings = get_settings()
    extractor = RecipeExtractor(OllamaClient(), settings.images_dir)

    with SessionLocal() as session:
        job = session.get(Job, job_id)
        image_path = job.image_path if job else None
    if not image_path:
        return

    def set_stage(stage: str) -> None:
        with SessionLocal() as session:
            job = session.get(Job, job_id)
            if job is not None:
                job.stage = stage
                session.commit()

    try:
        recipes = await extractor.extract(Path(image_path), on_stage=set_stage)
        with SessionLocal() as session:
            job = session.get(Job, job_id)
            job.result_json = json.dumps(recipes, ensure_ascii=False)
            job.status = JobStatus.DONE
            job.stage = None
            session.commit()
        logger.info("Job %s done: %d recipe(s) extracted.", job_id, len(recipes))
    except Exception as exc:  # noqa: BLE001 - record the failure on the job
        logger.exception("Job %s failed", job_id)
        with SessionLocal() as session:
            job = session.get(Job, job_id)
            job.status = JobStatus.ERROR
            job.error = str(exc)
            session.commit()


async def worker_loop(stop_event: asyncio.Event) -> None:
    settings = get_settings()
    logger.info("Job worker started (poll interval %.1fs).", settings.worker_poll_interval)
    while not stop_event.is_set():
        try:
            with SessionLocal() as session:
                job = session.scalars(
                    select(Job).where(Job.status == JobStatus.PENDING).limit(1)
                ).first()
                if job is not None:
                    job.status = JobStatus.PROCESSING
                    session.commit()
                    job_id = job.id
                else:
                    job_id = None
            if job_id is not None:
                await _process_one(job_id)
        except Exception:  # noqa: BLE001 - worker must never die on a single bad job
            logger.exception("Worker iteration failed")
        try:
            await asyncio.wait_for(stop_event.wait(), timeout=settings.worker_poll_interval)
        except asyncio.TimeoutError:
            pass
    logger.info("Job worker stopped.")
