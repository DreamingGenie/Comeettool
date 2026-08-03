"""
회의록 구조화 데이터 스키마

이전 대화에서 정의한 회의록 JSON 스키마를 Pydantic 모델로 옮긴 것입니다.
OpenAI Structured Outputs에 그대로 넘겨서
'이 스키마를 벗어난 응답이 나올 수 없도록' 강제하는 용도로 씁니다.
"""

from pydantic import BaseModel, Field
from typing import Optional


class Decision(BaseModel):
    content: str = Field(description="결정된 내용")
    timestamp: str = Field(description="근거가 된 발화의 타임스탬프 (예: 00:12:34)")


class ActionItem(BaseModel):
    assignee: Optional[str] = Field(
        default=None,
        description="담당자. 발화에서 명시적으로 언급되지 않았다면 반드시 null로 둘 것 (추측 금지)",
    )
    task: str = Field(description="해야 할 일")
    due_date: Optional[str] = Field(default=None, description="기한 (언급 없으면 null)")
    source_timestamp: str = Field(description="근거가 된 발화의 타임스탬프")


class Topic(BaseModel):
    title: str
    summary: str = Field(description="이 주제에 대한 논의 요약 2~3문장")


class MeetingMinutes(BaseModel):
    """LLM이 최종적으로 채워야 하는 구조화 데이터. 이 스키마 밖의 필드는 생성되지 않음."""

    title: str = Field(description="회의 제목 (내용 기반으로 추론)")
    summary: str = Field(description="전체 회의 핵심 요약 3~5문장")
    topics: list[Topic] = Field(default_factory=list)
    decisions: list[Decision] = Field(default_factory=list)
    action_items: list[ActionItem] = Field(default_factory=list)
    open_issues: list[str] = Field(
        default_factory=list, description="결론 나지 않고 보류된 안건"
    )
