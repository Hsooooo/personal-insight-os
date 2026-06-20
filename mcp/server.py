"""
PIOS Coach MCP Server

Personal Insight OS의 건강/욏동/수면 데이터를 외부 LLM(Claude, Copilot, Kimi 등)이
읽을 수 있도록 제공하는 Model Context Protocol (MCP) 서버입니다.

생체스포츠 코치(Bio-Sports Coach) 페르소나를 가지며,
사용자의 Garmin 데이터를 기반으로 훈련/회복/수면 인사이트를 제공합니다.

실행 방법:
    # stdio transport (Kimi Code CLI, Claude Desktop 등)
    PIOS_API_URL=http://localhost:8080/api PIOS_API_KEY=<jwt_token> \
        python mcp/server.py --transport stdio

    # streamable-http transport
    PIOS_API_URL=http://localhost:8080/api PIOS_API_KEY=<jwt_token> \
        python mcp/server.py --transport streamable-http --host 0.0.0.0 --port 8001
"""

import argparse
import contextlib
import contextvars
import os
from typing import Optional
from datetime import datetime, timedelta

from mcp.server.fastmcp import FastMCP
import httpx

APP_NAME = "pios-coach-mcp"
BASE_URL = os.getenv("PIOS_API_URL", "http://localhost:8080/api")
ENV_API_KEY = os.getenv("PIOS_API_KEY", "")

# HTTP transport 요청에서 추출한 API 키 (contextvars로 스레드/코루틴 안전)
api_key_var = contextvars.ContextVar("api_key", default="")
client_ip_var = contextvars.ContextVar("client_ip", default=None)


def _get_headers(tool_name=None):
    headers = {"Content-Type": "application/json"}
    api_key = api_key_var.get() or ENV_API_KEY
    if api_key:
        # X-API-Key 형식이면 그대로, 아니면 Bearer JWT로 가정
        if api_key.startswith("pios_"):
            headers["X-API-Key"] = api_key
        else:
            headers["Authorization"] = f"Bearer {api_key}"
    client_ip = client_ip_var.get()
    if client_ip:
        headers["X-MCP-Client-IP"] = client_ip
    if tool_name:
        headers["X-MCP-Tool"] = tool_name
    return headers


def _format_date(d: Optional[str]) -> Optional[str]:
    if not d:
        return None
    try:
        datetime.strptime(d, "%Y-%m-%d")
        return d
    except ValueError:
        return None


def _default_date_range(days: int = 7):
    end = datetime.now().date()
    start = end - timedelta(days=days)
    return start.isoformat(), end.isoformat()


