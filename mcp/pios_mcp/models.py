"""Tool 구조화 출력 모델 (outputSchema / structuredContent).

백엔드 DTO 필드(camelCase)는 백엔드 변경에 깨지지 않도록 `dict[str, Any]`로 그대로 전달하고,
도구가 계산하는 메타데이터(조회 기간, 개수 등)만 명시적으로 타입을 둔다.
"""

from __future__ import annotations

from typing import Any, Optional

from pydantic import BaseModel, Field

Record = dict[str, Any]


class UserProfileResult(BaseModel):
    latest_health: Optional[Record] = Field(None, description="최신 일일 건강 지표 (HealthMetricDto)")
    latest_sleep: Optional[Record] = Field(None, description="최신 수면 기록 (SleepDto)")
    latest_activity: Optional[Record] = Field(None, description="최신 활동 (ActivityDto)")
    total_activities: Optional[int] = None
    recent_insights: list[Record] = Field(default_factory=list)
    suggested_questions: list[str] = Field(default_factory=list)
    latest_briefing: Optional[Record] = None
    active_goals: list[Record] = Field(default_factory=list)


class ActivityListResult(BaseModel):
    start_date: str
    end_date: str
    activity_type: Optional[str] = None
    page: int
    size: int
    total_elements: int = 0
    total_pages: int = 0
    activities: list[Record] = Field(default_factory=list, description="ActivityDto 목록")


class ActivityDetailResult(BaseModel):
    activity: Record = Field(description="ActivityDto")


class ActivityLapsResult(BaseModel):
    activity_id: int
    lap_count: int
    laps: list[Record] = Field(default_factory=list, description="GarminActivityLapDto 목록")


class HealthMetricsResult(BaseModel):
    start_date: str
    end_date: str
    count: int
    metrics: list[Record] = Field(default_factory=list, description="HealthMetricDto 목록 (일 단위)")


class SleepResult(BaseModel):
    start_date: str
    end_date: str
    count: int
    sleeps: list[Record] = Field(default_factory=list, description="SleepDto 목록")


class GoalsResult(BaseModel):
    count: int
    goals: list[Record] = Field(default_factory=list, description="GoalDto 목록 (진행률 포함)")


class InsightsResult(BaseModel):
    total: int = Field(description="조건에 맞는 전체 인사이트 수")
    returned: int = Field(description="이번 응답에 포함된 수 (limit 적용)")
    insights: list[Record] = Field(default_factory=list, description="InsightDto 목록")


class AskCoachResult(BaseModel):
    question: str
    answer: str
    intent: Optional[str] = None
    question_id: Optional[int] = None
    insight_id: Optional[int] = None
    period: Optional[Record] = None
    confidence: Optional[Record] = None
    evidences: list[Record] = Field(default_factory=list)
    follow_up_questions: list[str] = Field(default_factory=list)


class GraphResult(BaseModel):
    days: int
    view: str
    race_category: Optional[str] = None
    node_count: int
    relationship_count: int
    truncated: bool = Field(description="구조화 출력에서 노드/관계 일부가 생략되었는지 여부")
    nodes: list[Record] = Field(default_factory=list)
    relationships: list[Record] = Field(default_factory=list)


class GoalWriteResult(BaseModel):
    action: str = Field(description="created | updated")
    goal: Record


class InsightActionResult(BaseModel):
    action: str = Field(description="saved | feedback")
    insight: Record


class SyncResult(BaseModel):
    sync_log: Record = Field(description="SyncLogDto")


class BriefingResult(BaseModel):
    briefing: Record = Field(description="주간 브리핑 InsightDto")
