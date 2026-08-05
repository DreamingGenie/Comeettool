"""
회의 품질 평가(Facilitator) 보고서 구조화 데이터 스키마

schema.py의 MeetingMinutes와 같은 방식으로, OpenAI Structured Outputs에 그대로
넘겨 '이 스키마를 벗어난 응답이 나올 수 없도록' 강제하는 용도로 씁니다.
"""

from pydantic import BaseModel, Field
from typing import Optional


class QualityGrade(BaseModel):
    """진행 품질 평가 항목 하나의 등급"""
    grade: str = Field(description="등급. 반드시 '우수'/'보통'/'미흡' 중 하나")
    evidence_timestamp: str = Field(description="근거가 된 발화의 타임스탬프 (예: 00:12:34)")


class QualityEvaluation(BaseModel):
    """진행 품질 평가 5개 항목. 리스트가 아닌 고정 필드로 강제해 항목 누락을 방지한다."""

    agenda_clarity: QualityGrade = Field(description="아젠다/목적 명확성")
    time_management: QualityGrade = Field(description="시간 관리")
    speaking_opportunity_balance: QualityGrade = Field(description="발언 기회 분배")
    decision_process: QualityGrade = Field(description="의사결정 프로세스")
    discussion_focus: QualityGrade = Field(description="논의 집중도")


class Strength(BaseModel):
    content: str = Field(description="잘된 점 설명")
    timestamp: str = Field(description="근거 시각")


class Improvement(BaseModel):
    issue: str = Field(description="문제점")
    timestamp: str = Field(description="근거 시점")
    suggestion: str = Field(description="구체적 개선 제언")


class DecisionProcessCheck(BaseModel):
    decision: str = Field(description="결정 내용")
    consensus_type: str = Field(
        description="합의 방식 (예: 만장일치/다수결/특정인 일방 결정). "
        "발화에서 실제로 드러나지 않아 불분명하면 '불분명'으로 표기 (추측 금지)"
    )
    timestamp: str = Field(description="근거 시점")


class FacilitatorReport(BaseModel):
    """LLM이 최종적으로 채워야 하는 구조화 데이터. 이 스키마 밖의 필드는 생성되지 않음."""

    title: str = Field(description="회의 제목 (내용 기반으로 추론)")
    meeting_type: Optional[str] = Field(
        default=None,
        description="회의 유형 (예: 정기/긴급/기획). 전사에서 명시적으로 드러나지 않으면 반드시 null",
    )
    overall_review: str = Field(description="회의 진행 전반에 대한 총평 3~5문장")
    participation_comment: str = Field(
        description="참여 균형 표에 대한 1~2문장 코멘트. 반드시 주어진 실제 참여 균형 "
        "수치를 근거로 서술하며, 수치 자체를 새로 만들어내지 않는다"
    )
    quality_evaluation: QualityEvaluation
    strengths: list[Strength] = Field(default_factory=list)
    improvements: list[Improvement] = Field(default_factory=list)
    decision_process_checks: list[DecisionProcessCheck] = Field(default_factory=list)
    unresolved_issues_evaluation: list[str] = Field(
        default_factory=list, description="안건별 미해결 이유 + 다음 회의 우선순위 평가"
    )
    next_meeting_suggestions: list[str] = Field(
        default_factory=list, description="다음 회의를 위한 구체적 액션 제안"
    )
