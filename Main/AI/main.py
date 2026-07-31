"""
전체 파이프라인 실행

LiveKit egress 회의 폴더 -> STT (화자는 참가자 폴더로 이미 구분됨)
-> 관련 과거 회의록 검색(임베딩 유사도) -> LLM 구조화 추출 -> MD 회의록 렌더링 -> 히스토리 저장

실행 방법:
    python main.py path/to/meeting_dir

meeting_dir은 participants/<participant_id>/EG_*.json, TR_*.ogg 를 담은 폴더다.

결과물 (output/<회의폴더명>/ 아래에 저장):
    meeting_report_transcript.json (STT 원본 결과)
    meeting_report.json            (LLM이 추출한 구조화 회의록)
    meeting_report.md              (최종 회의록 문서)
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
from rag import get_relevant_meetings, save_meeting
from extract import extract_meeting_minutes
from render import render_markdown

OUTPUT_DIR = Path("output")


def main(meeting_dir: str):
    meeting_id = Path(meeting_dir).name
    run_dir = OUTPUT_DIR / meeting_id
    run_dir.mkdir(parents=True, exist_ok=True)

    # 1. STT
    print("=" * 50)
    print("1단계: STT")
    print("=" * 50)
    transcript = transcribe_meeting(meeting_dir)

    transcript_json = [seg.model_dump() for seg in transcript]
    (run_dir / "meeting_report_transcript.json").write_text(
        json.dumps(transcript_json, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    print(f"-> {run_dir}/meeting_report_transcript.json 저장 완료 ({len(transcript)}개 세그먼트)\n")

    if not transcript:
        print("경고: 전사 결과가 비어있습니다. 오디오 파일/언어 설정을 확인하세요.")
        return

    # 1.5. 관련 과거 회의록 검색 (임베딩 유사도 top-k, STT -> RAG -> LLM 추출 순서)
    print("=" * 50)
    print("1.5단계: 관련 과거 회의록 검색")
    print("=" * 50)
    query_text = "\n".join(seg.text for seg in transcript)
    relevant_meetings = get_relevant_meetings(query_text, exclude_id=meeting_id)
    if relevant_meetings:
        ids = ", ".join(doc_id for doc_id, _ in relevant_meetings)
        print(f"-> 참고한 과거 회의: {ids}")
    else:
        print("-> 참고할 과거 회의록이 없습니다 (첫 회의).")
    print()

    # 2. LLM 구조화 추출
    print("=" * 50)
    print("2단계: LLM 구조화 추출")
    print("=" * 50)
    minutes = extract_meeting_minutes(
        transcript,
        previous_meetings=[document for _, document in relevant_meetings],
    )

    (run_dir / "meeting_report.json").write_text(
        minutes.model_dump_json(indent=2, exclude_none=False), encoding="utf-8"
    )
    print(f"-> {run_dir}/meeting_report.json 저장 완료\n")

    # 3. MD 렌더링
    print("=" * 50)
    print("3단계: 회의록 렌더링")
    print("=" * 50)
    md = render_markdown(minutes, transcript)
    (run_dir / "meeting_report.md").write_text(md, encoding="utf-8")
    print(f"-> {run_dir}/meeting_report.md 저장 완료\n")

    # 4. 이번 회의록을 히스토리에 저장 (다음 회의가 참고할 수 있도록)
    print("=" * 50)
    print("4단계: 회의록 히스토리 저장")
    print("=" * 50)
    save_meeting(meeting_id, md)
    print(f"-> 히스토리에 저장 완료 (id={meeting_id})\n")

    print(f"전체 파이프라인 완료! {run_dir}/meeting_report.md 를 확인하세요.")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        print("사용법: python main.py <회의 폴더 경로>")
        sys.exit(1)
    main(sys.argv[1])
