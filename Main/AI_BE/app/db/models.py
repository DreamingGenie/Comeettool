"""
ORM 모델

기존 파이프라인은 결과를 파일(md/json)로만 저장했는데, 서비스로 전환하면서
구조화 데이터를 API로 조회할 수 있어야 하므로 DB 테이블로 옮긴다.
schema.py(MeetingMinutes)/schema_facilitator.py(FacilitatorReport)의 필드 구조를
그대로 따르되, 리스트/중첩 객체는 JSONB 컬럼 하나로 저장한다(항목별 조회/조인이
필요해지면 그때 정규화한다 - 지금은 과도한 설계).
"""

import uuid
from datetime import datetime

from sqlalchemy import BigInteger, Boolean, Column, DateTime, ForeignKey, String, Table, Text, func
from sqlalchemy.dialects.postgresql import JSONB, UUID
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.db.session import Base

# meeting_rooms는 Main Backend(Java/JPA)가 소유·마이그레이션하는 테이블이다.
# AI_BE는 이 테이블을 만들거나 매핑 클래스로 관리하지 않고, FK 대상 해석용으로만
# PK 컬럼 하나짜리 Table을 같은 MetaData에 등록해둔다(등록해야 SQLAlchemy가
# ForeignKey("meeting_rooms.meeting_room_id")를 resolve해서 flush 순서를 계산할 수 있음).
# create_all()은 checkfirst=True라 이미 존재하는 테이블은 건드리지 않는다 - 단,
# Main Backend 마이그레이션이 AI_BE 기동보다 먼저 적용되어 있어야 한다.
meeting_rooms = Table(
    "meeting_rooms",
    Base.metadata,
    Column("meeting_room_id", BigInteger, primary_key=True),
)


class ProcessingJob(Base):
    """meeting_id 하나를 처리하는 백그라운드 작업 1건의 상태."""

    __tablename__ = "processing_jobs"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    # meeting_rooms.meeting_room_id(Main Backend)와 동일한 값. STT 시작 시
    # AiTranscriptionClient가 Long meetingId로 넘기는 것과 같은 ID.
    meeting_id: Mapped[int] = mapped_column(
        BigInteger, ForeignKey("meeting_rooms.meeting_room_id"), index=True
    )
    # pending -> processing -> done | failed
    status: Mapped[str] = mapped_column(String, default="pending")
    error_message: Mapped[str | None] = mapped_column(Text, default=None)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), onupdate=func.now()
    )


class AudioTranscriptionRecord(Base):
    """common/transcribe.py TranscriptSegment 리스트(STT 결과) 저장."""

    __tablename__ = "audio_transcriptions"

    meeting_id: Mapped[int] = mapped_column(
        BigInteger, ForeignKey("meeting_rooms.meeting_room_id"), primary_key=True
    )
    transcript: Mapped[list] = mapped_column(JSONB, default=list)
    # transcript 원소: {"speaker": str, "start": float, "end": float, "text": str}
    md_url: Mapped[str | None] = mapped_column(String, default=None)
    pdf_url: Mapped[str | None] = mapped_column(String, default=None)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())


class MeetingMinutesRecord(Base):
    """schema.py MeetingMinutes 결과 저장."""

    __tablename__ = "meeting_minutes"

    meeting_id: Mapped[int] = mapped_column(
        BigInteger, ForeignKey("meeting_rooms.meeting_room_id"), primary_key=True
    )
    title: Mapped[str] = mapped_column(String)
    summary: Mapped[str] = mapped_column(Text)
    topics: Mapped[list] = mapped_column(JSONB, default=list)
    decisions: Mapped[list] = mapped_column(JSONB, default=list)
    action_items: Mapped[list] = mapped_column(JSONB, default=list)
    open_issues: Mapped[list] = mapped_column(JSONB, default=list)
    is_confirmed: Mapped[bool] = mapped_column(Boolean, default=False)
    confirmed_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), default=None)
    md_url: Mapped[str | None] = mapped_column(String, default=None)
    pdf_url: Mapped[str | None] = mapped_column(String, default=None)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())


class FacilitatorReportRecord(Base):
    """schema_facilitator.py FacilitatorReport 결과 + 참여 균형 통계 저장."""

    __tablename__ = "facilitator_reports"

    meeting_id: Mapped[int] = mapped_column(
        BigInteger, ForeignKey("meeting_rooms.meeting_room_id"), primary_key=True
    )
    title: Mapped[str] = mapped_column(String)
    meeting_type: Mapped[str | None] = mapped_column(String, default=None)
    overall_review: Mapped[str] = mapped_column(Text)
    participation_comment: Mapped[str] = mapped_column(Text)
    participation_stats: Mapped[list] = mapped_column(JSONB, default=list)  # participation.py 결과
    quality_evaluation: Mapped[dict] = mapped_column(JSONB)
    strengths: Mapped[list] = mapped_column(JSONB, default=list)
    improvements: Mapped[list] = mapped_column(JSONB, default=list)
    decision_process_checks: Mapped[list] = mapped_column(JSONB, default=list)
    unresolved_issues_evaluation: Mapped[list] = mapped_column(JSONB, default=list)
    next_meeting_suggestions: Mapped[list] = mapped_column(JSONB, default=list)
    md_url: Mapped[str | None] = mapped_column(String, default=None)
    pdf_url: Mapped[str | None] = mapped_column(String, default=None)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())

