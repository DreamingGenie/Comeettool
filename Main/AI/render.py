"""
구조화 데이터(MeetingMinutes) -> 항상 동일한 형식의 MD 문서로 렌더링

여기가 '양식 통일'을 실제로 보장하는 지점입니다.
LLM이 문서 형식을 만드는 게 아니라, 이 코드(템플릿)가 항상 같은 틀로 찍어냅니다.
"""

from datetime import datetime

from jinja2 import Environment, FileSystemLoader

from schema import MeetingMinutes, TranscriptSegment

env = Environment(loader=FileSystemLoader("."), trim_blocks=True, lstrip_blocks=True)


def render_markdown(minutes: MeetingMinutes, transcript: list[TranscriptSegment]) -> str:
    template = env.get_template("template.md.j2")
    speaker_count = len(set(seg.speaker for seg in transcript))
    return template.render(
        minutes=minutes,
        transcript=transcript,
        meeting_date=datetime.now().strftime("%Y-%m-%d %H:%M"),
        speaker_count=speaker_count,
    )
