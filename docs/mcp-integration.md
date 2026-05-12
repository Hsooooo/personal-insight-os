# MCP 연동 가이드 — PIOS 생체스포츠 코치

## 개요

PIOS(Personal Insight OS)는 **Model Context Protocol(MCP)** 서버를 통해 외부 LLM(Claude, Copilot, Kimi 등)이 사용자의 개인화 건강 데이터를 안전하게 읽고 코칭할 수 있도록 지원합니다.

MCP 서버는 **생체스포츠 코치(Bio-Sports Coach)** 페르소나를 가지며, Garmin 데이터를 기반으로 훈련/회복/수면 인사이트를 제공합니다.

## 아키텍처

```
[외부 LLM 클라이언트] ←──MCP(stdio/streamable-http)──→ [PIOS MCP Server]
                                                                     │
                                                                     └──HTTP──→ [PIOS Backend API]
                                                                                     │
                                                                                     └──JDBC──→ [PostgreSQL + Neo4j]
```

- **MCP Server**: Python + FastMCP 기반의 Thin Proxy 서버 (`mcp/` 디렉토리)
- **Transport**: `stdio` (로컬 CLI 도구) 또는 `streamable-http` (원격 에이전트)
- **인증**: JWT Bearer Token (`Authorization: Bearer <token>`)

## 제공 도구 (Tools)

| Tool | 설명 | API |
|------|------|-----|
| `pios_get_user_profile` | 대시보드 요약 조회 | `GET /api/dashboard/summary` |
| `pios_get_activities` | 활동 목록 조회 (기간/유형 필터) | `GET /api/activities` |
| `pios_get_activity_detail` | 특정 활동 상세 조회 | `GET /api/activities/{id}` |
| `pios_get_health_metrics` | 건강 지표 조회 (RHR, HRV, 스트레스 등) | `GET /api/health/metrics` |
| `pios_get_sleep` | 수면 기록 조회 | `GET /api/health/sleep` |
| `pios_get_goals` | 목표 목록 조회 | `GET /api/goals` |
| `pios_get_insights` | 인사이트 목록 조회 | `GET /api/insights` |
| `pios_ask_coach` | 자연어 코칭 질문 (RAG) | `POST /api/ask` |
| `pios_get_graph` | 개인 지식 그래프 조회 | `GET /api/graph` |

## 설치 및 실행

### 1. 환경 변수 설정

`.env` 파일에 JWT 토큰을 설정합니다.

```bash
# .env
PIOS_MCP_API_KEY=eyJhbGciOiJIUzI1NiIs...
```

> JWT 토큰은 PIOS 웹앱에서 로그인 후 브라우저 개발자 도구 → Application → LocalStorage → `pios-auth` 에서 확인하거나, `POST /api/auth/login` API로 직접 발급받을 수 있습니다.

### 2. Docker Compose (권장)

```bash
docker-compose up --build mcp
```

- MCP 서버가 `http://localhost:8001` (streamable-http)로 노출됩니다.

### 3. 로컬 Python (stdio transport)

```bash
cd mcp
pip install -r requirements.txt
PIOS_API_URL=http://localhost:8080/api \
PIOS_API_KEY=eyJhbGciOiJIUzI1NiIs... \
    python server.py --transport stdio
```

## 클라이언트 연동

### Kimi Code CLI

`~/.kimi/mcp-config.json` (또는 프로젝트 루트의 `.kimi/mcp-config.json`)에 추가:

```json
{
  "mcpServers": {
    "pios-coach": {
      "command": "python",
      "args": ["/absolute/path/to/mcp/server.py", "--transport", "stdio"],
      "env": {
        "PIOS_API_URL": "http://localhost:8080/api",
        "PIOS_API_KEY": "eyJhbGciOiJIUzI1NiIs..."
      }
    }
  }
}
```

### Claude Desktop

`claude_desktop_config.json` (macOS: `~/Library/Application Support/Claude/claude_desktop_config.json`):

```json
{
  "mcpServers": {
    "pios-coach": {
      "command": "python",
      "args": ["/absolute/path/to/mcp/server.py", "--transport", "stdio"],
      "env": {
        "PIOS_API_URL": "http://localhost:8080/api",
        "PIOS_API_KEY": "eyJhbGciOiJIUzI1NiIs..."
      }
    }
  }
}
```

### VS Code + Cline / Roo Code

`.vscode/mcp.json` 또는 Cline 설정의 MCP 서버 목록에 동일한 JSON을 추가합니다.

## 보안 고려사항

- **JWT 토큰 관리**: MCP 서버는 사용자의 JWT 토큰을 메모리/환경변수로만 보관합니다. 디스크에 저장하지 않습니다.
- **네트워크 격리**: Docker Compose 낸넨 `pios-network` 브리지 네트워크를 사용하여 백엔드와만 통신합니다.
- **streamable-http**: 프로덕션 환경에서는 반드시 reverse proxy(Caddy/nginx) + TLS를 적용하고, `X-API-Key` 헤더 기반 인증을 추가하세요.

## 향후 확장

- [ ] 사용자별 동적 JWT 발급 (OAuth2 / Refresh Token)
- [ ] `pios_create_goal` — 목표 생성 tool
- [ ] `pios_save_insight` — 인사이트 저장 tool
- [ ] `pios_sync_garmin` — Garmin 동기화 트리거 tool
- [ ] Resource 프로토콜 지원 (시계열 데이터를 MCP Resource로 노출)
