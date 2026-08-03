"""전사 세그먼트 스키마 (회의록/Facilitator 보고서 공용)"""

from pydantic import BaseModel


class TranscriptSegment(BaseModel):
    """STT 결과 한 줄 (발화 단위)"""
    speaker: str  # LiveKit egress의 participant_id (폴더명), 실명 아님
    start: float  # 초 단위
    end: float
    text: str
