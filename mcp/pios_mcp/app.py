"""MCP 서버 / HTTP ASGI 앱 조립."""

from __future__ import annotations

import contextlib
import logging
from collections.abc import AsyncIterator
from typing import Any

from mcp.server.auth.settings import AuthSettings
from mcp.server.mcpserver import MCPServer
from mcp.server.transport_security import TransportSecuritySettings
from starlette.applications import Starlette
from starlette.requests import Request
from starlette.responses import JSONResponse
from starlette.routing import Mount, Route
from starlette.types import ASGIApp

from .auth import (
    AccessLogMiddleware,
    IntrospectionGateMiddleware,
    IntrospectionTokenVerifier,
    RequestPreprocessMiddleware,
    make_prm_endpoint,
    prm_paths,
)
from .backend import PiosBackend
from .config import READ_SCOPE, Settings
from .tools import ToolRuntime, TransportMode, register_tools

logger = logging.getLogger("pios_mcp")

APP_NAME = "pios-coach-mcp"
APP_VERSION = "2.0.0"

INSTRUCTIONS = (
    "PIOS Coach는 사용자의 Garmin 기반 건강·운동·수면 데이터를 다루는 생체스포츠 코치입니다. "
    "먼저 pios_get_user_profile로 전반적인 컨디션을 파악하고, 필요한 기간의 상세 데이터를 조회하세요. "
    "날짜는 YYYY-MM-DD(Asia/Seoul)이며, 답변은 데이터 근거를 밝히고 한국어 코치 톤으로 작성하세요. "
    "쓰기 도구(목표·피드백·동기화·브리핑)는 사용자가 명시적으로 요청했을 때만 사용하세요."
)


def build_mcp_server(
    settings: Settings,
    backend: PiosBackend,
    transport: TransportMode,
    verifier: IntrospectionTokenVerifier | None = None,
) -> MCPServer:
    @contextlib.asynccontextmanager
    async def lifespan(_server: MCPServer) -> AsyncIterator[dict[str, Any]]:
        # 프로세스 전체에서 하나의 httpx.AsyncClient를 공유한다.
        async with backend:
            yield {"backend": backend}

    kwargs: dict[str, Any] = {}
    if transport == "http":
        if verifier is None:
            raise ValueError("HTTP transport requires a token verifier")
        kwargs["token_verifier"] = verifier
        kwargs["auth"] = AuthSettings(
            issuer_url=settings.issuer_url,
            resource_server_url=settings.resource_url,
            required_scopes=[READ_SCOPE],
            # 토큰의 aud(introspection 결과)가 resource URL과 일치해야만 통과
            validate_token_resource=True,
        )

    mcp = MCPServer(
        APP_NAME,
        title="PIOS Coach",
        description="Personal Insight OS — 개인 건강·운동·수면 데이터 기반 생체스포츠 코치",
        instructions=INSTRUCTIONS,
        version=APP_VERSION,
        website_url=settings.public_base_url,
        lifespan=lifespan,
        **kwargs,
    )
    register_tools(mcp, ToolRuntime(settings, backend, transport))
    return mcp


async def _healthz(_request: Request) -> JSONResponse:
    return JSONResponse({"status": "ok"}, headers={"Cache-Control": "no-store"})


def build_http_app(settings: Settings, backend: PiosBackend | None = None) -> ASGIApp:
    """Streamable HTTP + OAuth RS ASGI 앱.

    라우팅 (모두 루트 기준, Caddy가 경로를 그대로 전달):
      - {mcp_path}                                  MCP Streamable HTTP (Bearer 필수)
      - /.well-known/oauth-protected-resource{mcp_path}  RFC 9728 PRM (+ 루트 변형)
      - /healthz                                    무인증 헬스체크
    """
    backend = backend or PiosBackend(settings)
    verifier = IntrospectionTokenVerifier(backend, settings)
    mcp = build_mcp_server(settings, backend, "http", verifier)

    inner = mcp.streamable_http_app(
        streamable_http_path=settings.mcp_path,
        stateless_http=True,
        json_response=True,
        transport_security=TransportSecuritySettings(
            enable_dns_rebinding_protection=True,
            allowed_hosts=settings.allowed_hosts,
            allowed_origins=settings.allowed_origins,
        ),
        host="0.0.0.0",
    )

    @contextlib.asynccontextmanager
    async def lifespan(_app: Starlette) -> AsyncIterator[None]:
        # inner Starlette를 Mount하면 그 lifespan은 실행되지 않으므로 session manager를 직접 연다.
        # (session manager가 MCPServer lifespan → 공유 httpx client를 연다)
        async with mcp.session_manager.run():
            logger.info(
                "PIOS MCP ready: resource=%s issuer=%s path=%s", settings.resource_url, settings.issuer_url, settings.mcp_path
            )
            yield

    prm = make_prm_endpoint(settings)
    routes = [Route(p, endpoint=prm, methods=["GET", "OPTIONS"]) for p in prm_paths(settings)]
    routes.append(Route("/healthz", endpoint=_healthz, methods=["GET"]))
    routes.append(Mount("/", app=inner))

    outer = Starlette(routes=routes, lifespan=lifespan)

    gated = IntrospectionGateMiddleware(outer, settings, verifier)
    pre = RequestPreprocessMiddleware(gated, settings)
    app = AccessLogMiddleware(pre)
    # 테스트/운영 진단용 핸들
    app.mcp = mcp  # type: ignore[attr-defined]
    app.verifier = verifier  # type: ignore[attr-defined]
    app.backend = backend  # type: ignore[attr-defined]
    return app
