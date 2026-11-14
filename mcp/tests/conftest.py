"""공통 테스트 픽스처: 가짜 PIOS 백엔드(httpx.MockTransport) + Starlette TestClient."""

import json
from dataclasses import dataclass, field
from typing import Any, Callable
from urllib.parse import parse_qs

import httpx
import pytest
from starlette.testclient import TestClient

from pios_mcp.app import build_http_app
from pios_mcp.backend import PiosBackend
from pios_mcp.config import Settings

PUBLIC = "https://pios.example.com"
RESOURCE = f"{PUBLIC}/mcp"
SECRET = "s" * 40

READ_WRITE_TOKEN = "oauth-rw-token"
READ_ONLY_TOKEN = "oauth-ro-token"
API_KEY = "pios_testkey123"
WRONG_AUD_TOKEN = "oauth-other-aud"


@dataclass
class FakeBackend:
    """introspect + REST API를 흉내 낸다. 호출 기록을 남긴다."""

    tokens: dict[str, dict[str, Any]] = field(default_factory=dict)
    routes: dict[tuple[str, str], Callable[[httpx.Request], httpx.Response]] = field(default_factory=dict)
    requests: list[httpx.Request] = field(default_factory=list)
    introspect_calls: int = 0
    introspect_down: bool = False

    def handler(self, request: httpx.Request) -> httpx.Response:
        self.requests.append(request)
        path = request.url.path
        if path == "/api/oauth/introspect":
            self.introspect_calls += 1
            if self.introspect_down:
                raise httpx.ConnectError("down", request=request)
            if request.headers.get("X-PIOS-Internal-Secret") != SECRET:
                return httpx.Response(401, json={"error": "unauthorized"})
            token = parse_qs(request.content.decode()).get("token", [""])[0]
            body = self.tokens.get(token)
            return httpx.Response(200, json=body or {"active": False})
        fn = self.routes.get((request.method, path))
        if fn is None:
            return httpx.Response(404, json={"success": False, "message": "not found"})
        return fn(request)

    def api_requests(self) -> list[httpx.Request]:
        return [r for r in self.requests if r.url.path != "/api/oauth/introspect"]


def ok_json(data: Any) -> httpx.Response:
    return httpx.Response(200, json={"success": True, "message": None, "data": data})


@pytest.fixture
def settings() -> Settings:
    return Settings.from_env(
        {
            "PIOS_API_URL": "http://backend:8080/api",
            "PIOS_PUBLIC_BASE_URL": PUBLIC,
            "PIOS_MCP_RESOURCE_URL": RESOURCE,
            "PIOS_MCP_INTERNAL_SECRET": SECRET,
            "PIOS_TIMEZONE": "Asia/Seoul",
        }
    )


@pytest.fixture
def fake() -> FakeBackend:
    fb = FakeBackend()
    fb.tokens = {
        READ_WRITE_TOKEN: {
            "active": True,
            "sub": "7",
            "scope": "mcp:read mcp:write",
            "client_id": "client-abc",
            "aud": RESOURCE,
            "exp": 4102444800,
            "token_type": "oauth",
        },
        READ_ONLY_TOKEN: {
            "active": True,
            "sub": "7",
            "scope": "mcp:read",
            "client_id": "client-abc",
            "aud": RESOURCE,
            "exp": 4102444800,
            "token_type": "oauth",
        },
        API_KEY: {"active": True, "sub": "7", "scope": "mcp:read mcp:write", "aud": RESOURCE, "token_type": "api_key"},
        WRONG_AUD_TOKEN: {
            "active": True,
            "sub": "7",
            "scope": "mcp:read mcp:write",
            "client_id": "client-abc",
            "aud": "https://evil.example.com/mcp",
            "exp": 4102444800,
            "token_type": "oauth",
        },
    }
    return fb


@pytest.fixture
def app(settings: Settings, fake: FakeBackend):
    backend = PiosBackend(settings, transport=httpx.MockTransport(fake.handler))
    return build_http_app(settings, backend)


@pytest.fixture
def client(app):
    with TestClient(app, base_url=PUBLIC) as c:
        yield c


MCP_HEADERS = {
    "Accept": "application/json, text/event-stream",
    "Content-Type": "application/json",
    "MCP-Protocol-Version": "2025-06-18",
}


def rpc(client: TestClient, method: str, params: dict | None = None, *, headers: dict | None = None, path="/mcp"):
    body = {"jsonrpc": "2.0", "id": 1, "method": method, "params": params or {}}
    return client.post(path, content=json.dumps(body), headers={**MCP_HEADERS, **(headers or {})})


def call_tool(client: TestClient, name: str, arguments: dict | None = None, *, token=READ_WRITE_TOKEN, headers=None):
    h = {"Authorization": f"Bearer {token}"} if token else {}
    h.update(headers or {})
    resp = rpc(client, "tools/call", {"name": name, "arguments": arguments or {}}, headers=h)
    assert resp.status_code == 200, resp.text
    payload = resp.json()
    assert "result" in payload, payload
    return payload["result"]
