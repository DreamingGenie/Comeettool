"""API 요청/응답 스키마 (schema.py의 LLM 추출 스키마와는 별개)"""

from datetime import datetime

from pydantic import BaseModel, Field


class SegmentMeta(BaseModel):
    """S3 conferences/{meetingId}/participants/{participantId}/segment-{seq}.json 메타데이터.

    같은 경로의 segment-{seq}.ogg 오디오 파일 하나에 1:1로 대응한다.
    """

    meeting_room_id: int = Field(alias="meetingRoomId")
    participant_id: int = Field(alias="participantId")
    sequence: int
    started_at: int = Field(alias="startedAt")  # epoch milliseconds
    ended_at: int = Field(alias="endedAt")  # epoch milliseconds
    duration_ms: int = Field(alias="durationMs")
    audio_sha256: str = Field(alias="audioSha256")
    audio_object_key: str = Field(alias="audioObjectKey")
    uploaded_at: datetime = Field(alias="uploadedAt")

    model_config = {"populate_by_name": True}


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
