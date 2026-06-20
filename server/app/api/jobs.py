"""Async recipe-extraction job endpoints."""

import json
import uuid
from pathlib import Path

from fastapi import APIRouter, Depends, File, HTTPException, UploadFile
from sqlalchemy.orm import Session

from app.config import get_settings
from app.db import get_session
from app.models import Job, JobStatus
from app.schemas import JobCreatedResponse, JobStatusResponse

router = APIRouter(prefix="/jobs", tags=["jobs"])


@router.post("", response_model=JobCreatedResponse, status_code=201)
async def create_job(
    image: UploadFile = File(...),
    session: Session = Depends(get_session),
) -> JobCreatedResponse:
    """Accept a photo and enqueue it for extraction. The worker picks it up async."""
    settings = get_settings()
    suffix = Path(image.filename or "upload.jpg").suffix or ".jpg"
    image_path = settings.images_dir / f"{uuid.uuid4()}{suffix}"
    image_path.write_bytes(await image.read())

    job = Job(status=JobStatus.PENDING, image_path=str(image_path))
    session.add(job)
    session.commit()
    return JobCreatedResponse(job_id=job.id, status=job.status)


@router.get("/{job_id}", response_model=JobStatusResponse)
def get_job(job_id: str, session: Session = Depends(get_session)) -> JobStatusResponse:
    job = session.get(Job, job_id)
    if job is None:
        raise HTTPException(status_code=404, detail="Job not found")
    return JobStatusResponse(
        job_id=job.id,
        status=job.status,
        stage=job.stage,
        result=json.loads(job.result_json) if job.result_json else None,
        error=job.error,
        created_at=job.created_at,
        updated_at=job.updated_at,
    )
