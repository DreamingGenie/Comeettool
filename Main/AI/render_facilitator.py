"""
구조화 데이터(FacilitatorReport) -> 항상 동일한 형식의 MD 문서로 렌더링

render.py의 render_markdown과 동일한 패턴입니다. LLM이 아니라 이 템플릿이
문서 형식을 고정합니다.
"""

from datetime import datetime

from jinja2 import Environment, FileSystemLoader

from extract import _seconds_to_hhmmss
from participation import ParticipationStat, get_meeting_duration, get_participants
from schema import TranscriptSegment
from schema_facilitator import FacilitatorReport

env = Environment(loader=FileSystemLoader("."), trim_blocks=True, lstrip_blocks=True)
env.filters["hhmmss"] = _seconds_to_hhmmss


def render_facilitator_markdown(
    report: FacilitatorReport,
    participation_stats: list[ParticipationStat],
    transcript: list[TranscriptSegment],
) -> str:
    template = env.get_template("facilitator_report.md.j2")
    start, end = get_meeting_duration(transcript)
    return template.render(
        report=report,
        participation_stats=participation_stats,
        transcript=transcript,
        participants=get_participants(transcript),
        meeting_date=datetime.now().strftime("%Y-%m-%d"),
        start_time=_seconds_to_hhmmss(start),
        end_time=_seconds_to_hhmmss(end),
    )
