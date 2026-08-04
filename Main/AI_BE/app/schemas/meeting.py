"""API 요청/응답 스키마 (schema.py의 LLM 추출 스키마와는 별개)"""

from datetime import datetime

from pydantic import BaseModel


class ProcessMeetingResponse(BaseModel):
    """POST /meetings/{meeting_id}/process 응답 - 접수만 하고 바로 반환(비동기 처리)."""

    job_id: str
    meeting_id: int
    status: str


class ProcessingJobStatusResponse(BaseModel):
    job_id: str
    meeting_id: int
    status: str
    error_message: str | None = None
    created_at: datetime
    updated_at: datetime

    model_config = {"from_attributes": True}
