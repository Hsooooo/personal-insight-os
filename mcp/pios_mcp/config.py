"""환경 변수 기반 설정.

mcp 2.x SDK는 MCP_* 환경 변수를 더 이상 읽지 않으므로, 이 서버의 모든 설정은
PIOS_* 변수에서 명시적으로 읽어 SDK에 전달한다.
"""

from __future__ import annotations

import os
from dataclasses import dataclass, field
from urllib.parse import urlsplit
from zoneinfo import ZoneInfo, ZoneInfoNotFoundError

READ_SCOPE = "mcp:read"
WRITE_SCOPE = "mcp:write"
SUPPORTED_SCOPES = [READ_SCOPE, WRITE_SCOPE]

DEFAULT_PUBLIC_BASE_URL = "http://localhost:8080"
DEFAULT_API_URL = "http://localhost:8080/api"
DEFAULT_TIMEZONE = "Asia/Seoul"
MAX_TOKEN_CACHE_TTL = 60


def _csv(value: str | None) -> list[str]:
    if not value:
        return []
    return [item.strip() for item in value.split(",") if item.strip()]


def _bool(value: str | None, default: bool) -> bool:
    if value is None or value.strip() == "":
        return default
    return value.strip().lower() in {"1", "true", "yes", "on"}


def _netloc_variants(url: str) -> list[str]:
    """URL에서 Host 헤더로 올 수 있는 값(host, host:port)을 만든다."""
    parts = urlsplit(url)
    host = parts.hostname
    if not host:
        return []
    if ":" in host:  # IPv6
        host = f"[{host}]"
    variants = [host]
    if parts.port is not None:
        variants.insert(0, f"{host}:{parts.port}")
    else:
        default_port = 443 if parts.scheme == "https" else 80
        variants.append(f"{host}:{default_port}")
    return variants


def _origin(url: str) -> str | None:
    parts = urlsplit(url)
    if not parts.scheme or not parts.netloc:
        return None
    return f"{parts.scheme}://{parts.netloc}"


@dataclass(frozen=True)
class Settings:
    api_url: str = DEFAULT_API_URL
    public_base_url: str = DEFAULT_PUBLIC_BASE_URL
    resource_url: str = f"{DEFAULT_PUBLIC_BASE_URL}/mcp"
    internal_secret: str = ""
    introspect_url: str = f"{DEFAULT_API_URL}/oauth/introspect"
    stdio_api_key: str = ""
    timezone_name: str = DEFAULT_TIMEZONE
    trust_proxy_headers: bool = True
    token_cache_ttl: int = MAX_TOKEN_CACHE_TTL
    negative_cache_ttl: int = 5
    http_timeout: float = 30.0
    extra_allowed_hosts: list[str] = field(default_factory=list)
    extra_allowed_origins: list[str] = field(default_factory=list)

    # ---- derived -------------------------------------------------------

    @property
    def issuer_url(self) -> str:
        return self.public_base_url

    @property
    def mcp_path(self) -> str:
        """MCP 엔드포인트 경로 (resource URL의 path, 끝 슬래시 제거)."""
        path = urlsplit(self.resource_url).path.rstrip("/")
        return path or "/mcp"

    @property
    def tz(self) -> ZoneInfo:
        try:
            return ZoneInfo(self.timezone_name)
        except (ZoneInfoNotFoundError, ValueError):
            return ZoneInfo(DEFAULT_TIMEZONE)

    @property
    def allowed_hosts(self) -> list[str]:
        hosts: list[str] = []
        for url in (self.public_base_url, self.resource_url):
            hosts.extend(_netloc_variants(url))
        hosts.extend(
            [
                # docker compose 내부 이름 (backend/healthcheck 등 내부 호출)
                "mcp",
                "mcp:8001",
                "pios-mcp",
                "pios-mcp:8001",
                # 로컬 개발 / 컨테이너 내부 healthcheck
                "localhost",
                "localhost:*",
                "127.0.0.1",
                "127.0.0.1:*",
                "[::1]",
                "[::1]:*",
            ]
        )
        hosts.extend(self.extra_allowed_hosts)
        return list(dict.fromkeys(hosts))

    @property
    def allowed_origins(self) -> list[str]:
        origins: list[str] = []
        for url in (self.public_base_url, self.resource_url):
            origin = _origin(url)
            if origin:
                origins.append(origin)
        origins.extend(["http://localhost:*", "http://127.0.0.1:*", "http://[::1]:*"])
        origins.extend(self.extra_allowed_origins)
        return list(dict.fromkeys(origins))

    # ---- factory -------------------------------------------------------

    @classmethod
    def from_env(cls, env: dict[str, str] | None = None) -> "Settings":
        env = dict(os.environ if env is None else env)
        api_url = (env.get("PIOS_API_URL") or DEFAULT_API_URL).rstrip("/")
        public = (env.get("PIOS_PUBLIC_BASE_URL") or DEFAULT_PUBLIC_BASE_URL).rstrip("/")
        # resource URL은 OAuth access token의 aud와 정확히 일치해야 하므로 가공하지 않는다.
        resource = env.get("PIOS_MCP_RESOURCE_URL") or f"{public}/mcp"
        try:
            ttl = int(env.get("PIOS_MCP_TOKEN_CACHE_TTL", MAX_TOKEN_CACHE_TTL))
        except ValueError:
            ttl = MAX_TOKEN_CACHE_TTL
        try:
            timeout = float(env.get("PIOS_MCP_HTTP_TIMEOUT", 30.0))
        except ValueError:
            timeout = 30.0
        return cls(
            api_url=api_url,
            public_base_url=public,
            resource_url=resource,
            internal_secret=env.get("PIOS_MCP_INTERNAL_SECRET", ""),
            introspect_url=env.get("PIOS_INTROSPECT_URL") or f"{api_url}/oauth/introspect",
            stdio_api_key=env.get("PIOS_API_KEY", ""),
            timezone_name=env.get("PIOS_TIMEZONE") or DEFAULT_TIMEZONE,
            trust_proxy_headers=_bool(env.get("PIOS_MCP_TRUST_PROXY"), True),
            token_cache_ttl=max(0, min(ttl, MAX_TOKEN_CACHE_TTL)),
            http_timeout=timeout if timeout > 0 else 30.0,
            extra_allowed_hosts=_csv(env.get("PIOS_MCP_ALLOWED_HOSTS")),
            extra_allowed_origins=_csv(env.get("PIOS_MCP_ALLOWED_ORIGINS")),
        )
