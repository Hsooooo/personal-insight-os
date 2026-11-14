"""PIOS 백엔드 REST API 클라이언트.

- 프로세스당 하나의 httpx.AsyncClient를 공유한다 (MCP 서버 lifespan에서 열고 닫음).
- 모든 호출은 `request()` 한 곳을 거치며, HTTP/네트워크 오류를 사용자에게 보여줄 수 있는
  한국어 메시지를 담은 `BackendError`로 변환한다 (raw exception을 MCP 클라이언트에 노출하지 않음).
"""

from __future__ import annotations

import logging
from typing import Any

import httpx

from .config import Settings

logger = logging.getLogger("pios_mcp.backend")

INTERNAL_SECRET_HEADER = "X-PIOS-Internal-Secret"


class BackendError(Exception):
    """백엔드 호출 실패. `message`는 그대로 MCP tool error 텍스트로 사용된다."""

    def __init__(self, message: str, *, status: int | None = None):
        super().__init__(message)
        self.message = message
        self.status = status


class BackendUnavailable(BackendError):
    """백엔드에 연결할 수 없음 (네트워크 오류/타임아웃/5xx). 인증 판단 불가 상황 구분용."""


def _backend_message(resp: httpx.Response) -> str | None:
    try:
        payload = resp.json()
    except ValueError:
        return None
    if isinstance(payload, dict):
        msg = payload.get("message") or payload.get("error_description") or payload.get("error")
        if isinstance(msg, str) and msg.strip():
            return msg.strip()[:300]
    return None


def map_http_error(resp: httpx.Response) -> BackendError:
    status = resp.status_code
    detail = _backend_message(resp)
    suffix = f" (서버 메시지: {detail})" if detail else ""
    if status == 401:
        return BackendError(
            "🔐 인증이 만료되었거나 유효하지 않습니다. MCP 클라이언트에서 PIOS 연결을 다시 로그인(재인증)하세요.",
            status=status,
        )
    if status == 403:
        return BackendError(
            "⛔ 이 작업에 대한 권한이 없습니다. 연결 시 승인한 권한(scope)을 확인하세요." + suffix,
            status=status,
        )
    if status == 404:
        return BackendError("📭 요청한 데이터를 찾을 수 없습니다. ID를 다시 확인하세요." + suffix, status=status)
    if status == 429:
        retry_after = resp.headers.get("Retry-After")
        hint = f" {retry_after}초 후" if retry_after and retry_after.isdigit() else " 잠시 후"
        return BackendError(f"⏳ 요청이 너무 많습니다 (rate limit).{hint} 다시 시도하세요." + suffix, status=status)
    if status >= 500:
        return BackendUnavailable(
            f"🛠️ PIOS 백엔드 내부 오류가 발생했습니다 (HTTP {status}). 잠시 후 다시 시도하세요.", status=status
        )
    return BackendError(f"❌ 요청이 거부되었습니다 (HTTP {status})." + suffix, status=status)


