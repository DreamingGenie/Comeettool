"""
구조화 데이터(FacilitatorReport) -> RAG 히스토리 저장용 plain text 직렬화

minutes/history_text.py와 동일한 목적. facilitator_report.md.j2 렌더링은 더 이상
파이프라인에서 호출되지 않지만, facilitator_report_history에 저장할 때는 여전히
"이번 보고서 내용"을 문자열 하나로 만들어 임베딩해야 하므로 그 목적만을 위한
최소한의 텍스트 직렬화를 여기서 만든다.
"""

from .participation import ParticipationStat
from .schema import FacilitatorReport

_QUALITY_LABELS = {
    "agenda_clarity": "아젠다/목적 명확성",
    "time_management": "시간 관리",
    "speaking_opportunity_balance": "발언 기회 분배",
    "decision_process": "의사결정 프로세스",
    "discussion_focus": "논의 집중도",
}


def build_facilitator_history_text(
    report: FacilitatorReport, participation_stats: list[ParticipationStat]
) -> str:
    lines = [
        f"회의 품질 평가 보고서: {report.title}",
        f"회의 유형: {report.meeting_type or '미분류'}",
        "",
        "총평",
        report.overall_review,
    ]

    lines.append("")
    lines.append("참여 균형")
    if participation_stats:
        for stat in participation_stats:
            lines.append(
                f"- {stat.speaker}: 발화 {stat.utterance_count}회, "
                f"{stat.speaking_seconds:.1f}초, {stat.ratio * 100:.1f}%"
            )
    else:
        lines.append("(없음)")
    lines.append(report.participation_comment)

    lines.append("")
    lines.append("진행 품질 평가")
    for field, label in _QUALITY_LABELS.items():
        grade = getattr(report.quality_evaluation, field)
        lines.append(f"- {label}: {grade.grade} ({grade.evidence_timestamp})")

    lines.append("")
    lines.append("잘된 점")
    if report.strengths:
        for s in report.strengths:
            lines.append(f"- {s.content} ({s.timestamp})")
    else:
        lines.append("(없음)")

    lines.append("")
    lines.append("개선 필요 사항")
    if report.improvements:
        for imp in report.improvements:
            lines.append(f"- {imp.issue} ({imp.timestamp}): {imp.suggestion}")
    else:
        lines.append("(없음)")

    lines.append("")
    lines.append("결정 프로세스 점검")
    if report.decision_process_checks:
        for c in report.decision_process_checks:
            lines.append(f"- {c.decision} / {c.consensus_type} ({c.timestamp})")
    else:
        lines.append("(결정사항 없음)")

    lines.append("")
    lines.append("미해결 이슈 처리 평가")
    if report.unresolved_issues_evaluation:
        for issue in report.unresolved_issues_evaluation:
            lines.append(f"- {issue}")
    else:
        lines.append("(없음)")

    lines.append("")
    lines.append("다음 회의를 위한 제언")
    if report.next_meeting_suggestions:
        for i, suggestion in enumerate(report.next_meeting_suggestions, start=1):
            lines.append(f"{i}. {suggestion}")
    else:
        lines.append("(없음)")

    return "\n".join(lines)
