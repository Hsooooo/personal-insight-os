"""PIOS Coach MCP 도구 (15종).

각 도구는 사람이 읽기 좋은 한국어 코치 스타일 텍스트(content)와 함께
구조화 데이터(structuredContent, outputSchema는 `Annotated[CallToolResult, Model]`에서 생성)를 반환한다.
실패는 `ToolError`로 올려 SDK가 `isError: true` 결과로 변환하게 한다.
"""

import re
from datetime import date, datetime, timedelta
from typing import Annotated, Any, Literal, Optional

from mcp.server.mcpserver import MCPServer
from mcp.server.mcpserver.exceptions import ToolError
from mcp_types import CallToolResult, TextContent, ToolAnnotations
from pydantic import BaseModel, Field

from .auth import client_ip_var, current_access_token
from .backend import BackendError, PiosBackend
from .config import READ_SCOPE, WRITE_SCOPE, Settings
from .models import (
    ActivityDetailResult,
    ActivityLapsResult,
    ActivityListResult,
    AskCoachResult,
    BriefingResult,
    GoalsResult,
    GoalWriteResult,
    GraphResult,
    HealthMetricsResult,
    InsightActionResult,
    InsightsResult,
    SleepResult,
    SyncResult,
    UserProfileResult,
)

TransportMode = Literal["stdio", "http"]

_DATE_RE = re.compile(r"^\d{4}-\d{2}-\d{2}$")
MAX_HEALTH_RANGE_DAYS = 366
MAX_ACTIVITY_RANGE_DAYS = 3660
GRAPH_STRUCTURED_NODE_LIMIT = 300
GRAPH_STRUCTURED_REL_LIMIT = 600

FeedbackStatus = Literal["CORRECT", "UNCLEAR", "WRONG", "IMPORTANT"]
GraphView = Literal["all", "activities", "condition", "insights"]
SyncType = Literal["INCREMENTAL", "FULL"]

DateParam = Annotated[Optional[str], Field(description="YYYY-MM-DD (Asia/Seoul 기준 날짜)")]
IdParam = Annotated[int, Field(ge=1, description="PIOS 내부 ID")]


# ---------------------------------------------------------------------------
# 공통 헬퍼
# ---------------------------------------------------------------------------


def parse_date(name: str, value: Optional[str]) -> Optional[date]:
    """YYYY-MM-DD 검증. 빈 값은 None, 형식 오류는 조용히 버리지 않고 ToolError."""
    if value is None or str(value).strip() == "":
        return None
    text = str(value).strip()
    if not _DATE_RE.match(text):
        raise ToolError(f"❌ {name} 형식이 올바르지 않습니다: '{text}'. YYYY-MM-DD 형식으로 입력하세요 (예: 2026-03-01).")
    try:
        return date.fromisoformat(text)
    except ValueError:
        raise ToolError(f"❌ {name} 값이 존재하지 않는 날짜입니다: '{text}'.") from None


def resolve_range(
    start: Optional[str],
    end: Optional[str],
    *,
    default_days: int,
    max_days: int,
    today: date,
    start_name: str = "start_date",
    end_name: str = "end_date",
) -> tuple[date, date]:
    """조회 기간 계산. 미지정 시 오늘(설정 타임존) 포함 최근 default_days일."""
    s = parse_date(start_name, start)
    e = parse_date(end_name, end)
    if s is None and e is None:
        e = today
        s = today - timedelta(days=default_days - 1)
    elif s is None:
        assert e is not None
        s = e - timedelta(days=default_days - 1)
    elif e is None:
        e = max(today, s)
    assert s is not None and e is not None
    if s > e:
        raise ToolError(f"❌ {start_name}({s})가 {end_name}({e})보다 늦습니다.")
    if (e - s).days + 1 > max_days:
        raise ToolError(f"❌ 조회 기간이 너무 깁니다 ({(e - s).days + 1}일). 최대 {max_days}일까지 조회할 수 있습니다.")
    return s, e


def ok(text: str, model: BaseModel) -> CallToolResult:
    return CallToolResult(
        content=[TextContent(type="text", text=text)],
        structured_content=model.model_dump(mode="json"),
    )


def _get(d: Any, key: str, default: Any = "-") -> Any:
    if not isinstance(d, dict):
        return default
    value = d.get(key)
    return default if value is None else value


def _fmt_pace(seconds: Any) -> Optional[str]:
    try:
        sec = int(float(seconds))
    except (TypeError, ValueError):
        return None
    if sec <= 0:
        return None
    return f"{sec // 60}:{sec % 60:02d} /km"


