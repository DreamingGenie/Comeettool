"""전사 텍스트 포맷 유틸 (회의록/Facilitator 보고서 공용)"""

from .schema import TranscriptSegment


def format_transcript(segments: list[TranscriptSegment]) -> str:
    lines = []
    for seg in segments:
        ts = seconds_to_hhmmss(seg.start)
        lines.append(f"[{ts}] {seg.speaker}: {seg.text}")
    return "\n".join(lines)


def seconds_to_hhmmss(seconds: float) -> str:
    h = int(seconds // 3600)
    m = int((seconds % 3600) // 60)
    s = int(seconds % 60)
    return f"{h:02d}:{m:02d}:{s:02d}"
