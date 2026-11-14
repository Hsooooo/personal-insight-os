"""OAuth 2.1 Resource Server 측 구현.

- `IntrospectionTokenVerifier`: 백엔드 내부 `/api/oauth/introspect`로 Bearer 토큰(OAuth access token
  또는 `pios_` API key)을 검증한다. 양성 결과는 최대 60초, 음성 결과는 수 초만 캐시한다.
- ASGI 미들웨어: 경로 정규화(/mcp/ → /mcp), X-API-Key → Authorization 재작성, 클라이언트 IP 추출,
  introspection 장애 시 503 응답(401로 오인해 클라이언트가 토큰을 버리지 않도록).
- Protected Resource Metadata(RFC 9728) 응답 생성.
"""

from __future__ import annotations

import contextvars
import hashlib
import logging
import time
from collections import OrderedDict
from typing import Any, Callable

from mcp.server.auth.middleware.auth_context import get_access_token
from mcp.server.auth.provider import AccessToken
from starlette.requests import Request
from starlette.responses import JSONResponse, Response
from starlette.types import ASGIApp, Receive, Scope, Send

from .backend import BackendUnavailable, PiosBackend
from .config import SUPPORTED_SCOPES, Settings

logger = logging.getLogger("pios_mcp.auth")

client_ip_var: contextvars.ContextVar[str | None] = contextvars.ContextVar("pios_client_ip", default=None)

API_KEY_CLIENT_ID = "pios_api_key"  # backend introspect와 동일
_MAX_CACHE_ENTRIES = 2048


def _token_key(token: str) -> str:
    # 원문 토큰을 메모리 dict 키로 두지 않는다.
    return hashlib.sha256(token.encode("utf-8")).hexdigest()


class IntrospectionTokenVerifier:
    """mcp SDK `TokenVerifier` 프로토콜 구현."""

    def __init__(
        self,
        backend: PiosBackend,
        settings: Settings,
        *,
        clock: Callable[[], float] = time.time,
    ):
        self.backend = backend
        self.settings = settings
        self.clock = clock
        self._cache: OrderedDict[str, tuple[AccessToken | None, float]] = OrderedDict()

    def _cache_get(self, key: str) -> tuple[bool, AccessToken | None]:
        entry = self._cache.get(key)
        if entry is None:
            return False, None
        value, expires = entry
        if expires <= self.clock():
            self._cache.pop(key, None)
            return False, None
        return True, value

    def _cache_put(self, key: str, value: AccessToken | None, ttl: float) -> None:
        if ttl <= 0:
            return
        self._cache[key] = (value, self.clock() + ttl)
        self._cache.move_to_end(key)
        while len(self._cache) > _MAX_CACHE_ENTRIES:
            self._cache.popitem(last=False)

    def invalidate(self, token: str | None = None) -> None:
        if token is None:
            self._cache.clear()
        else:
            self._cache.pop(_token_key(token), None)

    def _to_access_token(self, token: str, body: dict[str, Any]) -> AccessToken | None:
        if body.get("active") is not True:
            return None
        sub = body.get("sub")
        if sub is None or str(sub).strip() == "":
            return None
        token_type = body.get("token_type") or "oauth"

        aud = body.get("aud")
        if isinstance(aud, list):
            aud = next((a for a in aud if str(a).rstrip("/") == self.settings.resource_url.rstrip("/")), None)
        if not aud and token_type == "api_key":
            # API key는 이 MCP 리소스 전용 자격증명이다.
            aud = self.settings.resource_url
        if not aud:
            return None

        scope = body.get("scope")
        if isinstance(scope, list):
            scopes = [str(s) for s in scope]
        else:
            scopes = str(scope or "").split()

        exp = body.get("exp")
        try:
            expires_at = int(exp) if exp is not None else None
        except (TypeError, ValueError):
            expires_at = None
        if expires_at is not None and expires_at <= int(self.clock()):
            return None

        return AccessToken(
            token=token,
            client_id=str(body.get("client_id") or API_KEY_CLIENT_ID),
            scopes=scopes,
            expires_at=expires_at,
            resource=str(aud),
            subject=str(sub),
            claims={"token_type": token_type},
        )

    async def verify_token(self, token: str) -> AccessToken | None:
        """유효하면 AccessToken, 아니면 None. 백엔드 장애 시 BackendUnavailable을 던진다."""
        if not token or len(token) > 8192:
            return None
        key = _token_key(token)
        hit, cached = self._cache_get(key)
        if hit:
            return cached

        body = await self.backend.introspect(token)
        result = self._to_access_token(token, body)
        if result is None:
            self._cache_put(key, None, self.settings.negative_cache_ttl)
            return None

        ttl = float(self.settings.token_cache_ttl)
        if result.expires_at is not None:
            ttl = min(ttl, result.expires_at - self.clock())
        self._cache_put(key, result, ttl)
        return result


# ---------------------------------------------------------------------------
# Protected Resource Metadata (RFC 9728)
# ---------------------------------------------------------------------------


def protected_resource_metadata(settings: Settings) -> dict[str, Any]:
    return {
        "resource": settings.resource_url,
        "authorization_servers": [settings.issuer_url],
        "scopes_supported": list(SUPPORTED_SCOPES),
        "bearer_methods_supported": ["header"],
        "resource_name": "PIOS Coach",
        "resource_documentation": f"{settings.public_base_url}/mcp-settings",
    }


