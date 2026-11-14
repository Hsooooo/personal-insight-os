"""도구 동작: write scope, 날짜 검증, 에러 매핑, structuredContent, 기본 기간."""

from datetime import date, datetime, timedelta
from zoneinfo import ZoneInfo

import httpx
import pytest
from conftest import READ_ONLY_TOKEN, READ_WRITE_TOKEN, call_tool, ok_json

from pios_mcp.tools import parse_date, resolve_range
from mcp.server.mcpserver.exceptions import ToolError


def _text(result) -> str:
    return "".join(c.get("text", "") for c in result["content"])


# ---- write scope -----------------------------------------------------------


def test_write_tool_requires_write_scope(client, fake):
    fake.routes[("POST", "/api/insights/5/save")] = lambda r: ok_json({"id": 5, "title": "t"})
    result = call_tool(client, "pios_save_insight", {"insight_id": 5}, token=READ_ONLY_TOKEN)
    assert result["isError"] is True
    assert "mcp:write" in _text(result)
    assert fake.api_requests() == []


def test_write_tool_with_write_scope(client, fake):
    fake.routes[("POST", "/api/insights/5/save")] = lambda r: ok_json({"id": 5, "title": "수면 패턴"})
    result = call_tool(client, "pios_save_insight", {"insight_id": 5})
    assert result.get("isError") is not True
    assert result["structuredContent"]["action"] == "saved"
    assert result["structuredContent"]["insight"]["id"] == 5
    assert "수면 패턴" in _text(result)


def test_read_tool_with_read_only_scope(client, fake):
    fake.routes[("GET", "/api/goals")] = lambda r: ok_json([{"id": 1, "title": "주 30km", "goalType": "WEEKLY_RUN_DISTANCE"}])
    result = call_tool(client, "pios_get_goals", token=READ_ONLY_TOKEN)
    assert result.get("isError") is not True
    assert result["structuredContent"]["count"] == 1
    assert "주 30km" in _text(result)


# ---- date validation -------------------------------------------------------


def test_invalid_date_returns_error_not_silently_dropped(client, fake):
    result = call_tool(client, "pios_get_sleep", {"start_date": "2026/01/01", "end_date": "2026-01-07"})
    assert result["isError"] is True
    assert "YYYY-MM-DD" in _text(result)
    assert fake.api_requests() == []


def test_impossible_date(client):
    result = call_tool(client, "pios_get_health_metrics", {"start_date": "2026-02-30"})
    assert result["isError"] is True


def test_reversed_range(client):
    result = call_tool(client, "pios_get_sleep", {"start_date": "2026-03-10", "end_date": "2026-03-01"})
    assert result["isError"] is True


def test_size_bounds_validated(client, fake):
    result = call_tool(client, "pios_get_activities", {"size": 1000})
    assert result["isError"] is True
    assert fake.api_requests() == []


def test_parse_and_resolve_helpers():
    assert parse_date("d", None) is None
    assert parse_date("d", "") is None
    assert parse_date("d", "2026-01-05") == date(2026, 1, 5)
    with pytest.raises(ToolError):
        parse_date("d", "20260105")
    today = date(2026, 3, 10)
    assert resolve_range(None, None, default_days=7, max_days=366, today=today) == (date(2026, 3, 4), today)
    assert resolve_range("2026-03-01", None, default_days=7, max_days=366, today=today) == (date(2026, 3, 1), today)
    assert resolve_range(None, "2026-02-10", default_days=7, max_days=366, today=today) == (
        date(2026, 2, 4),
        date(2026, 2, 10),
    )
    with pytest.raises(ToolError):
        resolve_range("2024-01-01", "2026-01-01", default_days=7, max_days=366, today=today)


def test_default_range_uses_seoul_timezone(client, fake):
    captured = {}

    def handler(r: httpx.Request):
        captured.update(dict(r.url.params))
        return ok_json([])

    fake.routes[("GET", "/api/health/sleep")] = handler
    result = call_tool(client, "pios_get_sleep")
    today = datetime.now(ZoneInfo("Asia/Seoul")).date()
    assert captured == {"start": (today - timedelta(days=6)).isoformat(), "end": today.isoformat()}
    assert result["structuredContent"]["end_date"] == today.isoformat()