def _fmt_hm(seconds: Any) -> str:
    try:
        total = int(seconds or 0)
    except (TypeError, ValueError):
        total = 0
    hours, mins = divmod(total // 60, 60)
    return f"{hours}h {mins}m"


def _minutes(seconds: Any) -> int:
    try:
        return int(seconds or 0) // 60
    except (TypeError, ValueError):
        return 0


def _fmt_seconds(value: Any) -> str:
    """초 → H:MM:SS (레이스 기록 표시용)"""
    try:
        total = int(round(float(value)))
    except (TypeError, ValueError):
        return "-"
    h, rem = divmod(total, 3600)
    m, sec = divmod(rem, 60)
    return f"{h}:{m:02d}:{sec:02d}" if h else f"{m}:{sec:02d}"


def _as_list(data: Any) -> list[dict[str, Any]]:
    if isinstance(data, list):
        return [x for x in data if isinstance(x, dict)]
    return []


def _as_dict(data: Any) -> dict[str, Any]:
    return data if isinstance(data, dict) else {}


READ_ONLY = dict(read_only_hint=True, destructive_hint=False, idempotent_hint=True, open_world_hint=False)


def _ann(title: str, **hints: bool) -> ToolAnnotations:
    return ToolAnnotations(title=title, **hints)


# ---------------------------------------------------------------------------
# Runtime (자격증명 + 백엔드 호출)
# ---------------------------------------------------------------------------


class ToolRuntime:
    def __init__(self, settings: Settings, backend: PiosBackend, transport: TransportMode):
        self.settings = settings
        self.backend = backend
        self.transport = transport

    def today(self) -> date:
        return datetime.now(self.settings.tz).date()

    def credentials(self, *, write: bool) -> str:
        if self.transport == "http":
            token = current_access_token()
            if token is None:
                # RequireAuthMiddleware 뒤에서만 도구가 실행되므로 정상 흐름에서는 발생하지 않는다.
                raise ToolError("🔐 인증 정보가 없습니다. PIOS 계정으로 다시 연결하세요.")
            if READ_SCOPE not in token.scopes:
                raise ToolError(f"⛔ 이 연결에는 {READ_SCOPE} 권한이 없습니다.")
            if write and WRITE_SCOPE not in token.scopes:
                raise ToolError(
                    f"⛔ 이 작업은 데이터를 변경하므로 {WRITE_SCOPE} 권한이 필요합니다. "
                    f"MCP 클라이언트에서 PIOS 연결을 해제한 뒤 쓰기 권한을 포함해 다시 승인하세요."
                )
            return token.token
        if not self.settings.stdio_api_key:
            raise ToolError(
                "🔑 PIOS_API_KEY 환경 변수가 설정되지 않았습니다. PIOS 웹 → MCP 설정에서 API 키(pios_...)를 발급해 설정하세요."
            )
        return self.settings.stdio_api_key

    async def call(
        self,
        tool: str,
        method: str,
        path: str,
        *,
        write: bool = False,
        params: Optional[dict[str, Any]] = None,
        json: Any = None,
        timeout: Optional[float] = None,
    ) -> Any:
        token = self.credentials(write=write)
        try:
            return await self.backend.request(
                method,
                path,
                token=token,
                tool=tool,
                client_ip=client_ip_var.get(),
                params=params,
                json=json,
                timeout=timeout,
            )
        except BackendError as exc:
            raise ToolError(exc.message) from None


# ---------------------------------------------------------------------------
# 도구 등록
# ---------------------------------------------------------------------------


def register_tools(mcp: MCPServer, rt: ToolRuntime) -> None:  # noqa: C901 - 도구 나열
    # 1 ---------------------------------------------------------------------
    @mcp.tool(
        title="사용자 프로필 요약",
        annotations=_ann("사용자 프로필 요약", **READ_ONLY),
    )
    async def pios_get_user_profile() -> Annotated[CallToolResult, UserProfileResult]:
        """사용자의 최근 건강/운동/수면 요약 프로필을 조회합니다.

        대시보드 요약 정보(최신 건강 지표, 최근 수면, 최근 활동, 인사이트, 진행 중 목표 등)를 반환합니다.
        사용자의 전반적인 컨디션을 빠르게 파악할 때 가장 먼저 사용하세요.
        """
        data = _as_dict(await rt.call("pios_get_user_profile", "GET", "/dashboard/summary"))
        lines = ["📊 사용자 프로필 요약\n"]

        h = data.get("latestHealth")
        if isinstance(h, dict):
            lines.append(
                f"🫀 최신 건강 지표 ({_get(h, 'metricDate')}):\n"
                f"   - 안정시 심박수: {_get(h, 'restingHeartRate')} bpm\n"
                f"   - HRV 평균: {_get(h, 'hrvAvg')}\n"
                f"   - 스트레스 평균: {_get(h, 'stressAvg')}\n"
                f"   - 바디 배터리: {_get(h, 'bodyBatteryMin')}~{_get(h, 'bodyBatteryMax')}\n"
                f"   - 걸음수: {_get(h, 'steps')} | 칼로리: {_get(h, 'caloriesTotal')} kcal\n"
            )
        s = data.get("latestSleep")
        if isinstance(s, dict):
            lines.append(
                f"😴 최신 수면 ({_get(s, 'sleepDate')}):\n"
                f"   - 총 수면: {_fmt_hm(s.get('totalSleepSeconds'))} | 수면 점수: {_get(s, 'sleepScore')}\n"
            )
        a = data.get("latestActivity")
        if isinstance(a, dict):
            lines.append(
                f"🏃 최신 활동: {_get(a, 'activityName')} ({_get(a, 'activityType')})\n"
                f"   - 시작: {_get(a, 'startTime')} | 심박수: {_get(a, 'averageHeartRate')} bpm\n"
            )
        if data.get("totalActivities") is not None:
            lines.append(f"📈 총 활동 기록 수: {data.get('totalActivities')}\n")
        goals = _as_list(data.get("activeGoals"))
        if goals:
            lines.append(f"🎯 진행 중 목표 ({len(goals)}개):\n")
            for g in goals[:5]:
                lines.append(f"   - {_get(g, 'title')} — 진행 {_get(g, 'progressPercent')}%\n")
        insights = _as_list(data.get("recentInsights"))
        if insights:
            lines.append(f"💡 최근 인사이트 ({len(insights)}개):\n")
            for ins in insights[:3]:
                lines.append(f"   - {_get(ins, 'title')} (신뢰도: {_get(ins, 'confidence')})\n")
        suggested = [q for q in (data.get("suggestedQuestions") or []) if isinstance(q, str)]
        if suggested:
            lines.append(f"❓ 추천 질문: {', '.join(suggested[:3])}\n")

        total = data.get("totalActivities")
        return ok(
            "".join(lines),
            UserProfileResult(
                latest_health=h if isinstance(h, dict) else None,
                latest_sleep=s if isinstance(s, dict) else None,
                latest_activity=a if isinstance(a, dict) else None,
                total_activities=int(total) if isinstance(total, (int, float)) else None,
                recent_insights=insights,
                suggested_questions=suggested,
                latest_briefing=data.get("latestBriefing") if isinstance(data.get("latestBriefing"), dict) else None,
                active_goals=goals,
            ),
        )

    # 2 ---------------------------------------------------------------------
    @mcp.tool(title="활동 목록", annotations=_ann("활동 목록", **READ_ONLY))
    async def pios_get_activities(
        start_date: DateParam = None,
        end_date: DateParam = None,
        activity_type: Annotated[
            Optional[str], Field(max_length=64, description="예: running, cycling, swimming, strength_training")
        ] = None,
        page: Annotated[int, Field(ge=0, le=10000, description="0부터 시작하는 페이지 번호")] = 0,
        size: Annotated[int, Field(ge=1, le=100, description="페이지 크기 (1~100)")] = 20,
    ) -> Annotated[CallToolResult, ActivityListResult]:
        """운동/활동 목록을 최신순으로 조회합니다.

        활동 유형(러닝, 사이클링, 수영, 웨이트트레이닝 등)과 기간으로 필터링할 수 있습니다.
        날짜 미지정 시 최근 30일(Asia/Seoul 기준)을 조회합니다. 훈련 이력 분석이나 패턴 탐색에 사용하세요.
        """
        s, e = resolve_range(
            start_date, end_date, default_days=30, max_days=MAX_ACTIVITY_RANGE_DAYS, today=rt.today()
        )
        params = {
            "startTimeFrom": s.isoformat(),
            "startTimeTo": e.isoformat(),
            "activityType": activity_type.strip() if activity_type and activity_type.strip() else None,
            "page": page,
            "size": size,
        }
        data = _as_dict(await rt.call("pios_get_activities", "GET", "/activities", params=params))
        content = _as_list(data.get("content"))
        total = int(data.get("totalElements") or 0)
        result = ActivityListResult(
            start_date=s.isoformat(),
            end_date=e.isoformat(),
            activity_type=params["activityType"],
            page=page,
            size=size,
            total_elements=total,
            total_pages=int(data.get("totalPages") or 0),
            activities=content,
        )
        if not content:
            return ok(f"📭 {s} ~ {e} 기간에 활동 기록이 없습니다.", result)

        lines = [f"🏃 활동 목록 ({s} ~ {e}, 총 {total}개 중 {len(content)}개, page {page})\n"]
        for act in content:
            extra = []
            dist = act.get("distanceMeters")
            if dist:
                extra.append(f"거리: {dist} m")
            pace = _fmt_pace(act.get("averagePaceSeconds"))
            if pace:
                extra.append(f"페이스: {pace}")
            lines.append(
                f"- [{_get(act, 'id')}] {_get(act, 'activityName')} ({_get(act, 'activityType')})\n"
                f"  시작: {_get(act, 'startTime')} | 심박수: {_get(act, 'averageHeartRate')} bpm | "
                f"칼로리: {_get(act, 'calories')} kcal" + (" | " + " | ".join(extra) if extra else "") + "\n"
            )
        return ok("".join(lines), result)

    # 3 ---------------------------------------------------------------------
    @mcp.tool(title="활동 상세", annotations=_ann("활동 상세", **READ_ONLY))
    async def pios_get_activity_detail(activity_id: IdParam) -> Annotated[CallToolResult, ActivityDetailResult]:
        """특정 활동의 상세 정보를 조회합니다.

        활동 ID를 기반으로 상세 데이터(심박, 페이스, 고도, 웨이트 상세 등)를 반환합니다.
        """
        act = _as_dict(await rt.call("pios_get_activity_detail", "GET", f"/activities/{activity_id}"))
        lines = [
            f"🏃 활동 상세: {_get(act, 'activityName')}\n",
            f"- 유형: {_get(act, 'activityType')}\n",
            f"- 시작: {_get(act, 'startTime')}\n",
            f"- 평균 심박수: {_get(act, 'averageHeartRate')} bpm | 최대: {_get(act, 'maxHeartRate')} bpm\n",
            f"- 칼로리: {_get(act, 'calories')} kcal\n",
        ]
        if act.get("distanceMeters"):
            lines.append(f"- 거리: {act['distanceMeters']} m\n")
        pace = _fmt_pace(act.get("averagePaceSeconds"))
        if pace:
            lines.append(f"- 평균 페이스: {pace}\n")
        if act.get("elevationGainMeters"):
            lines.append(f"- 고도 상승: {act['elevationGainMeters']} m\n")
        if act.get("userTag"):
            lines.append(f"- 사용자 태그: {act['userTag']}\n")
        if act.get("weightTrainingDetail"):
            lines.append(f"- 웨이트 상세: {act['weightTrainingDetail']}\n")
        return ok("".join(lines), ActivityDetailResult(activity=act))

    # 4 ---------------------------------------------------------------------
    @mcp.tool(title="활동 구간(랩) 데이터", annotations=_ann("활동 구간(랩) 데이터", **READ_ONLY))
    async def pios_get_activity_laps(activity_id: IdParam) -> Annotated[CallToolResult, ActivityLapsResult]:
        """특정 활동의 구간(lap/split) 데이터를 조회합니다.

        각 구간별 거리, 시간, 페이스, 심박수 등을 반환합니다. 인터벌 세션 리뷰나 페이스 분배 분석에 유용합니다.
        """
        laps = _as_list(await rt.call("pios_get_activity_laps", "GET", f"/activities/{activity_id}/laps"))
        result = ActivityLapsResult(activity_id=activity_id, lap_count=len(laps), laps=laps)
        if not laps:
            return ok("📭 이 활동에는 구간(lap) 데이터가 없습니다.", result)
        lines = [f"🏃 구간 데이터 ({len(laps)}개)\n"]
        for lap in laps:
            try:
                dur_m, dur_s = divmod(int(float(lap.get("durationSeconds") or 0)), 60)
            except (TypeError, ValueError):
                dur_m, dur_s = 0, 0
            lines.append(
                f"\n[구간 {_get(lap, 'lapIndex')}]\n"
                f"  시간: {dur_m}m {dur_s}s | 거리: {_get(lap, 'distanceMeters')} m | "
                f"페이스: {_fmt_pace(lap.get('averagePaceSeconds')) or '-'}\n"
                f"  심박수: 평균 {_get(lap, 'averageHeartRate')} bpm | 최대 {_get(lap, 'maxHeartRate')} bpm\n"
            )
        return ok("".join(lines), result)

    # 5 ---------------------------------------------------------------------
    @mcp.tool(title="건강 지표", annotations=_ann("건강 지표", **READ_ONLY))
    async def pios_get_health_metrics(
        start_date: DateParam = None,
        end_date: DateParam = None,
    ) -> Annotated[CallToolResult, HealthMetricsResult]:
        """지정 기간의 일일 건강 지표(안정시 심박수, HRV, 스트레스, 걸음수, 칼로리, 바디배터리 등)를 조회합니다.

        회복 상태와 일일 컨디션 추이를 분석할 때 사용하세요.
        날짜 미지정 시 오늘 포함 최근 7일(Asia/Seoul 기준), 최대 366일까지 조회합니다.
        """
        s, e = resolve_range(start_date, end_date, default_days=7, max_days=MAX_HEALTH_RANGE_DAYS, today=rt.today())
        metrics = _as_list(
            await rt.call(
                "pios_get_health_metrics",
                "GET",
                "/health/metrics",
                params={"start": s.isoformat(), "end": e.isoformat()},
            )
        )
        result = HealthMetricsResult(start_date=s.isoformat(), end_date=e.isoformat(), count=len(metrics), metrics=metrics)
        if not metrics:
            return ok(f"📭 {s} ~ {e} 기간에 건강 지표가 없습니다.", result)
        lines = [f"🫀 건강 지표 ({s} ~ {e})\n"]
        for m in metrics:
            lines.append(
                f"- {_get(m, 'metricDate')}: "
                f"RHR {_get(m, 'restingHeartRate')} bpm | HRV {_get(m, 'hrvAvg')} | "
                f"Stress {_get(m, 'stressAvg')} | Steps {_get(m, 'steps')} | "
                f"Kcal {_get(m, 'caloriesTotal')} | BB {_get(m, 'bodyBatteryMin')}-{_get(m, 'bodyBatteryMax')}\n"
            )
        return ok("".join(lines), result)

    # 6 ---------------------------------------------------------------------
    @mcp.tool(title="수면 기록", annotations=_ann("수면 기록", **READ_ONLY))
    async def pios_get_sleep(
        start_date: DateParam = None,
        end_date: DateParam = None,
    ) -> Annotated[CallToolResult, SleepResult]:
        """지정 기간의 수면 기록을 조회합니다.

        총 수면 시간, 딥/렘/라이트 수면, 각성 시간, 수면 점수 등을 반환합니다.
        회복 품질과 수면 패턴을 분석할 때 사용하세요. 날짜 미지정 시 오늘 포함 최근 7일(Asia/Seoul 기준).
        """
        s, e = resolve_range(start_date, end_date, default_days=7, max_days=MAX_HEALTH_RANGE_DAYS, today=rt.today())
        sleeps = _as_list(
            await rt.call(
                "pios_get_sleep", "GET", "/health/sleep", params={"start": s.isoformat(), "end": e.isoformat()}
            )
        )
        result = SleepResult(start_date=s.isoformat(), end_date=e.isoformat(), count=len(sleeps), sleeps=sleeps)
        if not sleeps:
            return ok(f"📭 {s} ~ {e} 기간에 수면 기록이 없습니다.", result)
        lines = [f"😴 수면 기록 ({s} ~ {e})\n"]
        for sl in sleeps:
            lines.append(
                f"- {_get(sl, 'sleepDate')}: 총 {_fmt_hm(sl.get('totalSleepSeconds'))} | "
                f"딥 {_minutes(sl.get('deepSleepSeconds'))}m | 렘 {_minutes(sl.get('remSleepSeconds'))}m | "
                f"라이트 {_minutes(sl.get('lightSleepSeconds'))}m | 각성 {_minutes(sl.get('awakeSeconds'))}m | "
                f"점수 {_get(sl, 'sleepScore')}\n"
            )
        return ok("".join(lines), result)

    # 7 ---------------------------------------------------------------------
    @mcp.tool(title="목표 목록", annotations=_ann("목표 목록", **READ_ONLY))
    async def pios_get_goals() -> Annotated[CallToolResult, GoalsResult]:
        """사용자가 설정한 목표 목록과 실제 데이터 기반 진행률을 조회합니다.

        훈련, 회복, 수면 등 각 영역의 목표와 현재 진행 상황(진행률, 페이스, 방해 요인)을 확인할 때 사용하세요.
        """
        goals = _as_list(await rt.call("pios_get_goals", "GET", "/goals"))
        result = GoalsResult(count=len(goals), goals=goals)
        if not goals:
            return ok("📭 등록된 목표가 없습니다.", result)
        lines = [f"🎯 목표 목록 ({len(goals)}개)\n"]
        for g in goals:
            progress = ""
            if g.get("progressPercent") is not None:
                progress = f" | 진행: {g['progressPercent']}% ({_get(g, 'paceStatus')})"
            if g.get("goalType") == "RACE":
                progress += (
                    f" | 거리: {_get(g, 'raceDistanceKm')}km | 목표 기록: {_fmt_seconds(g.get('targetValue'))}"
                    f" | 예상 기록: {_fmt_seconds(g.get('currentValue'))}"
                )
            lines.append(
                f"- [{_get(g, 'id')}] {_get(g, 'title')} ({_get(g, 'goalType')})\n"
                f"  상태: {_get(g, 'status')} | 목표: {_get(g, 'targetValue')} {_get(g, 'targetUnit', '')} | "
                f"기간: {_get(g, 'startDate')} ~ {_get(g, 'targetDate')}{progress}\n"
                f"  설명: {_get(g, 'description', '없음')}\n"
            )
        return ok("".join(lines), result)

    # 8 ---------------------------------------------------------------------
    @mcp.tool(title="인사이트 목록", annotations=_ann("인사이트 목록", **READ_ONLY))
    async def pios_get_insights(
        category: Annotated[
            Optional[str], Field(max_length=64, description="예: health, activity, sleep, WEEKLY_BRIEFING, ANOMALY_ALERT")
        ] = None,
        feedback_status: Optional[FeedbackStatus] = None,
        limit: Annotated[int, Field(ge=1, le=50, description="반환할 최대 개수 (1~50)")] = 10,
    ) -> Annotated[CallToolResult, InsightsResult]:
        """생성된 인사이트(근거 기반 분석 결과, 주간 브리핑, 이상 신호 알림) 목록을 조회합니다.

        카테고리나 피드백 상태(CORRECT, UNCLEAR, WRONG, IMPORTANT)로 필터링할 수 있습니다.
        과거 분석 결과와 사용자의 피드백 이력을 확인할 때 사용하세요.
        """
        params = {"category": category.strip() if category and category.strip() else None, "feedbackStatus": feedback_status}
        insights = _as_list(await rt.call("pios_get_insights", "GET", "/insights", params=params))
        shown = insights[:limit]
        result = InsightsResult(total=len(insights), returned=len(shown), insights=shown)
        if not insights:
            return ok("📭 해당 조건의 인사이트가 없습니다.", result)
        lines = [f"💡 인사이트 목록 ({len(insights)}개 중 {len(shown)}개)\n"]
        for ins in shown:
            lines.append(
                f"- [{_get(ins, 'id')}] {_get(ins, 'title')} ({_get(ins, 'category')})\n"
                f"  요약: {_get(ins, 'summary')} | 신뢰도: {_get(ins, 'confidence')} | "
                f"피드백: {_get(ins, 'feedbackStatus')} | 저장: {'✅' if ins.get('isSaved') else '❌'}\n"
            )
        return ok("".join(lines), result)

    # 9 ---------------------------------------------------------------------
    @mcp.tool(
        title="코치에게 질문",
        annotations=_ann(
            "코치에게 질문", read_only_hint=False, destructive_hint=False, idempotent_hint=False, open_world_hint=False
        ),
    )
    async def pios_ask_coach(
        question: Annotated[str, Field(min_length=1, max_length=2000, description="자연어 질문")],
    ) -> Annotated[CallToolResult, AskCoachResult]:
        """생체스포츠 코치에게 자연어로 질문합니다.

        사용자의 건강/운동/수면 데이터를 기반으로 RAG 파이프라인을 통해 근거 기반 답변을 생성합니다.
        답변은 인사이트로 저장됩니다. 복잡한 분석이나 개인화된 코칭이 필요할 때 사용하세요.
        """
        q = (question or "").strip()
        if not q:
            raise ToolError("❌ 질문 내용을 입력해야 합니다.")
        data = _as_dict(await rt.call("pios_ask_coach", "POST", "/ask", json={"question": q}, timeout=90.0))
        answer = str(data.get("answer") or "")
        period = _as_dict(data.get("period"))
        confidence = _as_dict(data.get("confidence"))
        evidences = _as_list(data.get("evidences"))
        follow_ups = [x for x in (data.get("followUpQuestions") or []) if isinstance(x, str)]
        intent = data.get("intent")

        lines = ["🧠 코치의 답변\n\n", answer, "\n"]
        if period.get("start") and period.get("end"):
            lines.append(f"\n📅 분석 기간: {period['start']} ~ {period['end']}\n")
            if period.get("baselineStart") and period.get("baselineEnd"):
                lines.append(f"📊 기준선 기간: {period['baselineStart']} ~ {period['baselineEnd']}\n")
        if evidences:
            lines.append("\n📎 근거:\n")
            for ev in evidences:
                lines.append(f"- {ev.get('label', '')}: {ev.get('observation', '')} ({ev.get('comparison', '')})\n")
                cur, base, change, unit = (
                    ev.get("currentValue"),
                    ev.get("baselineValue"),
                    ev.get("changeRate"),
                    ev.get("unit") or "",
                )
                if cur is not None and base is not None:
                    lines.append(f"    현재 {cur}{unit} / 기준선 {base}{unit}")
                    if isinstance(change, (int, float)):
                        lines.append(f" / {abs(change):.1f}% {'증가' if change > 0 else '감소'}")
                    lines.append("\n")
        if confidence:
            score = confidence.get("score")
            score_str = f" ({float(score) * 100:.0f}%)" if isinstance(score, (int, float)) else ""
            lines.append(f"\n🎯 신뢰도: {confidence.get('level', '')}{score_str}\n")
            reasons = [r for r in (confidence.get("reasons") or []) if isinstance(r, str)]
            if reasons:
                lines.append("  판정 이유:\n")
                lines.extend(f"  - {r}\n" for r in reasons)
        if follow_ups:
            lines.append("\n💡 추가 질문:\n")
            lines.extend(f"- {x}\n" for x in follow_ups)
        if intent:
            lines.append(f"\n🏷️ 의도: {intent}\n")

        def _int(v: Any) -> Optional[int]:
            return int(v) if isinstance(v, (int, float)) else None

        return ok(
            "".join(lines),
            AskCoachResult(
                question=q,
                answer=answer,
                intent=str(intent) if intent else None,
                question_id=_int(data.get("questionId")),
                insight_id=_int(data.get("insightId")),
                period=period or None,
                confidence=confidence or None,
                evidences=evidences,
                follow_up_questions=follow_ups,
            ),
        )

    # 10 --------------------------------------------------------------------
    @mcp.tool(title="개인 지식 그래프", annotations=_ann("개인 지식 그래프", **READ_ONLY))
    async def pios_get_graph(
        days: Annotated[int, Field(ge=0, le=3650, description="최근 N일 (0 = 전체 기간)")] = 30,
        view: GraphView = "all",
        race_category: Annotated[Optional[str], Field(max_length=32, description="레이스 카테고리 필터 (예: 10K, HALF, FULL)")] = None,
    ) -> Annotated[CallToolResult, GraphResult]:
        """개인 지식 그래프(도메인 노드와 관계)를 조회합니다.

        활동, 수면, 건강 지표, 목표, 인사이트 등이 어떻게 연결되어 있는지 그래프 구조로 확인할 때 사용하세요.
        view: all | activities | condition | insights
        """
        params = {"days": days, "view": view, "raceCategory": race_category.strip() if race_category else None}
        data = _as_dict(await rt.call("pios_get_graph", "GET", "/graph", params=params))
        nodes = _as_list(data.get("nodes"))
        rels = _as_list(data.get("relationships"))
        truncated = len(nodes) > GRAPH_STRUCTURED_NODE_LIMIT or len(rels) > GRAPH_STRUCTURED_REL_LIMIT
        result = GraphResult(
            days=days,
            view=view,
            race_category=params["raceCategory"],
            node_count=len(nodes),
            relationship_count=len(rels),
            truncated=truncated,
            nodes=nodes[:GRAPH_STRUCTURED_NODE_LIMIT],
            relationships=rels[:GRAPH_STRUCTURED_REL_LIMIT],
        )
        if not nodes:
            return ok("📭 그래프 데이터가 없습니다.", result)
        labels = {n.get("id"): n.get("label") or n.get("id") for n in nodes}
        lines = [f"🕸️ 개인 지식 그래프 (노드 {len(nodes)}개, 관계 {len(rels)}개)\n", "\n[노드]\n"]
        for n in nodes[:20]:
            lines.append(f"- {_get(n, 'label')} ({_get(n, 'type')}) | ID: {_get(n, 'id')}\n")
        if len(nodes) > 20:
            lines.append(f"... 외 {len(nodes) - 20}개\n")
        lines.append("\n[관계]\n")
        for r in rels[:20]:
            src = labels.get(r.get("sourceId"), r.get("sourceId", "-"))
            tgt = labels.get(r.get("targetId"), r.get("targetId", "-"))
            lines.append(f"- {src} -> [{_get(r, 'type')}] -> {tgt}\n")
        if len(rels) > 20:
            lines.append(f"... 외 {len(rels) - 20}개\n")
        return ok("".join(lines), result)

    # 11 --------------------------------------------------------------------
    @mcp.tool(
        title="목표 생성/수정",
        annotations=_ann(
            "목표 생성/수정", read_only_hint=False, destructive_hint=False, idempotent_hint=False, open_world_hint=False
        ),
    )
    async def pios_create_or_update_goal(
        title: Annotated[Optional[str], Field(max_length=200, description="목표 제목 (생성 시 필수)")] = None,
        goal_type: Annotated[
            Optional[str],
            Field(
                max_length=64,
                description="생성 시 필수. 자동 진행률 템플릿: RACE, WEEKLY_RUN_DISTANCE, WEEKLY_ACTIVITY_COUNT, SLEEP_HOURS, WEIGHT_KG (그 외 문자열은 진행률 미추적). RACE는 race_distance_km + target_value(목표 기록, 초) + target_date(대회일)",
            ),
        ] = None,
        target_value: Optional[float] = None,
        target_unit: Annotated[Optional[str], Field(max_length=32)] = None,
        race_distance_km: Annotated[
            Optional[float],
            Field(gt=0, le=400, description="RACE 목표의 대회 거리(km). 예: 하프 21.0975, 풀 42.195"),
        ] = None,
        description: Annotated[Optional[str], Field(max_length=2000)] = None,
        start_date: DateParam = None,
        target_date: DateParam = None,
        goal_id: Annotated[Optional[int], Field(ge=1, description="지정 시 해당 목표를 수정 (PATCH)")] = None,
        status: Annotated[Optional[str], Field(max_length=32, description="ACTIVE | COMPLETED | PAUSED | CANCELLED 등")] = None,
    ) -> Annotated[CallToolResult, GoalWriteResult]:
        """목표를 생성하거나 수정합니다. goal_id가 있으면 수정(PATCH), 없으면 생성(POST)합니다.

        수정 시에는 바꿀 필드만 전달하면 됩니다. 삭제는 지원하지 않으며, status로 COMPLETED/CANCELLED 등을 설정하세요.
        mcp:write 권한이 필요합니다.
        """
        sd = parse_date("start_date", start_date)
        td = parse_date("target_date", target_date)
        if sd and td and sd > td:
            raise ToolError(f"❌ start_date({sd})가 target_date({td})보다 늦습니다.")
        is_update = goal_id is not None
        if not is_update:
            if not title or not title.strip():
                raise ToolError("❌ 목표 생성 시 title은 필수입니다.")
            if not goal_type or not goal_type.strip():
                raise ToolError("❌ 목표 생성 시 goal_type은 필수입니다.")

        body: dict[str, Any] = {}
        if title and title.strip():
            body["title"] = title.strip()
        if goal_type and goal_type.strip():
            body["goalType"] = goal_type.strip()
        if target_value is not None:
            body["targetValue"] = target_value
        if target_unit:
            body["targetUnit"] = target_unit.strip()
        if race_distance_km is not None:
            body["raceDistanceKm"] = race_distance_km
        if description is not None:
            body["description"] = description
        if sd:
            body["startDate"] = sd.isoformat()
        if td:
            body["targetDate"] = td.isoformat()
        if status and status.strip():
            body["status"] = status.strip().upper()
        if is_update and not body:
            raise ToolError("❌ 수정할 필드가 없습니다.")

        if is_update:
            g = await rt.call("pios_create_or_update_goal", "PATCH", f"/goals/{goal_id}", write=True, json=body)
        else:
            g = await rt.call("pios_create_or_update_goal", "POST", "/goals", write=True, json=body)
        g = _as_dict(g)
        blockers = [b for b in (g.get("blockers") or []) if isinstance(b, str)]
        text = (
            f"✅ 목표 {'수정' if is_update else '생성'} 완료\n"
            f"- [{_get(g, 'id')}] {_get(g, 'title')} ({_get(g, 'goalType')})\n"
            f"  상태: {_get(g, 'status')} | 진행: {_get(g, 'progressPercent')}%"
            + (("\n  방해 요인: " + "; ".join(blockers)) if blockers else "")
        )
        return ok(text, GoalWriteResult(action="updated" if is_update else "created", goal=g))

    # 12 --------------------------------------------------------------------
    @mcp.tool(
        title="인사이트 저장",
        annotations=_ann(
            "인사이트 저장", read_only_hint=False, destructive_hint=False, idempotent_hint=True, open_world_hint=False
        ),
    )
    async def pios_save_insight(insight_id: IdParam) -> Annotated[CallToolResult, InsightActionResult]:
        """인사이트를 저장(즐겨찾기)합니다. mcp:write 권한이 필요합니다."""
        data = _as_dict(await rt.call("pios_save_insight", "POST", f"/insights/{insight_id}/save", write=True))
        return ok(
            f"✅ 인사이트 저장됨: [{_get(data, 'id', insight_id)}] {_get(data, 'title', '')}",
            InsightActionResult(action="saved", insight=data),
        )

    # 13 --------------------------------------------------------------------
    @mcp.tool(
        title="인사이트 피드백",
        annotations=_ann(
            "인사이트 피드백", read_only_hint=False, destructive_hint=False, idempotent_hint=True, open_world_hint=False
        ),
    )
    async def pios_submit_feedback(
        insight_id: IdParam,
        feedback_status: FeedbackStatus,
    ) -> Annotated[CallToolResult, InsightActionResult]:
        """인사이트에 피드백을 제출합니다 (CORRECT | UNCLEAR | WRONG | IMPORTANT). mcp:write 권한이 필요합니다."""
        data = _as_dict(
            await rt.call(
                "pios_submit_feedback",
                "POST",
                f"/insights/{insight_id}/feedback",
                write=True,
                json={"feedbackStatus": feedback_status},
            )
        )
        return ok(
            f"✅ 피드백 반영: [{_get(data, 'id', insight_id)}] {_get(data, 'title', '')} → "
            f"{_get(data, 'feedbackStatus', feedback_status)}",
            InsightActionResult(action="feedback", insight=data),
        )

    # 14 --------------------------------------------------------------------
    @mcp.tool(
        title="Garmin 동기화",
        annotations=_ann(
            "Garmin 동기화", read_only_hint=False, destructive_hint=False, idempotent_hint=False, open_world_hint=True
        ),
    )
    async def pios_trigger_sync(
        sync_type: SyncType = "INCREMENTAL",
        date_from: DateParam = None,
        date_to: DateParam = None,
    ) -> Annotated[CallToolResult, SyncResult]:
        """Garmin Connect 데이터 동기화를 시작합니다. 서버 rate limit(30초)이 적용됩니다.

        sync_type: INCREMENTAL(기본) | FULL. date_from/date_to(YYYY-MM-DD)로 기간을 지정할 수 있습니다.
        mcp:write 권한이 필요합니다.
        """
        df = parse_date("date_from", date_from)
        dt = parse_date("date_to", date_to)
        if df and dt and df > dt:
            raise ToolError(f"❌ date_from({df})가 date_to({dt})보다 늦습니다.")
        body: dict[str, Any] = {"syncType": sync_type}
        if df:
            body["dateFrom"] = df.isoformat()
        if dt:
            body["dateTo"] = dt.isoformat()
        log = _as_dict(
            await rt.call("pios_trigger_sync", "POST", "/data-sources/garmin/sync", write=True, json=body, timeout=120.0)
        )
        return ok(
            "✅ 동기화 요청 완료\n"
            f"- logId: {_get(log, 'id')} | type: {_get(log, 'syncType')} | status: {_get(log, 'status')}\n"
            f"- 기간: {_get(log, 'dateFrom')} ~ {_get(log, 'dateTo')}",
            SyncResult(sync_log=log),
        )

    # 15 --------------------------------------------------------------------
    @mcp.tool(
        title="주간 브리핑 생성",
        annotations=_ann(
            "주간 브리핑 생성", read_only_hint=False, destructive_hint=True, idempotent_hint=False, open_world_hint=False
        ),
    )
    async def pios_generate_briefing(
        force: Annotated[bool, Field(description="True면 같은 주의 기존 브리핑을 덮어써 재생성")] = False,
    ) -> Annotated[CallToolResult, BriefingResult]:
        """주간 브리핑을 생성합니다. force=True이면 같은 주 기존 브리핑을 재생성(대체)합니다. mcp:write 권한이 필요합니다."""
        data = _as_dict(
            await rt.call(
                "pios_generate_briefing", "POST", "/briefings/generate", write=True, json={"force": bool(force)}, timeout=120.0
            )
        )
        summary = str(data.get("summary") or "")
        if len(summary) > 800:
            summary = summary[:800] + "…"
        return ok(
            f"✅ 주간 브리핑\n- [{_get(data, 'id')}] {_get(data, 'title', '')}\n"
            f"- 신뢰도: {_get(data, 'confidence')}\n\n{summary}",
            BriefingResult(briefing=data),
        )


TOOL_NAMES = [
    "pios_get_user_profile",
    "pios_get_activities",
    "pios_get_activity_detail",
    "pios_get_activity_laps",
    "pios_get_health_metrics",
    "pios_get_sleep",
    "pios_get_goals",
    "pios_get_insights",
    "pios_ask_coach",
    "pios_get_graph",
    "pios_create_or_update_goal",
    "pios_save_insight",
    "pios_submit_feedback",
    "pios_trigger_sync",
    "pios_generate_briefing",
]
WRITE_TOOLS = {
    "pios_create_or_update_goal",
    "pios_save_insight",
    "pios_submit_feedback",
    "pios_trigger_sync",
    "pios_generate_briefing",
}
