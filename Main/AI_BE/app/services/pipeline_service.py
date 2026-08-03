"""
S3에서 받은 녹음 -> 파이프라인 로직(app/pipeline/) 실행 -> DB 저장

STT/RAG/LLM 추출/렌더링 로직은 app/pipeline/ 패키지(구 루트 스크립트들)를 그대로 쓴다.
"""

from sqlalchemy.orm import Session

from app.db.models import FacilitatorReportRecord, MeetingMinutesRecord
from app.pipeline.common.transcribe import transcribe_meeting
from app.pipeline.facilitator.extract import extract_facilitator_report
from app.pipeline.facilitator.participation import compute_participation_stats
from app.pipeline.facilitator.rag import get_relevant_facilitator_reports, save_facilitator_report
from app.pipeline.facilitator.render import render_facilitator_markdown
from app.pipeline.minutes.extract import extract_meeting_minutes
from app.pipeline.minutes.rag import get_relevant_meetings, save_meeting
from app.pipeline.minutes.render import render_markdown
from app.services.s3_client import download_meeting_recordings


def _build_legacy_meeting_dir(session_root: str, meeting_id: str) -> str:
    """S3에서 내려받은 conferences/{meetingId}/participants/{pid}/sessions/{sid}/... 구조를
    transcribe.py가 기대하는 participants/{pid}/EG_*.json + TR_*.ogg 구조로 변환한다.

    TODO: segment-{seq}.json의 실제 필드(시작 시각, 순서 등)를 확인한 뒤 구현.
    현재 세그먼트 병합(ogg concat, 타임스탬프 오프셋 계산) 로직이 없어 그대로 두면
    transcribe_meeting()이 실패한다. metadata/segment-*.json 스키마 확정되는 대로
    여기서 세그먼트들을 시간순으로 이어붙여 참가자당 오디오 1개 + EG_*.json 1개로
    만들어야 한다.
    """
    raise NotImplementedError(
        "segment-*.json 메타데이터 스키마 확정 후 세그먼트 병합 로직을 구현해야 합니다."
    )


def run_full_pipeline(meeting_id: str, db: Session) -> None:
    """meeting_id 하나에 대해 회의록 + Facilitator 보고서를 모두 만들어 DB에 저장한다."""
    import tempfile

    with tempfile.TemporaryDirectory() as tmp:
        session_root = download_meeting_recordings(meeting_id, tmp)
        meeting_dir = _build_legacy_meeting_dir(session_root, meeting_id)

        transcript = transcribe_meeting(meeting_dir)
        if not transcript:
            raise RuntimeError(f"{meeting_id}: 전사 결과가 비어 있습니다.")

        # --- MVP: 회의록 ---
        query_text = "\n".join(seg.text for seg in transcript)
        relevant_meetings = get_relevant_meetings(query_text, exclude_id=meeting_id)
        minutes = extract_meeting_minutes(
            transcript, previous_meetings=[doc for _, doc in relevant_meetings]
        )
        minutes_md = render_markdown(minutes, transcript)
        save_meeting(meeting_id, minutes_md)

        db.merge(
            MeetingMinutesRecord(
                meeting_id=meeting_id,
                title=minutes.title,
                summary=minutes.summary,
                topics=[t.model_dump() for t in minutes.topics],
                decisions=[d.model_dump() for d in minutes.decisions],
                action_items=[a.model_dump() for a in minutes.action_items],
                open_issues=minutes.open_issues,
            )
        )

        # --- Sub: Facilitator 보고서 ---
        participation_stats = compute_participation_stats(transcript)
        relevant_reports = get_relevant_facilitator_reports(query_text, exclude_id=meeting_id)
        report = extract_facilitator_report(
            transcript,
            participation_stats,
            previous_reports=[doc for _, doc in relevant_reports],
        )
        report_md = render_facilitator_markdown(report, participation_stats, transcript)
        save_facilitator_report(meeting_id, report_md)

        db.merge(
            FacilitatorReportRecord(
                meeting_id=meeting_id,
                title=report.title,
                meeting_type=report.meeting_type,
                overall_review=report.overall_review,
                participation_comment=report.participation_comment,
                participation_stats=[s.model_dump() for s in participation_stats],
                quality_evaluation=report.quality_evaluation.model_dump(),
                strengths=[s.model_dump() for s in report.strengths],
                improvements=[i.model_dump() for i in report.improvements],
                decision_process_checks=[c.model_dump() for c in report.decision_process_checks],
                unresolved_issues_evaluation=report.unresolved_issues_evaluation,
                next_meeting_suggestions=report.next_meeting_suggestions,
            )
        )

        db.commit()
