"""회의 처리 트리거 + 결과 조회 API"""

import secrets

from fastapi import APIRouter, BackgroundTasks, Depends, Header, HTTPException
from sqlalchemy.orm import Session

from app.core.config import get_settings
from app.db.models import FacilitatorReportRecord, MeetingMinutesRecord, ProcessingJob
from app.db.session import get_db
from app.schemas.meeting import ProcessingJobStatusResponse, ProcessMeetingResponse
from app.workers.processor import process_meeting

router = APIRouter(prefix="/meetings", tags=["meetings"])


def verify_internal_token(x_internal_token: str = Header(..., alias="X-Internal-Token")) -> None:
    """BE -> AI 내부 호출 인증. X-Internal-Token 헤더를 AI_INTERNAL_TOKEN과 대조한다."""
    expected = get_settings().AI_INTERNAL_TOKEN
    if not secrets.compare_digest(x_internal_token, expected):
        raise HTTPException(status_code=401, detail="invalid internal token")


@router.post(
    "/{meeting_id}/process",
    response_model=ProcessMeetingResponse,
    status_code=202,
    dependencies=[Depends(verify_internal_token)],
)
def trigger_processing(
    meeting_id: int, background_tasks: BackgroundTasks, db: Session = Depends(get_db)
):
    """회의 종료 후 호출. 녹음이 S3에 다 올라간 뒤 호출한다고 가정한다.

    바로 202를 반환하고, 실제 STT/LLM 처리는 백그라운드에서 진행한다.
    X-Internal-Token 헤더로 BE 내부 호출임을 검증한다.
    """
    job = ProcessingJob(meeting_id=meeting_id, status="pending")
    db.add(job)
    db.commit()
    db.refresh(job)

    background_tasks.add_task(process_meeting, job.id, meeting_id)

    return ProcessMeetingResponse(job_id=str(job.id), meeting_id=meeting_id, status=job.status)


@router.get("/jobs/{job_id}", response_model=ProcessingJobStatusResponse)
def get_job_status(job_id: str, db: Session = Depends(get_db)):
    job = db.get(ProcessingJob, job_id)
    if job is None:
        raise HTTPException(status_code=404, detail="job not found")
    return ProcessingJobStatusResponse(
        job_id=str(job.id),
        meeting_id=job.meeting_id,
        status=job.status,
        error_message=job.error_message,
        created_at=job.created_at,
        updated_at=job.updated_at,
    )


@router.get("/{meeting_id}/minutes")
def get_meeting_minutes(meeting_id: int, db: Session = Depends(get_db)):
    record = db.get(MeetingMinutesRecord, meeting_id)
    if record is None:
        raise HTTPException(status_code=404, detail="not found")
    return record


@router.get("/{meeting_id}/facilitator-report")
def get_facilitator_report(meeting_id: int, db: Session = Depends(get_db)):
    record = db.get(FacilitatorReportRecord, meeting_id)
    if record is None:
        raise HTTPException(status_code=404, detail="not found")
    return record
