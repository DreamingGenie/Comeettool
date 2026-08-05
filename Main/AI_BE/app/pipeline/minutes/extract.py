"""
transcript(발화 로그) -> LLM -> 구조화된 회의록 데이터(MeetingMinutes)

핵심: 프롬프트로 "이 형식 지켜줘"라고 부탁하는 게 아니라,
Structured Outputs로 스키마를 강제합니다.
-> 모델이 스키마를 벗어난 필드/형식을 만들어낼 수가 없음.
"""

import os
from pathlib import Path

from openai import OpenAI

from ..common.schema import TranscriptSegment
from ..common.text import format_transcript
from .schema import MeetingMinutes

client = OpenAI(
    api_key=os.environ.get("OPENAI_API_KEY"),
    base_url="https://gms.ssafy.io/gmsapi/api.openai.com/v1",
)

MODEL = os.environ.get("LLM_MODEL", "gpt-5")

SYSTEM_PROMPT = (
    Path(__file__).resolve().parents[1] / "prompts" / "minutes_system_prompt.md"
).read_text(encoding="utf-8")


def _build_previous_meetings_context(previous_meetings: list[str]) -> str | None:
    """임베딩 유사도로 찾은 관련 과거 회의록들을 프롬프트용 맥락 참고 블록으로 만든다.

    핵심: 이번 회의 전사에 실제로 언급되어 연결되는 내용만 자연스럽게 이어서
    서술하되, 이번 회의 전사에 없는 과거 회의 내용을 새로 지어내면 안 된다는
    지시를 명확히 한다(SYSTEM_PROMPT 규칙 6과 동일한 "추측 금지" 원칙).
    previous_meetings가 비어 있으면(첫 회의 등) None을 반환해 기존과 동일하게 동작한다.
    """
    if not previous_meetings:
        return None

    parts = [
        "아래는 이번 회의와 관련 있는 과거 회의록들이다. 이번 회의 전사에 실제로 "
        "언급되어 연결되는 내용이 있다면(예: 지난 회의에서 논의된 안건의 후속 논의, "
        "지난 액션 아이템의 진행 상황 언급 등) 맥락으로 참고해 자연스럽게 이어서 "
        "서술하라. 이번 회의 전사에 없는 과거 회의 내용을 새 회의록에 새로 지어내거나 "
        "채워 넣지 마라 — 근거는 어디까지나 이번 회의 전사여야 한다."
    ]
    for i, meeting in enumerate(previous_meetings, start=1):
        parts.append(
            f"[과거 회의록 {i}]\n"
            "--- 시작 ---\n"
            f"{meeting}\n"
            "--- 끝 ---"
        )
    return "\n\n".join(parts)


def extract_meeting_minutes(
    segments: list[TranscriptSegment],
    previous_meetings: list[str] | None = None,
) -> MeetingMinutes:
    transcript_text = format_transcript(segments)

    messages = [{"role": "system", "content": SYSTEM_PROMPT}]
    previous_meetings_context = _build_previous_meetings_context(previous_meetings or [])
    if previous_meetings_context is not None:
        messages.append({"role": "system", "content": previous_meetings_context})
    messages.append(
        {
            "role": "user",
            "content": (
                "아래는 회의 전사 원문(화자, 타임스탬프 포함)이다. "
                "이 내용을 분석해서 구조화된 회의록 데이터를 채워라.\n\n"
                f"{transcript_text}"
            ),
        }
    )

    print("[추출] OpenAI API 호출 중...")
    response = client.responses.parse(
        model=MODEL,
        input=messages,
        text_format=MeetingMinutes,
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
