"""
transcript(발화 로그) -> LLM -> 구조화된 회의록 데이터(MeetingMinutes)

핵심: 프롬프트로 "이 형식 지켜줘"라고 부탁하는 게 아니라,
Structured Outputs로 스키마를 강제합니다.
-> 모델이 스키마를 벗어난 필드/형식을 만들어낼 수가 없음.
"""

import os

from openai import OpenAI

from schema import MeetingMinutes, TranscriptSegment

client = OpenAI(
    api_key=os.environ.get("OPENAI_API_KEY"),
    base_url="https://gms.ssafy.io/gmsapi/api.openai.com/v1",
)

MODEL = "gpt-5"  # 필요에 맞게 gpt-5-mini, gpt-4o 등으로 교체 가능

SYSTEM_PROMPT = """\
너는 다년간 여러 팀의 회의를 기록해 온 회의록 작성 전문 서기다. 네가 쓴 회의록은 팀원들이
나중에 "그때 뭘 하기로 했지?"를 확인하는 유일한 근거 자료로 쓰인다. 아래 규칙을 반드시 지켜라.

1. 실제로 결론이 난 내용만 decisions에 넣어라. 논의만 하다 끝난 건 open_issues로 분류하라.
2. action_items의 assignee는 발화에서 담당자가 명시적으로 언급된 경우에만 채우고,
   그렇지 않으면 반드시 null로 두어라. 절대 추측해서 채우지 마라.
3. 모든 decisions와 action_items에는 근거가 된 발화의 타임스탬프를 반드시 포함하라.
4. summary는 3~5문장으로, 회의의 핵심 목적과 결과 중심으로 작성하라.
5. 화자 라벨(SPEAKER_00 등)은 실명이 아니므로, 굳이 실명으로 바꾸려 하지 말고 라벨 그대로 사용하라.
6. transcript에 없는 내용을 지어내지 마라. 근거가 불분명하면 포함하지 마라.

작성을 마치기 전에 아래 체크리스트로 스스로 점검하고, 어긋나는 부분이 있으면 고쳐라.
- decisions와 action_items가 서로 뒤섞이지 않고 명확히 구분되는가?
  (결론이 난 것=decisions, 결론 없이 할 일만 남은 것=action_items,
  결론도 할 일도 정해지지 않은 것=open_issues)
- topics의 각 항목이 발언자별 의견을 그냥 나열한 게 아니라, 논의가 어떻게 흘러가
  어떤 지점(합의/미합의)에 도달했는지 논리적으로 정리되어 있는가?
- 다음에 해야 할 일(task)과, 근거가 있는 경우 그 담당자(assignee)가 빠짐없이
  action_items에 명시되어 있는가?
"""

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


def _format_transcript(segments: list[TranscriptSegment]) -> str:
    lines = []
    for seg in segments:
        ts = _seconds_to_hhmmss(seg.start)
        lines.append(f"[{ts}] {seg.speaker}: {seg.text}")
    return "\n".join(lines)


def _seconds_to_hhmmss(seconds: float) -> str:
    h = int(seconds // 3600)
    m = int((seconds % 3600) // 60)
    s = int(seconds % 60)
    return f"{h:02d}:{m:02d}:{s:02d}"


def extract_meeting_minutes(
    segments: list[TranscriptSegment],
    previous_meetings: list[str] | None = None,
) -> MeetingMinutes:
    transcript_text = _format_transcript(segments)

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