class PiosBackend:
    """공유 httpx.AsyncClient 래퍼."""

    def __init__(self, settings: Settings, *, transport: httpx.AsyncBaseTransport | None = None):
        self.settings = settings
        self._transport = transport
        self._client: httpx.AsyncClient | None = None
        self._refs = 0

    # ---- lifecycle -----------------------------------------------------

    def _new_client(self) -> httpx.AsyncClient:
        return httpx.AsyncClient(
            base_url=self.settings.api_url + "/",
            timeout=httpx.Timeout(self.settings.http_timeout, connect=5.0),
            limits=httpx.Limits(max_connections=50, max_keepalive_connections=10),
            transport=self._transport,
            follow_redirects=False,
            headers={"Accept": "application/json", "User-Agent": "pios-coach-mcp/2"},
        )

    @property
    def client(self) -> httpx.AsyncClient:
        if self._client is None or self._client.is_closed:
            self._client = self._new_client()
        return self._client

    async def __aenter__(self) -> "PiosBackend":
        self._refs += 1
        _ = self.client
        return self

    async def __aexit__(self, *exc: object) -> None:
        self._refs -= 1
        if self._refs <= 0 and self._client is not None:
            await self._client.aclose()
            self._client = None
            self._refs = 0

    # ---- requests ------------------------------------------------------

    def _headers(self, token: str, tool: str | None, client_ip: str | None) -> dict[str, str]:
        headers = {"Authorization": f"Bearer {token}"}
        if self.settings.internal_secret:
            headers[INTERNAL_SECRET_HEADER] = self.settings.internal_secret
        if client_ip:
            headers["X-MCP-Client-IP"] = client_ip
        if tool:
            headers["X-MCP-Tool"] = tool
        return headers

    async def request(
        self,
        method: str,
        path: str,
        *,
        token: str,
        tool: str | None = None,
        client_ip: str | None = None,
        params: dict[str, Any] | None = None,
        json: Any = None,
        timeout: float | None = None,
    ) -> Any:
        """백엔드 API를 호출하고 `ApiResponse.data`를 반환한다. 실패 시 BackendError."""
        clean_params = {k: v for k, v in (params or {}).items() if v is not None}
        kwargs: dict[str, Any] = {"params": clean_params, "headers": self._headers(token, tool, client_ip)}
        if json is not None:
            kwargs["json"] = json
        if timeout is not None:
            kwargs["timeout"] = httpx.Timeout(timeout, connect=5.0)
        try:
            resp = await self.client.request(method, path.lstrip("/"), **kwargs)
        except httpx.TimeoutException:
            logger.warning("backend timeout: %s %s", method, path)
            raise BackendUnavailable("⌛ PIOS 백엔드 응답 시간이 초과되었습니다. 잠시 후 다시 시도하세요.")
        except httpx.HTTPError as exc:
            logger.warning("backend network error: %s %s: %r", method, path, exc)
            raise BackendUnavailable("🔌 PIOS 백엔드에 연결할 수 없습니다. 서버 상태를 확인하세요.")

        if resp.status_code >= 400:
            raise map_http_error(resp)
        try:
            payload = resp.json()
        except ValueError:
            raise BackendError(f"❌ 백엔드 응답을 해석할 수 없습니다 (HTTP {resp.status_code}).")
        if not isinstance(payload, dict):
            raise BackendError("❌ 백엔드 응답 형식이 올바르지 않습니다.")
        if payload.get("success") is False:
            raise BackendError(f"❌ API 오류: {payload.get('message') or '알 수 없는 오류'}")
        return payload.get("data")

    async def introspect(self, token: str) -> dict[str, Any]:
        """내부 introspect 호출. 백엔드 장애 시 BackendUnavailable."""
        headers = {"Accept": "application/json"}
        if self.settings.internal_secret:
            headers[INTERNAL_SECRET_HEADER] = self.settings.internal_secret
        try:
            resp = await self.client.post(
                self.settings.introspect_url,
                data={"token": token},
                headers=headers,
                timeout=httpx.Timeout(5.0, connect=3.0),
            )
        except httpx.HTTPError as exc:
            logger.warning("introspection request failed: %r", exc)
            raise BackendUnavailable("introspection endpoint unreachable")
        if resp.status_code >= 500:
            raise BackendUnavailable("introspection endpoint error", status=resp.status_code)
        if resp.status_code != 200:
            # 401 = internal secret 불일치 등 설정 오류. 토큰은 비활성으로 취급한다.
            logger.error("introspection rejected by backend: HTTP %s (check PIOS_MCP_INTERNAL_SECRET)", resp.status_code)
            return {"active": False}
        try:
            body = resp.json()
        except ValueError:
            raise BackendUnavailable("introspection returned non-JSON")
        return body if isinstance(body, dict) else {"active": False}