def prm_paths(settings: Settings) -> list[str]:
    base = "/.well-known/oauth-protected-resource"
    path = settings.mcp_path
    return list(dict.fromkeys([base + path, base + path + "/", base, base + "/"]))


_CORS_HEADERS = {
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Methods": "GET, OPTIONS",
    "Access-Control-Allow-Headers": "*",
}


def make_prm_endpoint(settings: Settings):
    metadata = protected_resource_metadata(settings)

    async def endpoint(request: Request) -> Response:
        if request.method == "OPTIONS":
            return Response(status_code=204, headers=_CORS_HEADERS)
        return JSONResponse(metadata, headers={**_CORS_HEADERS, "Cache-Control": "public, max-age=3600"})

    return endpoint


# ---------------------------------------------------------------------------
# ASGI middleware
# ---------------------------------------------------------------------------


def _get_header(scope: Scope, name: bytes) -> str | None:
    for key, value in scope.get("headers", []):
        if key == name:
            return value.decode("latin-1")
    return None


def extract_client_ip(scope: Scope, trust_proxy: bool) -> str | None:
    if trust_proxy:
        xff = _get_header(scope, b"x-forwarded-for")
        if xff:
            first = xff.split(",")[0].strip()
            if first:
                return first[:64]
        real = _get_header(scope, b"x-real-ip")
        if real:
            return real.strip()[:64]
    client = scope.get("client")
    return client[0] if client else None


class RequestPreprocessMiddleware:
    """인증 미들웨어보다 바깥에서 동작하는 전처리.

    1. `/mcp/` → `/mcp` 경로 정규화 (Starlette의 slash redirect가 Authorization을 잃게 하지 않도록)
    2. `X-API-Key: pios_...` → `Authorization: Bearer pios_...` (구버전 클라이언트 호환)
    3. 클라이언트 IP를 contextvar에 기록 (백엔드 감사 로그용 X-MCP-Client-IP)
    """

    def __init__(self, app: ASGIApp, settings: Settings):
        self.app = app
        self.settings = settings
        self.mcp_path = settings.mcp_path

    async def __call__(self, scope: Scope, receive: Receive, send: Send) -> None:
        if scope["type"] != "http":
            await self.app(scope, receive, send)
            return

        scope = dict(scope)
        path = scope.get("path", "")
        if path != "/" and path.rstrip("/") == self.mcp_path and path != self.mcp_path:
            scope["path"] = self.mcp_path
            scope["raw_path"] = self.mcp_path.encode()

        headers = list(scope.get("headers", []))
        has_auth = any(k == b"authorization" for k, _ in headers)
        api_key = None
        for k, v in headers:
            if k == b"x-api-key":
                api_key = v.decode("latin-1").strip()
                break
        if api_key and not has_auth:
            headers = [(k, v) for k, v in headers if k != b"x-api-key"]
            headers.append((b"authorization", f"Bearer {api_key}".encode("latin-1")))
            scope["headers"] = headers

        ip_token = client_ip_var.set(extract_client_ip(scope, self.settings.trust_proxy_headers))
        try:
            await self.app(scope, receive, send)
        finally:
            client_ip_var.reset(ip_token)


class IntrospectionGateMiddleware:
    """MCP 엔드포인트 요청의 토큰을 미리 검증해 백엔드 장애를 503으로 응답한다.

    검증 결과는 verifier 캐시에 남으므로 이어지는 SDK BearerAuthBackend 검증은 추가 호출 없이 끝난다.
    무효 토큰/토큰 없음 판정(401 + WWW-Authenticate)은 SDK RequireAuthMiddleware에 맡긴다.
    """

    def __init__(self, app: ASGIApp, settings: Settings, verifier: IntrospectionTokenVerifier):
        self.app = app
        self.mcp_path = settings.mcp_path
        self.verifier = verifier

    async def __call__(self, scope: Scope, receive: Receive, send: Send) -> None:
        if scope["type"] == "http" and scope.get("path") == self.mcp_path:
            auth = _get_header(scope, b"authorization")
            if auth and auth.lower().startswith("bearer "):
                try:
                    await self.verifier.verify_token(auth[7:].strip())
                except BackendUnavailable:
                    response = JSONResponse(
                        {
                            "error": "temporarily_unavailable",
                            "error_description": "Authorization server is unreachable; retry later.",
                        },
                        status_code=503,
                        headers={"Retry-After": "5"},
                    )
                    await response(scope, receive, send)
                    return
        await self.app(scope, receive, send)


class AccessLogMiddleware:
    """토큰 값을 남기지 않는 간단한 접근 로그."""

    def __init__(self, app: ASGIApp):
        self.app = app

    async def __call__(self, scope: Scope, receive: Receive, send: Send) -> None:
        if scope["type"] != "http":
            await self.app(scope, receive, send)
            return
        method = scope.get("method", "")
        path = scope.get("path", "")
        started = time.monotonic()
        status_holder = {"status": 0}

        async def wrapped_send(message):
            if message.get("type") == "http.response.start":
                status_holder["status"] = message.get("status", 0)
            await send(message)

        try:
            await self.app(scope, receive, wrapped_send)
        finally:
            if path != "/healthz":
                logger.info(
                    "%s %s -> %s (%.0fms)", method, path, status_holder["status"], (time.monotonic() - started) * 1000
                )


# ---------------------------------------------------------------------------
# Tool 측 헬퍼
# ---------------------------------------------------------------------------


def current_access_token() -> AccessToken | None:
    return get_access_token()
