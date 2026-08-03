"""
transcript(발화 로그) + 참여 균형 통계 + 과거 Facilitator 보고서 맥락
-> LLM -> 구조화된 회의 품질 평가 데이터(FacilitatorReport)

extract.py와 동일하게 Structured Outputs로 스키마를 강제한다.
회의록(무엇이 결정/할 일이었는가)이 아니라 회의 "진행 방식"을 평가하는 관점.
"""

import os
from pathlib import Path

from openai import OpenAI

from ..common.schema import TranscriptSegment
from ..common.text import format_transcript
from .participation import ParticipationStat
from .schema import FacilitatorReport

client = OpenAI(
    api_key=os.environ.get("OPENAI_API_KEY"),
    base_url="https://gms.ssafy.io/gmsapi/api.openai.com/v1",
)

MODEL = os.environ.get("LLM_MODEL", "gpt-5")

SYSTEM_PROMPT = (
    Path(__file__).resolve().parents[1] / "prompts" / "facilitator_system_prompt.md"
).read_text(encoding="utf-8")


def _build_participation_context(stats: list[ParticipationStat]) -> str:
    """참여 균형 통계를 `[화자] 발화 N회, 총 M초, 비율 R%` 형태 텍스트 블록으로 변환한다."""
    lines = [
        f"[{stat.speaker}] 발화 {stat.utterance_count}회, "
        f"총 {stat.speaking_seconds:.1f}초, 비율 {stat.ratio * 100:.1f}%"
        for stat in stats
    ]
    return (
        "아래는 코드로 직접 계산한 이번 회의의 화자별 참여 균형 수치다. "
        "participation_comment는 반드시 이 수치를 근거로 서술하고, 다른 수치를 "
        "새로 만들어내지 마라.\n\n" + "\n".join(lines)
    )


def _build_previous_reports_context(previous_reports: list[str]) -> str | None:
    """임베딩 유사도로 찾은 관련 과거 Facilitator 보고서들을 프롬프트용 맥락 블록으로 만든다.

    핵심: 이번 회의 전사에 실제로 언급되어 연결되는 내용만 자연스럽게 이어서
    서술하되, 이번 회의 전사에 없는 과거 보고서 내용을 새로 지어내면 안 된다는
    지시를 명확히 한다(SYSTEM_PROMPT 규칙 4/5와 동일한 "추측 금지" 원칙).
    previous_reports가 비어 있으면(첫 회의 등) None을 반환해 기존과 동일하게 동작한다.
    """
    if not previous_reports:
        return None

    parts = [
        "아래는 이번 회의와 관련 있는 과거 Facilitator 보고서들이다. 이번 회의 전사에 "
        "실제로 연결되는 내용이 있다면(예: 과거에도 반복된 진행 문제, 지난 개선 제안의 "
        "이행 여부 등) 맥락으로 참고해 자연스럽게 이어서 서술하라. 이번 회의 전사에 "
        "없는 과거 보고서 내용을 새 보고서에 새로 지어내거나 채워 넣지 마라 — 근거는 "
        "어디까지나 이번 회의 전사여야 한다."
    ]
    for i, report in enumerate(previous_reports, start=1):
        parts.append(
            f"[과거 Facilitator 보고서 {i}]\n"
            "--- 시작 ---\n"
            f"{report}\n"
            "--- 끝 ---"
        )
    return "\n\n".join(parts)


def extract_facilitator_report(
    segments: list[TranscriptSegment],
    participation_stats: list[ParticipationStat],
    previous_reports: list[str] | None = None,
) -> FacilitatorReport:
    transcript_text = format_transcript(segments)

    messages = [{"role": "system", "content": SYSTEM_PROMPT}]
    messages.append(
        {"role": "system", "content": _build_participation_context(participation_stats)}
    )
    previous_reports_context = _build_previous_reports_context(previous_reports or [])
    if previous_reports_context is not None:
        messages.append({"role": "system", "content": previous_reports_context})
    messages.append(
        {
            "role": "user",
            "content": (
                "아래는 회의 전사 원문(화자, 타임스탬프 포함)이다. "
                "이 내용을 퍼실리테이터 관점에서 분석해서 회의 품질 평가 보고서를 채워라.\n\n"
                f"{transcript_text}"
            ),
        }
    )

    print("[추출] OpenAI API 호출 중...")
    response = client.responses.parse(
        model=MODEL,
        input=messages,
        text_format=FacilitatorReport,
    )

    if response.output_parsed is None:
        refusal = None
        for output in response.output:
            if output.type == "message":
                for content in output.content:
                    if content.type == "refusal":
                        refusal = content.refusal
        raise RuntimeError(
            "모델이 구조화된 응답을 반환하지 않았습니다. refusal: " + str(refusal)
        )

    print("[추출] 완료")
    return response.output_parsed
