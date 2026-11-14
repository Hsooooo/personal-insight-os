"""HTTP 레벨: PRM, 401/WWW-Authenticate, X-API-Key 재작성, 슬래시 정규화, Host 검증, 503."""

from conftest import (
    API_KEY,
    MCP_HEADERS,
    PUBLIC,
    READ_WRITE_TOKEN,
    RESOURCE,
    SECRET,
    WRONG_AUD_TOKEN,
    ok_json,
    rpc,
)
from starlette.testclient import TestClient


def _profile_route(fake):
    fake.routes[("GET", "/api/dashboard/summary")] = lambda r: ok_json({"totalActivities": 3})


def test_prm_endpoints(client):
    for path in (
        "/.well-known/oauth-protected-resource/mcp",
        "/.well-known/oauth-protected-resource",
        "/.well-known/oauth-protected-resource/mcp/",
    ):
        resp = client.get(path)
        assert resp.status_code == 200, path
        body = resp.json()
        assert body["resource"] == RESOURCE
        assert body["authorization_servers"] == [PUBLIC]
        assert body["scopes_supported"] == ["mcp:read", "mcp:write"]
        assert body["bearer_methods_supported"] == ["header"]
        assert resp.headers["access-control-allow-origin"] == "*"


def test_prm_reachable_with_internal_host(app):
    # docker healthcheck: http://localhost:8001/.well-known/oauth-protected-resource
    with TestClient(app, base_url="http://localhost:8001") as c:
        assert c.get("/.well-known/oauth-protected-resource").status_code == 200
        assert c.get("/healthz").json() == {"status": "ok"}


def test_no_token_returns_401_with_resource_metadata(client, fake):
    resp = rpc(client, "tools/list")
    assert resp.status_code == 401
    www = resp.headers["www-authenticate"]
    assert www.startswith("Bearer ")
    assert f'resource_metadata="{PUBLIC}/.well-known/oauth-protected-resource/mcp"' in www
    assert fake.api_requests() == []


def test_invalid_token_returns_401(client):
    resp = rpc(client, "tools/list", headers={"Authorization": "Bearer nope"})
    assert resp.status_code == 401
    assert "invalid_token" in resp.headers["www-authenticate"]


def test_wrong_audience_rejected(client):
    resp = rpc(client, "tools/list", headers={"Authorization": f"Bearer {WRONG_AUD_TOKEN}"})
    assert resp.status_code == 401


def test_env_api_key_is_not_an_http_fallback(settings, fake):
    """PIOS_API_KEY가 있어도 HTTP 모드에서는 무인증 요청을 통과시키지 않는다."""
    import httpx
    from dataclasses import replace

    from pios_mcp.app import build_http_app
    from pios_mcp.backend import PiosBackend

    s = replace(settings, stdio_api_key=API_KEY)
    app = build_http_app(s, PiosBackend(s, transport=httpx.MockTransport(fake.handler)))
    with TestClient(app, base_url=PUBLIC) as c:
        assert rpc(c, "tools/list").status_code == 401


def test_bearer_token_lists_tools(client):
    resp = rpc(client, "tools/list", headers={"Authorization": f"Bearer {READ_WRITE_TOKEN}"})
    assert resp.status_code == 200, resp.text
    tools = resp.json()["result"]["tools"]
    assert len(tools) == 15
    for t in tools:
        ann = t["annotations"]
        assert t["title"]
        for hint in ("readOnlyHint", "destructiveHint", "idempotentHint", "openWorldHint"):
            assert hint in ann, (t["name"], hint)
        assert "outputSchema" in t, t["name"]
    by_name = {t["name"]: t for t in tools}
    assert by_name["pios_get_sleep"]["annotations"]["readOnlyHint"] is True
    assert by_name["pios_generate_briefing"]["annotations"]["destructiveHint"] is True
    assert by_name["pios_trigger_sync"]["annotations"]["openWorldHint"] is True


def test_x_api_key_rewritten_to_bearer(client, fake):
    _profile_route(fake)
    resp = rpc(
        client,
        "tools/call",
        {"name": "pios_get_user_profile", "arguments": {}},
        headers={"X-API-Key": API_KEY},
    )
    assert resp.status_code == 200, resp.text
    assert resp.json()["result"].get("isError") is not True
    api = fake.api_requests()
    assert len(api) == 1
    assert api[0].headers["Authorization"] == f"Bearer {API_KEY}"
    assert api[0].headers["X-PIOS-Internal-Secret"] == SECRET
    assert "X-API-Key" not in api[0].headers


