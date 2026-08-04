"""BackgroundTasks로 실행되는 실제 처리 함수. 요청-응답과 분리된 별도 DB 세션을 쓴다."""

import uuid

from app.db.models import ProcessingJob
from app.db.session import SessionLocal
from app.services.pipeline_service import run_full_pipeline


def process_meeting(job_id: uuid.UUID, meeting_id: int) -> None:
    db = SessionLocal()
    try:
        job = db.get(ProcessingJob, job_id)
        job.status = "processing"
        db.commit()

        run_full_pipeline(meeting_id, db)

        job.status = "done"
        db.commit()
    except Exception as exc:
        db.rollback()
        job = db.get(ProcessingJob, job_id)
        job.status = "failed"
        job.error_message = str(exc)
        db.commit()
        raise
    finally:
        db.close()
