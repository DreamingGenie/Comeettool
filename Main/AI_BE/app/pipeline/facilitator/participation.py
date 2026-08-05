"""
참여 균형 집계 (순수 계산, LLM/추출 로직 의존 없음)

transcript.json으로부터 화자별 발화 횟수/발화 시간/발화 비율을 코드로 직접 계산한다.
이 수치는 LLM이 추정하지 않고, 이후 PHASE(추출/렌더링)에서 그대로 근거로 쓰인다.
"""

from collections import defaultdict

from pydantic import BaseModel

from ..common.schema import TranscriptSegment


class ParticipationStat(BaseModel):
    """화자 한 명의 참여 통계"""
    speaker: str
    utterance_count: int  # 발화 횟수
    speaking_seconds: float  # 발화 시간 합계 (세그먼트별 end - start의 합)
    ratio: float  # 전체 화자 발화 시간 합 대비 비율


def compute_participation_stats(segments: list[TranscriptSegment]) -> list[ParticipationStat]:
    """화자별로 세그먼트를 묶어 발화 횟수/발화 시간/비율을 계산하고 비율 내림차순으로 정렬한다."""
    seconds_by_speaker: dict[str, float] = defaultdict(float)
    count_by_speaker: dict[str, int] = defaultdict(int)

    for seg in segments:
        seconds_by_speaker[seg.speaker] += seg.end - seg.start
        count_by_speaker[seg.speaker] += 1

    total_seconds = sum(seconds_by_speaker.values())

    stats = [
        ParticipationStat(
            speaker=speaker,
            utterance_count=count_by_speaker[speaker],
            speaking_seconds=seconds_by_speaker[speaker],
            ratio=(seconds_by_speaker[speaker] / total_seconds) if total_seconds > 0 else 0.0,
        )
        for speaker in seconds_by_speaker
    ]
    stats.sort(key=lambda s: s.ratio, reverse=True)
    return stats


def get_participants(segments: list[TranscriptSegment]) -> list[str]:
    """전사에 등장하는 고유 화자 라벨 목록 (첫 등장 순서)"""
    seen = []
    for seg in segments:
        if seg.speaker not in seen:
            seen.append(seg.speaker)
    return seen


def get_meeting_duration(segments: list[TranscriptSegment]) -> tuple[float, float]:
    """회의 진행 시간: 전체 세그먼트 중 최소 start ~ 최대 end를 (start, end)로 반환"""
    if not segments:
        return 0.0, 0.0
    starts = [seg.start for seg in segments]
    ends = [seg.end for seg in segments]
    return min(starts), max(ends)
