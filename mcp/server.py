"""
PIOS Coach MCP Server (mcp Python SDK 2.x)

Personal Insight OS의 건강/운동/수면 데이터를 외부 LLM(Claude, Cursor, Codex, Kimi 등)이
읽고 일부 기록할 수 있도록 제공하는 Model Context Protocol 서버입니다.
생체스포츠 코치(Bio-Sports Coach) 페르소나를 가집니다.

실행 방법:
    # stdio (로컬 클라이언트). 자격증명은 PIOS_API_KEY(pios_ API 키)
    PIOS_API_URL=http://localhost:8080/api PIOS_API_KEY=pios_xxx \
        python mcp/server.py --transport stdio

    # Streamable HTTP (OAuth 2.1 Resource Server). 무인증 fallback 없음
    PIOS_API_URL=http://localhost:8080/api PIOS_PUBLIC_BASE_URL=http://localhost:8080 \
    PIOS_MCP_INTERNAL_SECRET=... \
        python mcp/server.py --transport streamable-http --host 0.0.0.0 --port 8001

자세한 내용은 docs/mcp-integration.md 참고.
"""

import argparse
import logging
import os
import sys

from pios_mcp.app import build_http_app, build_mcp_server
from pios_mcp.backend import PiosBackend
from pios_mcp.config import Settings


def _configure_logging() -> None:
    level = os.getenv("PIOS_MCP_LOG_LEVEL", "INFO").upper()
    # stdio transport는 stdout을 프로토콜에 쓰므로 로그는 항상 stderr로 보낸다.
    logging.basicConfig(
        level=getattr(logging, level, logging.INFO),
        stream=sys.stderr,
        format="%(asctime)s %(levelname)s %(name)s: %(message)s",
    )


def main(argv: list[str] | None = None) -> None:
    parser = argparse.ArgumentParser(description="PIOS Coach MCP Server")
    parser.add_argument("--transport", choices=["stdio", "streamable-http"], default="stdio")
    parser.add_argument("--host", default="127.0.0.1", help="HTTP transport 바인딩 호스트 (컨테이너에서는 0.0.0.0)")
    parser.add_argument("--port", type=int, default=8001, help="HTTP transport 포트 (기본: 8001)")
    args = parser.parse_args(argv)

    _configure_logging()
    settings = Settings.from_env()

    if args.transport == "streamable-http":
        import uvicorn

        if len(settings.internal_secret) < 32:
            logging.getLogger("pios_mcp").error(
                "PIOS_MCP_INTERNAL_SECRET is required for streamable-http and must be at least 32 characters."
            )
            sys.exit(2)
        app = build_http_app(settings)
        uvicorn.run(
            app,
            host=args.host,
            port=args.port,
            # Caddy 뒤에서 동작: X-Forwarded-* 는 앱에서 직접 해석 (PIOS_MCP_TRUST_PROXY)
            proxy_headers=False,
            server_header=False,
            log_level=os.getenv("PIOS_MCP_LOG_LEVEL", "info").lower(),
            access_log=False,
        )
    else:
        if not settings.stdio_api_key:
            logging.getLogger("pios_mcp").warning("PIOS_API_KEY is not set; tools will return an authentication error.")
        mcp = build_mcp_server(settings, PiosBackend(settings), "stdio")
        mcp.run(transport="stdio")


if __name__ == "__main__":
    main()
