"""
S3에서 받은 녹음 -> 파이프라인 로직(app/pipeline/) 실행 -> DB 저장

STT/RAG/LLM 추출/렌더링 로직은 app/pipeline/ 패키지(구 루트 스크립트들)를 그대로 쓴다.
"""

import json
import subprocess
from pathlib import Path

from sqlalchemy.orm import Session

from app.db.models import AudioTranscriptionRecord, FacilitatorReportRecord, MeetingMinutesRecord
from app.pipeline.common.transcribe import transcribe_meeting
from app.pipeline.facilitator.extract import extract_facilitator_report
from app.pipeline.facilitator.history_text import build_facilitator_history_text
from app.pipeline.facilitator.participation import compute_participation_stats
from app.pipeline.facilitator.rag import get_relevant_facilitator_reports, save_facilitator_report
from app.pipeline.minutes.extract import extract_meeting_minutes
from app.pipeline.minutes.history_text import build_minutes_history_text
from app.pipeline.minutes.rag import get_relevant_meetings, save_meeting
from app.schemas.meeting import SegmentMeta
from app.services.s3_client import download_meeting_recordings

_SILENCE_SAMPLE_RATE = 48000


def _concat_segments_with_silence(
    segments: list[tuple[SegmentMeta, Path]], output_path: Path
) -> None:
    """(메타데이터, ogg 경로) 목록을 시작 시각순으로 무음 패딩 후 ogg 하나로 합친다.

    세그먼트 사이 간격(이전 endedAt ~ 다음 startedAt)만큼 ffmpeg lavfi anullsrc로 무음을
    채워 넣어, 병합된 오디오의 시간축이 실제 회의 시간축과 어긋나지 않게 한다.
    """
    cmd = ["ffmpeg", "-y"]
    input_count = 0
    prev_ended_at = None

    for meta, ogg_path in segments:
        if prev_ended_at is not None:
            gap = (meta.started_at - prev_ended_at) / 1000
            if gap > 0:
                cmd += [
                    "-t", f"{gap:.3f}",
                    "-f", "lavfi",
                    "-i", f"anullsrc=r={_SILENCE_SAMPLE_RATE}:cl=mono",
                ]
                input_count += 1
        cmd += ["-i", str(ogg_path)]
        input_count += 1
        prev_ended_at = meta.ended_at

    normalize = "".join(
        f"[{i}:a]aformat=sample_rates={_SILENCE_SAMPLE_RATE}:channel_layouts=mono[a{i}];"
        for i in range(input_count)
    )
    concat_inputs = "".join(f"[a{i}]" for i in range(input_count))
    filter_complex = f"{normalize}{concat_inputs}concat=n={input_count}:v=0:a=1[out]"

    cmd += ["-filter_complex", filter_complex, "-map", "[out]", "-c:a", "libopus", str(output_path)]
    subprocess.run(cmd, check=True, capture_output=True)


def _build_legacy_meeting_dir(session_root: str, meeting_id: str) -> str:
    """S3에서 내려받은 participants/{pid}/segment-{seq}.ogg + segment-{seq}.json 구조를
    transcribe.py가 기대하는 participants/{pid}/EG_*.json + TR_*.ogg 구조로 변환한다.

    참가자 한 명이 재연결 등으로 여러 세그먼트를 가질 수 있으므로, startedAt 순으로 정렬한
    뒤 세그먼트 사이 간격만큼 무음을 채워 ffmpeg로 참가자당 오디오 1개로 이어붙인다.
    """
    legacy_dir = Path(session_root).parent / f"{meeting_id}_legacy"

    for participant_dir in sorted((Path(session_root) / "participants").iterdir()):
        if not participant_dir.is_dir():
            continue

        segments = []
        for json_path in participant_dir.glob("segment-*.json"):
            meta = SegmentMeta.model_validate_json(json_path.read_text(encoding="utf-8"))
            segments.append((meta, json_path.with_suffix(".ogg")))
        if not segments:
            continue
        segments.sort(key=lambda s: s[0].started_at)

        out_dir = legacy_dir / "participants" / participant_dir.name
        out_dir.mkdir(parents=True, exist_ok=True)

        merged_ogg = out_dir / "TR_merged.ogg"
        _concat_segments_with_silence(segments, merged_ogg)

        eg_path = out_dir / "EG_merged.json"
        eg_path.write_text(
            json.dumps(
                {
                    "started_at": segments[0][0].started_at * 1_000_000,  # ms -> ns
                    "track_id": "TR_merged",
                }
            ),
            encoding="utf-8",
        )

    return str(legacy_dir)


def run_full_pipeline(meeting_id: int, db: Session) -> None:
    """meeting_id 하나에 대해 회의록 + Facilitator 보고서를 모두 만들어 DB에 저장한다."""
    import tempfile

    with tempfile.TemporaryDirectory() as tmp:
        session_root = download_meeting_recordings(meeting_id, tmp)
        meeting_dir = _build_legacy_meeting_dir(session_root, meeting_id)

        transcript = transcribe_meeting(meeting_dir)
        if not transcript:
            raise RuntimeError(f"{meeting_id}: 전사 결과가 비어 있습니다.")

        # --- STT 결과 원본 저장 (audio_transcriptions) ---
        db.merge(
            AudioTranscriptionRecord(
                meeting_id=meeting_id,
                transcript=[seg.model_dump() for seg in transcript],
            )
        )

        # --- MVP: 회의록 ---
        query_text = "\n".join(seg.text for seg in transcript)
        relevant_meetings = get_relevant_meetings(query_text, exclude_id=meeting_id)
        minutes = extract_meeting_minutes(
            transcript, previous_meetings=[doc for _, doc in relevant_meetings]
        )
        save_meeting(meeting_id, build_minutes_history_text(minutes))

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
        save_facilitator_report(
            meeting_id, build_facilitator_history_text(report, participation_stats)
        )

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
