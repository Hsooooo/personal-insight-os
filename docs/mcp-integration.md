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
- **인증**: `X-API-Key` 헤더 또는 JWT Bearer Token (`Authorization: Bearer <token>`)

## 제공 도구 (Tools)

| Tool | 설명 | API |
|------|------|-----|
| `pios_get_user_profile` | 대시보드 요약 조회 | `GET /api/dashboard/summary` |
| `pios_get_activities` | 활동 목록 조회 (기간/유형 필터) | `GET /api/activities` |
| `pios_get_activity_detail` | 특정 활동 상세 조회 | `GET /api/activities/{id}` |
| `pios_get_activity_laps` | 활동 구간(lap/split) 데이터 조회 | `GET /api/activities/{id}/laps` |
| `pios_get_health_metrics` | 건강 지표 조회 (RHR, HRV, 스트레스 등) | `GET /api/health/metrics` |
| `pios_get_sleep` | 수면 기록 조회 | `GET /api/health/sleep` |
| `pios_get_goals` | 목표 목록 조회 | `GET /api/goals` |
| `pios_get_insights` | 인사이트 목록 조회 | `GET /api/insights` |
| `pios_ask_coach` | 자연어 코칭 질문 (RAG) | `POST /api/ask` |
| `pios_get_graph` | 개인 지식 그래프 조회 | `GET /api/graph` |

## 설치 및 실행

### 1. API 키 발급

PIOS 웹앱의 **MCP 연동** 메뉴에서 AI 에이전트용 API 키를 발급받습니다.

1. 웹앱 로그인 → 좌측 사이드바 **MCP 연동** 클릭
2. 에이전트 이름 입력 (예: "내 노트북 Kimi") → **발행** 클릭
3. 발급된 `pios_xxx` 형식의 키를 복사하여 저장 (이 키는 한 번만 표시됨)

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
PIOS_API_KEY=pios_xxxxxxxxx \
    python server.py --transport stdio
```

## 클라이언트 연동

### Kimi Code CLI

```bash
kimi mcp add --transport http pios-coach \
  http://localhost:8001/mcp \
  --header "X-API-Key: pios_xxxxxxxx"
```

등록 후 `kimi mcp list`로 확인

### Claude Desktop

`claude_desktop_config.json` (macOS: `~/Library/Application Support/Claude/claude_desktop_config.json`):

```json
{
  "mcpServers": {
    "pios-coach": {
      "transport": "http",
      "url": "http://localhost:8001/mcp",
      "headers": {
        "X-API-Key": "pios_xxxxxxxx"
      }
    }
  }
}
```

### Claude Code

```bash
claude mcp add --transport http pios-coach \
  http://localhost:8001/mcp \
  --header "X-API-Key: pios_xxxxxxxx"
```

CLI에 `--header` 미지원 시 설정 파일 직접 수정:

```json
{
  "mcpServers": {
    "pios-coach": {
      "command": "npx",
      "args": ["-y", "@anthropic-ai/mcp-remote", "http://localhost:8001/mcp", "--header=X-API-Key:pios_xxxxxxxx"]
    }
  }
}
```

### Cursor

`~/.cursor/mcp.json`:

```json
{
  "mcpServers": {
    "pios-coach": {
      "transport": "http",
      "url": "http://localhost:8001/mcp",
      "headers": {
        "X-API-Key": "pios_xxxxxxxx"
      }
    }
  }
}
```

설정 저장 후 Cursor 재시작

### Codex CLI

```toml
[mcp_servers.pios-coach]
url = "http://localhost:8001/mcp"

[mcp_servers.pios-coach.env_http_headers]
"X-API-Key" = "PIOS_API_KEY"
```

파일 위치: `~/.codex/config.toml`

환경변수: `export PIOS_API_KEY=pios_xxxxxxxx`

## 보안 고려사항

- **API 키 관리**: 발급된 키는 서버에 BCrypt 해시로만 저장되며, 생성 직후에만 원본이 노출됩니다.
- **네트워크 격리**: Docker Compose 내에서는 `pios-network` 브리지 네트워크를 사용하여 백엔드와만 통신합니다.
- **streamable-http**: 프로덕션 환경에서는 반드시 reverse proxy(Caddy/nginx) + TLS를 적용하세요.

## 향후 확장

- [ ] `pios_create_goal` — 목표 생성 tool
- [ ] `pios_save_insight` — 인사이트 저장 tool
- [ ] `pios_sync_garmin` — Garmin 동기화 트리거 tool
- [ ] Resource 프로토콜 지원 (시계열 데이터를 MCP Resource로 노출)