def test_activities_use_backend_filter_param_names(client, fake):
    captured = {}

    def handler(r: httpx.Request):
        captured.update(dict(r.url.params))
        return ok_json({"content": [{"id": 9, "activityName": "아침 러닝", "activityType": "running"}], "totalElements": 1})

    fake.routes[("GET", "/api/activities")] = handler
    result = call_tool(
        client, "pios_get_activities", {"start_date": "2026-01-01", "end_date": "2026-01-31", "activity_type": "running"}
    )
    assert captured == {
        "startTimeFrom": "2026-01-01",
        "startTimeTo": "2026-01-31",
        "activityType": "running",
        "page": "0",
        "size": "20",
    }
    sc = result["structuredContent"]
    assert sc["total_elements"] == 1 and sc["activities"][0]["id"] == 9
    assert "아침 러닝" in _text(result)


# ---- error mapping ---------------------------------------------------------


@pytest.mark.parametrize(
    "status,needle",
    [
        (401, "인증"),
        (403, "권한"),
        (404, "찾을 수 없습니다"),
        (429, "rate limit"),
        (500, "HTTP 500"),
        (503, "HTTP 503"),
        (400, "HTTP 400"),
    ],
)
def test_http_error_mapping(client, fake, status, needle):
    fake.routes[("GET", "/api/activities/3")] = lambda r: httpx.Response(
        status, json={"success": False, "message": "boom"}, headers={"Retry-After": "30"}
    )
    result = call_tool(client, "pios_get_activity_detail", {"activity_id": 3})
    assert result["isError"] is True
    assert needle in _text(result)
    if status == 429:
        assert "30초" in _text(result)


def test_network_error_mapping(client, fake):
    def boom(r):
        raise httpx.ConnectError("refused", request=r)

    fake.routes[("GET", "/api/goals")] = boom
    result = call_tool(client, "pios_get_goals")
    assert result["isError"] is True
    assert "연결할 수 없습니다" in _text(result)


def test_timeout_mapping(client, fake):
    def slow(r):
        raise httpx.ReadTimeout("slow", request=r)

    fake.routes[("GET", "/api/goals")] = slow
    result = call_tool(client, "pios_get_goals")
    assert result["isError"] is True
    assert "시간이 초과" in _text(result)


def test_api_success_false_mapping(client, fake):
    fake.routes[("GET", "/api/goals")] = lambda r: httpx.Response(200, json={"success": False, "message": "잘못된 요청"})
    result = call_tool(client, "pios_get_goals")
    assert result["isError"] is True
    assert "잘못된 요청" in _text(result)


# ---- write tools body ------------------------------------------------------


def test_create_goal_requires_title_but_update_does_not(client, fake):
    seen = []

    def patch(r: httpx.Request):
        seen.append(r.content)
        return ok_json({"id": 2, "title": "x", "status": "COMPLETED"})

    fake.routes[("PATCH", "/api/goals/2")] = patch
    r1 = call_tool(client, "pios_create_or_update_goal", {"goal_type": "SLEEP_HOURS"})
    assert r1["isError"] is True
    r2 = call_tool(client, "pios_create_or_update_goal", {"goal_id": 2, "status": "completed"})
    assert r2.get("isError") is not True, r2
    assert b'"status":"COMPLETED"' in seen[0].replace(b" ", b"")
    assert r2["structuredContent"]["action"] == "updated"


def test_sync_rejects_bad_dates(client, fake):
    result = call_tool(client, "pios_trigger_sync", {"date_from": "yesterday"})
    assert result["isError"] is True
    assert fake.api_requests() == []


def test_feedback_enum_validated(client, fake):
    result = call_tool(client, "pios_submit_feedback", {"insight_id": 1, "feedback_status": "MAYBE"})
    assert result["isError"] is True
    assert fake.api_requests() == []


def test_graph_relationship_labels(client, fake):
    fake.routes[("GET", "/api/graph")] = lambda r: ok_json(
        {
            "nodes": [{"id": "a", "type": "Activity", "label": "러닝"}, {"id": "s", "type": "Sleep", "label": "수면"}],
            "relationships": [{"id": "r", "type": "AFFECTS", "sourceId": "s", "targetId": "a"}],
        }
    )
    result = call_tool(client, "pios_get_graph", {"days": 7, "view": "all"})
    assert "수면 -> [AFFECTS] -> 러닝" in _text(result)
    assert result["structuredContent"]["node_count"] == 2
