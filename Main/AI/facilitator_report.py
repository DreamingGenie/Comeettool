"""
퍼실리테이터 리포트 파이프라인

LiveKit egress 회의 폴더 -> STT(전사 재사용/생성) -> 참여 균형 집계
-> 관련 과거 Facilitator 보고서 검색(임베딩 유사도) -> LLM 구조화 추출
-> MD 보고서 렌더링 -> 히스토리 저장

실행 방법:
    python facilitator_report.py path/to/meeting_dir

meeting_dir은 participants/<participant_id>/EG_*.json, TR_*.ogg 를 담은 폴더다.

결과물 (output/<회의폴더명>/ 아래에 저장):
    meeting_report_transcript.json (STT 원본 결과, 재사용 또는 신규 생성)
    facilitator_report.json  (LLM이 추출한 구조화 회의 품질 평가 데이터)
    facilitator_report.md    (최종 회의 품질 평가 보고서)
"""

import os
import sys
import json
from pathlib import Path

from dotenv import load_dotenv

load_dotenv()  # .env 파일에서 OPENAI_API_KEY, DATABASE_URL 로드

# Windows에서 torch/numpy가 각자 번들한 OpenMP 런타임이 충돌해 죽는 것을 방지
os.environ.setdefault("KMP_DUPLICATE_LIB_OK", "TRUE")

from transcribe import transcribe_meeting
from schema import TranscriptSegment
from participation import compute_participation_stats
from rag_facilitator import get_relevant_facilitator_reports, save_facilitator_report
from extract_facilitator import extract_facilitator_report
from render_facilitator import render_facilitator_markdown

OUTPUT_DIR = Path("output")


def get_transcript(meeting_dir: str) -> list[TranscriptSegment]:
    """output/<회의ID>/meeting_report_transcript.json이 있으면 읽어 재사용하고, 없으면 STT로 새로 생성해 저장한다."""
    meeting_id = Path(meeting_dir).name
    run_dir = OUTPUT_DIR / meeting_id
    transcript_path = run_dir / "meeting_report_transcript.json"

    if transcript_path.exists():
        print(f"-> 기존 {transcript_path} 재사용")
        raw = json.loads(transcript_path.read_text(encoding="utf-8"))
        return [TranscriptSegment(**seg) for seg in raw]

    print(f"-> {transcript_path} 없음, 새로 STT 수행")
    transcript = transcribe_meeting(meeting_dir)

    run_dir.mkdir(parents=True, exist_ok=True)
    transcript_json = [seg.model_dump() for seg in transcript]
    transcript_path.write_text(
        json.dumps(transcript_json, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    print(f"-> {transcript_path} 저장 완료 ({len(transcript)}개 세그먼트)")

    return transcript


def main(meeting_dir: str):
    meeting_id = Path(meeting_dir).name
    run_dir = OUTPUT_DIR / meeting_id
    run_dir.mkdir(parents=True, exist_ok=True)

    # 1. 전사 확보 (재사용 또는 STT 신규 생성)
    print("=" * 50)
    print("1단계: 전사 확보")
    print("=" * 50)
    transcript = get_transcript(meeting_dir)
    print()

    if not transcript:
        print("경고: 전사 결과가 비어있습니다. 오디오 파일/언어 설정을 확인하세요.")
        return

    # 2. 참여 균형 집계 (코드로 직접 계산, LLM 의존 없음)
    print("=" * 50)
    print("2단계: 참여 균형 집계")
    print("=" * 50)
    participation_stats = compute_participation_stats(transcript)
    for stat in participation_stats:
        print(
            f"-> [{stat.speaker}] 발화 {stat.utterance_count}회, "
            f"{stat.speaking_seconds:.1f}초, 비율 {stat.ratio * 100:.1f}%"
        )
    print()

    # 3. 관련 과거 Facilitator 보고서 검색 (임베딩 유사도 top-k)
    print("=" * 50)
    print("3단계: 관련 과거 Facilitator 보고서 검색")
    print("=" * 50)
    query_text = "\n".join(seg.text for seg in transcript)
    relevant_reports = get_relevant_facilitator_reports(query_text, exclude_id=meeting_id)
    if relevant_reports:
        ids = ", ".join(doc_id for doc_id, _ in relevant_reports)
        print(f"-> 참고한 과거 보고서: {ids}")
    else:
        print("-> 참고할 과거 보고서가 없습니다 (첫 회의).")
    print()

    # 4. LLM 구조화 추출
    print("=" * 50)
    print("4단계: LLM 구조화 추출")
    print("=" * 50)
    report = extract_facilitator_report(
        transcript,
        participation_stats,
        previous_reports=[document for _, document in relevant_reports],
    )

    (run_dir / "facilitator_report.json").write_text(
        report.model_dump_json(indent=2, exclude_none=False), encoding="utf-8"
    )
    print(f"-> {run_dir}/facilitator_report.json 저장 완료\n")

    # 5. MD 렌더링
    print("=" * 50)
    print("5단계: 보고서 렌더링")
    print("=" * 50)
    md = render_facilitator_markdown(report, participation_stats, transcript)
    (run_dir / "facilitator_report.md").write_text(md, encoding="utf-8")
    print(f"-> {run_dir}/facilitator_report.md 저장 완료\n")

    # 6. 이번 보고서를 히스토리에 저장 (다음 회의가 참고할 수 있도록)
    print("=" * 50)
    print("6단계: 보고서 히스토리 저장")
    print("=" * 50)
    save_facilitator_report(meeting_id, md)
    print(f"-> 히스토리에 저장 완료 (id={meeting_id})\n")

    print(f"전체 파이프라인 완료! {run_dir}/facilitator_report.md 를 확인하세요.")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        print("사용법: python facilitator_report.py <회의 폴더 경로>")
        sys.exit(1)
    main(sys.argv[1])
