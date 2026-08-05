"""
구조화 데이터(MeetingMinutes) -> RAG 히스토리 저장용 plain text 직렬화

render.py(MD 렌더링)는 서비스 응답이 DB(JSONB)에서 바로 나가게 되면서 더 이상
파이프라인에서 호출되지 않는다. 다만 rag.py가 meeting_history에 저장할 때는
여전히 "이번 회의록 내용"을 문자열 하나로 만들어 임베딩해야 하므로, 그 목적만을
위한 최소한의 텍스트 직렬화를 여기서 만든다. MD 포맷/템플릿과는 무관하다.
"""

from .schema import MeetingMinutes


def build_minutes_history_text(minutes: MeetingMinutes) -> str:
    lines = [f"회의록: {minutes.title}", "", "요약", minutes.summary]

    lines.append("")
    lines.append("주요 논의사항")
    if minutes.topics:
        for i, topic in enumerate(minutes.topics, start=1):
            lines.append(f"{i}. {topic.title}: {topic.summary}")
    else:
        lines.append("(없음)")

    lines.append("")
    lines.append("결정사항")
    if minutes.decisions:
        for d in minutes.decisions:
            lines.append(f"- {d.content} ({d.timestamp})")
    else:
        lines.append("(없음)")

    lines.append("")
    lines.append("액션 아이템")
    if minutes.action_items:
        for a in minutes.action_items:
            assignee = a.assignee or "미배정"
            due_date = a.due_date or "-"
            lines.append(f"- {assignee}: {a.task} (기한: {due_date}, 근거: {a.source_timestamp})")
    else:
        lines.append("(없음)")

    lines.append("")
    lines.append("미해결/보류 사항")
    if minutes.open_issues:
        for issue in minutes.open_issues:
            lines.append(f"- {issue}")
    else:
        lines.append("(없음)")

    return "\n".join(lines)