def test_trailing_slash_path_works_without_redirect(client, fake):
    _profile_route(fake)
    resp = rpc(
        client,
        "tools/call",
        {"name": "pios_get_user_profile", "arguments": {}},
        headers={"Authorization": f"Bearer {READ_WRITE_TOKEN}"},
        path="/mcp/",
    )
    assert resp.status_code == 200, resp.text
    assert not resp.history  # 리다이렉트 없이 처리


def test_forwarded_client_ip_and_headers(client, fake):
    _profile_route(fake)
    rpc(
        client,
        "tools/call",
        {"name": "pios_get_user_profile", "arguments": {}},
        headers={"Authorization": f"Bearer {READ_WRITE_TOKEN}", "X-Forwarded-For": "203.0.113.9, 10.0.0.2"},
    )
    api = fake.api_requests()[0]
    assert api.headers["X-MCP-Client-IP"] == "203.0.113.9"
    assert api.headers["X-MCP-Tool"] == "pios_get_user_profile"
    assert api.headers["Authorization"] == f"Bearer {READ_WRITE_TOKEN}"


def test_unknown_host_rejected(app):
    with TestClient(app, base_url="https://attacker.example.net") as c:
        resp = c.post(
            "/mcp",
            json={"jsonrpc": "2.0", "id": 1, "method": "tools/list", "params": {}},
            headers={**MCP_HEADERS, "Authorization": f"Bearer {READ_WRITE_TOKEN}"},
        )
        assert resp.status_code == 421


def test_introspection_outage_returns_503(client, fake):
    fake.introspect_down = True
    resp = rpc(client, "tools/list", headers={"Authorization": f"Bearer {READ_WRITE_TOKEN}"})
    assert resp.status_code == 503
    assert resp.headers.get("retry-after") == "5"


def _modern_body(name: str, arguments: dict) -> dict:
    return {
        "jsonrpc": "2.0",
        "id": 7,
        "method": "tools/call",
        "params": {
            "name": name,
            "arguments": arguments,
            "_meta": {
                "io.modelcontextprotocol/protocolVersion": "2026-07-28",
                "io.modelcontextprotocol/clientCapabilities": {},
                "io.modelcontextprotocol/clientInfo": {"name": "pytest", "version": "0"},
            },
        },
    }


def test_modern_2026_protocol_tool_call(client, fake):
    """2026-07-28 stateless 요청(initialize 없음)에서도 토큰이 도구까지 전달된다."""
    import json as _json

    fake.routes[("GET", "/api/goals")] = lambda r: ok_json([{"id": 1, "title": "g"}])
    resp = client.post(
        "/mcp",
        content=_json.dumps(_modern_body("pios_get_goals", {})),
        headers={
            "Accept": "application/json, text/event-stream",
            "Content-Type": "application/json",
            "MCP-Protocol-Version": "2026-07-28",
            "Mcp-Method": "tools/call",
            "Mcp-Name": "pios_get_goals",
            "Authorization": f"Bearer {READ_WRITE_TOKEN}",
        },
    )
    assert resp.status_code == 200, resp.text
    result = resp.json()["result"]
    assert result.get("isError") is not True, result
    assert result["structuredContent"]["count"] == 1
    assert fake.api_requests()[0].headers["Authorization"] == f"Bearer {READ_WRITE_TOKEN}"


def test_modern_2026_protocol_requires_auth(client):
    import json as _json

    resp = client.post(
        "/mcp",
        content=_json.dumps(_modern_body("pios_get_goals", {})),
        headers={
            "Accept": "application/json, text/event-stream",
            "Content-Type": "application/json",
            "MCP-Protocol-Version": "2026-07-28",
            "Mcp-Method": "tools/call",
            "Mcp-Name": "pios_get_goals",
        },
    )
    assert resp.status_code == 401
    assert "resource_metadata=" in resp.headers["www-authenticate"]


def test_legacy_initialize_handshake(client):
    resp = rpc(
        client,
        "initialize",
        {"protocolVersion": "2025-06-18", "capabilities": {}, "clientInfo": {"name": "pytest", "version": "0"}},
        headers={"Authorization": f"Bearer {READ_WRITE_TOKEN}"},
    )
    assert resp.status_code == 200, resp.text
    info = resp.json()["result"]["serverInfo"]
    assert info["name"] == "pios-coach-mcp"