def register_tools(mcp: FastMCP):
    @mcp.tool()
    async def pios_get_user_profile() -> str:
        """사용자의 최근 건강/욏동/수면 요약 프로필을 조회합니다.

        대시보드 요약 정보(최신 건강 지표, 최근 수면, 최근 활동, 인사이트 등)를 반환합니다.
        사용자의 전반적인 컨디션을 빠르게 파악할 때 사용하세요.
        """
        async with httpx.AsyncClient(base_url=BASE_URL, timeout=30.0) as client:
            resp = await client.get(
                "/dashboard/summary",
                headers=_get_headers("pios_get_user_profile"),
            )
            resp.raise_for_status()
            payload = resp.json()
            if not payload.get("success"):
                return f"❌ API 오류: {payload.get('message', '알 수 없는 오류')}"

            data = payload.get("data", {})
            lines = ["📊 사용자 프로필 요약\n"]

            latest_health = data.get("latestHealth")
            if latest_health:
                lines.append(
                    f"🫀 최신 건강 지표 ({latest_health.get('metricDate')}):\n"
                    f"   - 안정시 심박수: {latest_health.get('restingHeartRate', '-')} bpm\n"
                    f"   - HRV 평균: {latest_health.get('hrvAvg', '-')}\n"
                    f"   - 스트레스 평균: {latest_health.get('stressAvg', '-')}\n"
                    f"   - 바디 배터리: {latest_health.get('bodyBatteryMin', '-')}~{latest_health.get('bodyBatteryMax', '-')}\n"
                    f"   - 걸음수: {latest_health.get('steps', '-')} | 칼로리: {latest_health.get('caloriesTotal', '-')} kcal\n"
                )

            latest_sleep = data.get("latestSleep")
            if latest_sleep:
                total = latest_sleep.get("totalSleepSeconds", 0)
                hours, mins = divmod(total // 60, 60)
                lines.append(
                    f"😴 최신 수면 ({latest_sleep.get('sleepDate')}):\n"
                    f"   - 총 수면: {hours}h {mins}m | 수면 점수: {latest_sleep.get('sleepScore', '-')}\n"
                )

            latest_activity = data.get("latestActivity")
            if latest_activity:
                lines.append(
                    f"🏃 최신 활동: {latest_activity.get('activityName', '-')} "
                    f"({latest_activity.get('activityType', '-')})\n"
                    f"   - 시작: {latest_activity.get('startTime', '-')} | "
                    f"심박수: {latest_activity.get('averageHeartRate', '-')} bpm\n"
                )

            total_act = data.get("totalActivities")
            if total_act is not None:
                lines.append(f"📈 총 활동 기록 수: {total_act}\n")

            insights = data.get("recentInsights", [])
            if insights:
                lines.append(f"💡 최근 인사이트 ({len(insights)}개):\n")
                for ins in insights[:3]:
                    lines.append(f"   - {ins.get('title', '-')} (신뢰도: {ins.get('confidence', '-')})\n")

            suggested = data.get("suggestedQuestions", [])
            if suggested:
                lines.append(f"❓ 추천 질문: {', '.join(suggested[:3])}\n")

            return "".join(lines)

    @mcp.tool()
    async def pios_get_activities(
        start_date: Optional[str] = None,
        end_date: Optional[str] = None,
        activity_type: Optional[str] = None,
        page: int = 0,
        size: int = 20,
    ) -> str:
        """최근 욏동/활동 목록을 조회합니다.

        활동 유형(러닝, 사이클링, 수영, 웨이트트레이닝 등)과 기간으로 필터링할 수 있습니다.
        사용자의 훈련 이력을 분석하거나 패턴을 찾을 때 사용하세요.
        """
        if not start_date or not end_date:
            start_date, end_date = _default_date_range(days=30)

        params = {
            "start": _format_date(start_date),
            "end": _format_date(end_date),
            "page": page,
            "size": size,
        }
        if activity_type:
            params["activityType"] = activity_type

        async with httpx.AsyncClient(base_url=BASE_URL, timeout=30.0) as client:
            resp = await client.get(
                "/activities",
                params={k: v for k, v in params.items() if v is not None},
                headers=_get_headers("pios_get_activities"),
            )
            resp.raise_for_status()
            payload = resp.json()
            if not payload.get("success"):
                return f"❌ API 오류: {payload.get('message', '알 수 없는 오류')}"

            data = payload.get("data", {})
            content = data.get("content", [])
            if not content:
                return "📭 해당 기간 내 활동 기록이 없습니다."

            lines = [f"🏃 활동 목록 (총 {data.get('totalElements', 0)}개)\n"]
            for act in content:
                lines.append(
                    f"- [{act.get('id')}] {act.get('activityName', '-')} ({act.get('activityType', '-')})\n"
                    f"  시작: {act.get('startTime', '-')} | "
                    f"심박수: {act.get('averageHeartRate', '-')} bpm | "
                    f"칼로리: {act.get('calories', '-')} kcal\n"
                )
            return "".join(lines)

    @mcp.tool()
    async def pios_get_activity_detail(activity_id: int) -> str:
        """특정 활동의 상세 정보를 조회합니다.

        활동 ID를 기반으로 상세 데이터(구간, 페이스, 고도, 웨이트 상세 등)를 반환합니다.
        """
        async with httpx.AsyncClient(base_url=BASE_URL, timeout=30.0) as client:
            resp = await client.get(
                f"/activities/{activity_id}",
                headers=_get_headers("pios_get_activity_detail"),
            )
            resp.raise_for_status()
            payload = resp.json()
            if not payload.get("success"):
                return f"❌ API 오류: {payload.get('message', '알 수 없는 오류')}"

            act = payload.get("data", {})
            lines = [
                f"🏃 활동 상세: {act.get('activityName', '-')}\n",
                f"- 유형: {act.get('activityType', '-')}\n",
                f"- 시작: {act.get('startTime', '-')}\n",
                f"- 평균 심박수: {act.get('averageHeartRate', '-')} bpm | 최대: {act.get('maxHeartRate', '-')} bpm\n",
                f"- 칼로리: {act.get('calories', '-')} kcal\n",
            ]
            dist = act.get("distanceMeters")
            if dist:
                lines.append(f"- 거리: {dist} m\n")
            pace = act.get("averagePaceSeconds")
            if pace:
                pace_sec = float(pace)
                m = int(pace_sec) // 60
                s = int(pace_sec) % 60
                lines.append(f"- 평균 페이스: {m}:{s:02d} /km\n")
            elev = act.get("elevationGainMeters")
            if elev:
                lines.append(f"- 고도 상승: {elev} m\n")
            tag = act.get("userTag")
            if tag:
                lines.append(f"- 사용자 태그: {tag}\n")
            wt = act.get("weightTrainingDetail")
            if wt:
                lines.append(f"- 웨이트 상세: {wt}\n")
            return "".join(lines)

    @mcp.tool()
    async def pios_get_activity_laps(activity_id: int) -> str:
        """특정 활동의 구간(lap/split) 데이터를 조회합니다.

        각 구간별 거리, 시간, 페이스, 심박수 등을 반환합니다.
        훈련 구간 분석이나 인터벌 세션 리뷰에 유용합니다.
        """
        async with httpx.AsyncClient(base_url=BASE_URL, timeout=30.0) as client:
            resp = await client.get(
                f"/activities/{activity_id}/laps",
                headers=_get_headers("pios_get_activity_laps"),
            )
            resp.raise_for_status()
            payload = resp.json()
            if not payload.get("success"):
                return f"❌ API 오류: {payload.get('message', '알 수 없는 오류')}"

            laps = payload.get("data", [])
            if not laps:
                return "📭 이 활동에는 구간(lap) 데이터가 없습니다."

            lines = [f"🏃 구간 데이터 ({len(laps)}개)\n"]
            for lap in laps:
                idx = lap.get("lapIndex", "-")
                duration = lap.get("durationSeconds", 0)
                dur_m, dur_s = divmod(duration, 60) if duration else (0, 0)
                dist = lap.get("distanceMeters")
                pace = lap.get("averagePaceSeconds")
                pace_str = "-"
                if pace:
                    pace_sec = float(pace)
                    pm = int(pace_sec) // 60
                    ps = int(pace_sec) % 60
                    pace_str = f"{pm}:{ps:02d} /km"
                hr = lap.get("averageHeartRate", "-")
                max_hr = lap.get("maxHeartRate", "-")
                lines.append(
                    f"\n[구간 {idx}]\n"
                    f"  시간: {dur_m}m {dur_s}s | "
                    f"거리: {dist} m | "
                    f"페이스: {pace_str}\n"
                    f"  심박수: 평균 {hr} bpm | 최대 {max_hr} bpm\n"
                )
            return "".join(lines)

    @mcp.tool()
    async def pios_get_health_metrics(
        start_date: Optional[str] = None,
        end_date: Optional[str] = None,
    ) -> str:
        """지정 기간의 건강 지표(심박수, HRV, 스트레스, 걸음수, 칼로리 등)를 조회합니다.

        회복 상태와 일일 컨디션 추이를 분석할 때 사용하세요.
        날짜 미지정 시 최근 7일 데이터를 조회합니다.
        """
        if not start_date or not end_date:
            start_date, end_date = _default_date_range(days=7)

        async with httpx.AsyncClient(base_url=BASE_URL, timeout=30.0) as client:
            resp = await client.get(
                "/health/metrics",
                params={
                    "start": _format_date(start_date),
                    "end": _format_date(end_date),
                },
                headers=_get_headers("pios_get_health_metrics"),
            )
            resp.raise_for_status()
            payload = resp.json()
            if not payload.get("success"):
                return f"❌ API 오류: {payload.get('message', '알 수 없는 오류')}"

            metrics = payload.get("data", [])
            if not metrics:
                return "📭 해당 기간 내 건강 지표가 없습니다."

            lines = [f"🫀 건강 지표 ({start_date} ~ {end_date})\n"]
            for m in metrics:
                lines.append(
                    f"- {m.get('metricDate')}: "
                    f"RHR {m.get('restingHeartRate', '-')} bpm | "
                    f"HRV {m.get('hrvAvg', '-')} | "
                    f"Stress {m.get('stressAvg', '-')} | "
                    f"Steps {m.get('steps', '-')} | "
                    f"Kcal {m.get('caloriesTotal', '-')} | "
                    f"BB {m.get('bodyBatteryMin', '-')}-{m.get('bodyBatteryMax', '-')}\n"
                )
            return "".join(lines)

    @mcp.tool()
    async def pios_get_sleep(
        start_date: Optional[str] = None,
        end_date: Optional[str] = None,
    ) -> str:
        """지정 기간의 수면 기록을 조회합니다.

        총 수면 시간, 딥슬립, 렘슬립, 각성 시간, 수면 점수 등을 반환합니다.
        회복 품질과 수면 패턴을 분석할 때 사용하세요.
        날짜 미지정 시 최근 7일 데이터를 조회합니다.
        """
        if not start_date or not end_date:
            start_date, end_date = _default_date_range(days=7)

        async with httpx.AsyncClient(base_url=BASE_URL, timeout=30.0) as client:
            resp = await client.get(
                "/health/sleep",
                params={
                    "start": _format_date(start_date),
                    "end": _format_date(end_date),
                },
                headers=_get_headers("pios_get_sleep"),
            )
            resp.raise_for_status()
            payload = resp.json()
            if not payload.get("success"):
                return f"❌ API 오류: {payload.get('message', '알 수 없는 오류')}"

            sleeps = payload.get("data", [])
            if not sleeps:
                return "📭 해당 기간 내 수면 기록이 없습니다."

            lines = [f"😴 수면 기록 ({start_date} ~ {end_date})\n"]
            for s in sleeps:
                total = s.get("totalSleepSeconds", 0)
                hours, mins = divmod(total // 60, 60)
                deep = s.get("deepSleepSeconds", 0) // 60
                rem = s.get("remSleepSeconds", 0) // 60
                light = s.get("lightSleepSeconds", 0) // 60
                awake = s.get("awakeSeconds", 0) // 60
                lines.append(
                    f"- {s.get('sleepDate')}: "
                    f"총 {hours}h{mins}m | "
                    f"딥 {deep}m | 렘 {rem}m | 라이트 {light}m | 각성 {awake}m | "
                    f"점수 {s.get('sleepScore', '-')}\n"
                )
            return "".join(lines)

    @mcp.tool()
    async def pios_get_goals() -> str:
        """사용자가 설정한 목표 목록을 조회합니다.

        훈련, 회복, 수면 등 각 영역의 목표와 현재 진행 상황을 확인할 때 사용하세요.
        """
        async with httpx.AsyncClient(base_url=BASE_URL, timeout=30.0) as client:
            resp = await client.get(
                "/goals",
                headers=_get_headers("pios_get_goals"),
            )
            resp.raise_for_status()
            payload = resp.json()
            if not payload.get("success"):
                return f"❌ API 오류: {payload.get('message', '알 수 없는 오류')}"

            goals = payload.get("data", [])
            if not goals:
                return "📭 등록된 목표가 없습니다."

            lines = [f"🎯 목표 목록 ({len(goals)}개)\n"]
            for g in goals:
                lines.append(
                    f"- [{g.get('id')}] {g.get('title', '-')} ({g.get('goalType', '-')})\n"
                    f"  상태: {g.get('status', '-')} | "
                    f"목표: {g.get('targetValue', '-')} {g.get('targetUnit', '')} | "
                    f"기간: {g.get('startDate', '-')} ~ {g.get('targetDate', '-')}\n"
                    f"  설명: {g.get('description', '없음')}\n"
                )
            return "".join(lines)

    @mcp.tool()
    async def pios_get_insights(
        category: Optional[str] = None,
        feedback_status: Optional[str] = None,
    ) -> str:
        """생성된 인사이트(근거 기반 분석 결과) 목록을 조회합니다.

        카테고리(health, activity, sleep 등)나 피드백 상태(CORRECT, WRONG 등)로 필터링할 수 있습니다.
        과거 분석 결과와 사용자의 피드백 이력을 확인할 때 사용하세요.
        """
        params = {}
        if category:
            params["category"] = category
        if feedback_status:
            params["feedbackStatus"] = feedback_status

        async with httpx.AsyncClient(base_url=BASE_URL, timeout=30.0) as client:
            resp = await client.get(
                "/insights",
                params=params,
                headers=_get_headers("pios_get_insights"),
            )
            resp.raise_for_status()
            payload = resp.json()
            if not payload.get("success"):
                return f"❌ API 오류: {payload.get('message', '알 수 없는 오류')}"

            insights = payload.get("data", [])
            if not insights:
                return "📭 해당 조건의 인사이트가 없습니다."

            lines = [f"💡 인사이트 목록 ({len(insights)}개)\n"]
            for ins in insights[:10]:
                lines.append(
                    f"- [{ins.get('id')}] {ins.get('title', '-')} ({ins.get('category', '-')})\n"
                    f"  요약: {ins.get('summary', '-')} | "
                    f"신뢰도: {ins.get('confidence', '-')} | "
                    f"피드백: {ins.get('feedbackStatus', '-')} | "
                    f"저장: {'✅' if ins.get('isSaved') else '❌'}\n"
                )
            return "".join(lines)

    @mcp.tool()
    async def pios_ask_coach(question: str) -> str:
        """생체스포츠 코치에게 자연어로 질문합니다.

        사용자의 건강/욏동/수면 데이터를 기반으로 RAG 파이프라인을 통해 근거 기반 답변을 생성합니다.
        복잡한 분석이나 개인화된 코칭이 필요할 때 사용하세요.
        """
        if not question or not question.strip():
            return "❌ 질문 내용을 입력해야 합니다."

        async with httpx.AsyncClient(base_url=BASE_URL, timeout=60.0) as client:
            resp = await client.post(
                "/ask",
                json={"question": question.strip()},
                headers=_get_headers("pios_ask_coach"),
            )
            resp.raise_for_status()
            payload = resp.json()
            if not payload.get("success"):
                return f"❌ API 오류: {payload.get('message', '알 수 없는 오류')}"

            data = payload.get("data", {})
            answer = data.get("answer", "")
            intent = data.get("intent", "")
            period = data.get("period", {}) or {}
            confidence = data.get("confidence", {}) or {}
            evidences = data.get("evidences", []) or []
            follow_ups = data.get("followUpQuestions", []) or []

            lines = ["🧠 코치의 답변\n\n", answer, "\n"]

            if period.get("start") and period.get("end"):
                lines.append(
                    f"\n📅 분석 기간: {period.get('start')} ~ {period.get('end')}\n"
                )
                if period.get("baselineStart") and period.get("baselineEnd"):
                    lines.append(
                        f"📊 기준선 기간: {period.get('baselineStart')} ~ {period.get('baselineEnd')}\n"
                    )

            if evidences:
                lines.append("\n📎 근거:\n")
                for e in evidences:
                    label = e.get("label", "")
                    observation = e.get("observation", "")
                    comparison = e.get("comparison", "")
                    current = e.get("currentValue")
                    baseline = e.get("baselineValue")
                    change = e.get("changeRate")
                    unit = e.get("unit", "")
                    lines.append(f"- {label}: {observation} ({comparison})\n")
                    if current is not None and baseline is not None:
                        lines.append(f"    현재 {current}{unit} / 기준선 {baseline}{unit}")
                        if change is not None:
                            direction = "증가" if change > 0 else "감소"
                            lines.append(f" / {abs(change):.1f}% {direction}")
                        lines.append("\n")

            if confidence:
                score = confidence.get("score")
                level = confidence.get("level", "")
                reasons = confidence.get("reasons", []) or []
                score_str = f" ({score * 100:.0f}%)" if score is not None else ""
                lines.append(f"\n🎯 신뢰도: {level}{score_str}\n")
                if reasons:
                    lines.append("  판정 이유:\n")
                    for r in reasons:
                        lines.append(f"  - {r}\n")

            if follow_ups:
                lines.append("\n💡 추가 질문:\n")
                for q in follow_ups:
                    lines.append(f"- {q}\n")

            if intent:
                lines.append(f"\n🏷️ 의도: {intent}\n")

            return "".join(lines)

    @mcp.tool()
    async def pios_get_graph(
        days: int = 30,
        view: str = "all",
        race_category: Optional[str] = None,
    ) -> str:
        """개인 지식 그래프(도메인 노드와 관계)를 조회합니다.

        활동, 수면, 건강 지표 등이 어떻게 연결되어 있는지 그래프 구조로 확인할 때 사용하세요.
        """
        params = {"days": days, "view": view}
        if race_category:
            params["raceCategory"] = race_category

        async with httpx.AsyncClient(base_url=BASE_URL, timeout=30.0) as client:
            resp = await client.get(
                "/graph",
                params=params,
                headers=_get_headers("pios_get_graph"),
            )
            resp.raise_for_status()
            payload = resp.json()
            if not payload.get("success"):
                return f"❌ API 오류: {payload.get('message', '알 수 없는 오류')}"

            data = payload.get("data", {})
            nodes = data.get("nodes", [])
            rels = data.get("relationships", [])
            if not nodes:
                return "📭 그래프 데이터가 없습니다."

            lines = [f"🕸️ 개인 지식 그래프 (노드 {len(nodes)}개, 관계 {len(rels)}개)\n"]
            lines.append("\n[노드]\n")
            for n in nodes[:20]:
                lines.append(
                    f"- {n.get('label', '-')} ({n.get('type', '-')}) | ID: {n.get('id', '-')}\n"
                )
            if len(nodes) > 20:
                lines.append(f"... 외 {len(nodes) - 20}개\n")

            lines.append("\n[관계]\n")
            for r in rels[:20]:
                lines.append(
                    f"- {r.get('sourceLabel', '-')} -> [{r.get('type', '-')}] -> {r.get('targetLabel', '-')}\n"
                )
            if len(rels) > 20:
                lines.append(f"... 외 {len(rels) - 20}개\n")

            return "".join(lines)


def main():
    parser = argparse.ArgumentParser(description="PIOS Coach MCP Server")
    parser.add_argument(
        "--transport",
        choices=["stdio", "sse", "streamable-http"],
        default="stdio",
    )
    parser.add_argument(
        "--host",
        default="0.0.0.0",
        help="HTTP/SSE transport용 호스트 (기본: 0.0.0.0)",
    )
    parser.add_argument(
        "--port",
        type=int,
        default=8001,
        help="HTTP/SSE transport용 포트 (기본: 8001)",
    )
    args = parser.parse_args()

    if args.transport == "streamable-http":
        from starlette.applications import Starlette
        from starlette.routing import Mount
        import uvicorn

        class LoggingMiddleware:
            def __init__(self, app):
                self.app = app

            async def __call__(self, scope, receive, send):
                if scope["type"] == "http":
                    method = scope.get("method", "")
                    path = scope.get("path", "")
                    headers = dict(scope.get("headers", []))
                    api_key = headers.get(b"x-api-key", b"")
                    auth = headers.get(b"authorization", b"")
                    has_key = bool(api_key or auth)
                    print(f"[MCP] >>> {method} {path} | Key={'set' if has_key else 'none'}", flush=True)

                    async def wrapped_send(message):
                        if message.get("type") == "http.response.start":
                            status = message.get("status", 0)
                            print(f"[MCP] <<< HTTP {status} {method} {path}", flush=True)
                        await send(message)

                    await self.app(scope, receive, wrapped_send)
                else:
                    await self.app(scope, receive, send)

        class APIKeyMiddleware:
            def __init__(self, app, fallback_key=""):
                self.app = app
                self.fallback_key = fallback_key

            async def __call__(self, scope, receive, send):
                if scope["type"] == "http":
                    headers = dict(scope.get("headers", []))
                    # X-API-Key 우선, 없으면 Authorization Bearer
                    api_key = headers.get(b"x-api-key", b"").decode()
                    if not api_key:
                        auth = headers.get(b"authorization", b"").decode()
                        if auth.startswith("Bearer "):
                            api_key = auth[7:]
                    client_ip = scope.get("client", (None, None))[0]
                else:
                    api_key = ""
                    client_ip = None

                api_token = api_key_var.set(api_key or self.fallback_key)
                ip_token = client_ip_var.set(client_ip)
                try:
                    await self.app(scope, receive, send)
                finally:
                    api_key_var.reset(api_token)
                    client_ip_var.reset(ip_token)

        mcp = FastMCP(
            APP_NAME,
            host=args.host,
            port=args.port,
            stateless_http=True,
            streamable_http_path="/",
        )
        register_tools(mcp)

        mcp_app = mcp.streamable_http_app()

        @contextlib.asynccontextmanager
        async def lifespan(app):
            async with mcp.session_manager.run():
                yield

        app = Starlette(routes=[Mount("/mcp", app=mcp_app)], lifespan=lifespan)
        app = APIKeyMiddleware(app, fallback_key=ENV_API_KEY)
        app = LoggingMiddleware(app)

        uvicorn.run(app, host=args.host, port=args.port)
    else:
        mcp = FastMCP(APP_NAME)
        register_tools(mcp)
        mcp.run(transport="stdio")


if __name__ == "__main__":
    main()
