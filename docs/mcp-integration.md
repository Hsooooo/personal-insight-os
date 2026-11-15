# MCP 연동 가이드 — PIOS 생체스포츠 코치

## 개요

PIOS(Personal Insight OS)는 **Model Context Protocol(MCP)** 서버를 통해 외부 LLM 클라이언트(Claude Code, Claude Desktop/claude.ai, Cursor, Codex, Kimi 등)가 사용자의 건강·운동·수면 데이터를 안전하게 읽고, 사용자가 허락한 범위에서 목표/피드백 등을 기록할 수 있게 합니다.

MCP 서버는 **생체스포츠 코치(Bio-Sports Coach)** 페르소나를 가지며, Garmin 데이터를 기반으로 훈련/회복/수면 인사이트를 제공합니다.

- **SDK**: MCP Python SDK `mcp` 2.x (`MCPServer`). 최신 스펙 **2026-07-28**(stateless, `initialize`/세션 없음)과 2025년대 클라이언트(initialize 핸드셰이크)를 같은 엔드포인트에서 모두 지원합니다.
- **Transport**: `streamable-http`(원격, OAuth 2.1) 또는 `stdio`(로컬). 구 HTTP+SSE transport는 제거되었습니다.
- **인증(HTTP)**: OAuth 2.1 (PIOS 백엔드 = Authorization Server, MCP 서버 = Resource Server). 헤드리스 클라이언트는 `pios_` API 키를 Bearer로 사용할 수 있습니다. **무인증 fallback은 없습니다.**

## 아키텍처

```
                         (공개 인터넷, TLS)
[MCP 클라이언트] ──HTTPS──▶ [Caddy]
                              ├─ /mcp*                                   ──▶ [MCP Server :8001]  (Resource Server)
                              ├─ /.well-known/oauth-protected-resource*  ──▶ [MCP Server :8001]
                              ├─ /.well-known/oauth-authorization-server*──▶ [Backend :8080]     (Authorization Server)
                              ├─ /api/oauth/introspect                   ──▶ 404 (외부 차단)
                              └─ /api*                                   ──▶ [Backend :8080]

[MCP Server] ──(내부망) POST http://backend:8080/api/oauth/introspect  (X-PIOS-Internal-Secret)
             ──(내부망) GET/POST http://backend:8080/api/...           (Authorization: Bearer <같은 토큰> + X-PIOS-Internal-Secret)
```

- **MCP Server** (`mcp/`): Python Thin Proxy. `server.py`(엔트리포인트) + `pios_mcp/` 패키지
  - `config.py` 환경 변수 / `backend.py` 공유 httpx 클라이언트·오류 매핑 / `auth.py` 토큰 검증·미들웨어·PRM / `tools.py` 15개 도구 / `models.py` 구조화 출력 모델 / `app.py` ASGI 조립
- 백엔드는 OAuth access token(`aud` = `PIOS_MCP_RESOURCE_URL`)을 **유효한 내부 시크릿 헤더가 있을 때만** 받아들이고, MCP 주체(`ROLE_MCP`)는 정해진 API allowlist만 호출할 수 있습니다.

### OAuth 흐름

```
클라이언트                         MCP Server (RS)                    Backend (AS)                 브라우저(사용자)
   │ POST /mcp (토큰 없음)            │                                  │                              │
   │────────────────────────────────▶│                                  │                              │
   │ 401 WWW-Authenticate: Bearer ... resource_metadata="https://<host>/.well-known/oauth-protected-resource/mcp"
   │◀────────────────────────────────│                                  │                              │
   │ GET /.well-known/oauth-protected-resource/mcp                      │                              │
   │────────────────────────────────▶│ {resource, authorization_servers:[https://<host>], scopes_supported}
   │ GET /.well-known/oauth-authorization-server ──────────────────────▶│                              │
   │ POST /api/oauth/register (DCR) 또는 Client ID Metadata Document ──▶│                              │
   │ 브라우저 열기: /api/oauth/authorize?...&code_challenge(S256)&resource=https://<host>/mcp ─────────▶│
   │                                  │                                  │ 302 → /oauth/consent ───────▶│ 로그인 + 승인
   │◀──────────────────────── redirect_uri?code=...&state=...&iss=... ───────────────────────────────│
   │ POST /api/oauth/token (code + code_verifier + resource) ──────────▶│ access_token(JWT 1h) + refresh_token
   │ POST /mcp  Authorization: Bearer <access_token>                    │                              │
   │────────────────────────────────▶│ introspect (≤60s 캐시) ─────────▶│                              │
   │                                  │ /api/... (Bearer + 내부 시크릿) ▶│                              │
   │◀──────────── tool 결과 ──────────│                                  │                              │
```

### 메타데이터 URL

| 문서 | URL |
|------|-----|
| Protected Resource Metadata (RFC 9728) | `https://<host>/.well-known/oauth-protected-resource/mcp` (루트 `/.well-known/oauth-protected-resource`도 동일 응답) |
| Authorization Server Metadata (RFC 8414) | `https://<host>/.well-known/oauth-authorization-server` |
| MCP 엔드포인트 (resource) | `https://<host>/mcp` (`/mcp/`도 리다이렉트 없이 동일 처리) |

PRM 응답 예:

```json
{
  "resource": "https://<host>/mcp",
  "authorization_servers": ["https://<host>"],
  "scopes_supported": ["mcp:read", "mcp:write"],
  "bearer_methods_supported": ["header"],
  "resource_name": "PIOS Coach",
  "resource_documentation": "https://<host>/mcp-settings"
}
```

### 스코프

| Scope | 허용 범위 |
|-------|-----------|
| `mcp:read` | 모든 조회 도구 + `pios_ask_coach` (MCP 엔드포인트 접근 자체에 필요) |
| `mcp:write` | 목표 생성/수정, 인사이트 저장/피드백, Garmin 동기화, 주간 브리핑 생성 |

스코프를 지정하지 않고 승인하면 `mcp:read mcp:write`가 부여됩니다. `mcp:read`만 가진 토큰으로 쓰기 도구를 호출하면 HTTP 오류가 아니라 **tool error(`isError: true`)** 로 "mcp:write 권한이 필요합니다" 메시지를 반환합니다. API 키는 항상 `mcp:read mcp:write`입니다.

## 제공 도구 (Tools)

모든 도구는 **사람이 읽는 한국어 코치 스타일 텍스트**(`content`)와 함께 **구조화 데이터**(`structuredContent`, 도구 목록에 `outputSchema` 공개)를 반환합니다. 도구에는 `title`과 `annotations`(`readOnlyHint`/`destructiveHint`/`idempotentHint`/`openWorldHint`)가 있습니다.

| Tool | 설명 | API | Scope | 힌트 |
|------|------|-----|-------|------|
| `pios_get_user_profile` | 대시보드 요약 (최신 건강/수면/활동, 목표, 인사이트) | `GET /api/dashboard/summary` | read | read-only |
| `pios_get_activities` | 활동 목록 (기간/유형/페이지, 기본 최근 30일) | `GET /api/activities` | read | read-only |
| `pios_get_activity_detail` | 활동 상세 | `GET /api/activities/{id}` | read | read-only |
| `pios_get_activity_laps` | 활동 구간(lap/split) | `GET /api/activities/{id}/laps` | read | read-only |
| `pios_get_health_metrics` | 일일 건강 지표 (기본 최근 7일, 최대 366일) | `GET /api/health/metrics` | read | read-only |
| `pios_get_sleep` | 수면 기록 (기본 최근 7일, 최대 366일) | `GET /api/health/sleep` | read | read-only |
| `pios_get_goals` | 목표 + 진행률 | `GET /api/goals` | read | read-only |
| `pios_get_insights` | 인사이트 목록 (category/feedback_status/limit) | `GET /api/insights` | read | read-only |
| `pios_ask_coach` | 자연어 코칭 질문 (RAG, 결과는 인사이트로 저장) | `POST /api/ask` | read | 비멱등 |
| `pios_get_graph` | 개인 지식 그래프 (days 0=전체, view) | `GET /api/graph` | read | read-only |
| `pios_create_or_update_goal` | 목표 생성(POST) / 수정(PATCH, 바꿀 필드만). 레이스는 `goal_type=RACE` + `race_distance_km` + `target_value`(초) + `target_date` | `POST /api/goals`, `PATCH /api/goals/{id}` | write | 비파괴, 비멱등 |
| `pios_save_insight` | 인사이트 저장 | `POST /api/insights/{id}/save` | write | 멱등 |
| `pios_submit_feedback` | 인사이트 피드백 (CORRECT/UNCLEAR/WRONG/IMPORTANT) | `POST /api/insights/{id}/feedback` | write | 멱등 |
| `pios_trigger_sync` | Garmin 동기화 (INCREMENTAL/FULL, 30초 rate limit) | `POST /api/data-sources/garmin/sync` | write | open-world |
| `pios_generate_briefing` | 주간 브리핑 생성 (`force=true`면 같은 주 브리핑 대체) | `POST /api/briefings/generate` | write | destructive |

삭제 도구는 제공하지 않습니다 (목표 종료는 `status=COMPLETED/CANCELLED`).

### 입력 검증과 오류

- 날짜는 `YYYY-MM-DD`만 허용합니다. 형식 오류/존재하지 않는 날짜/시작>종료/최대 기간 초과는 조용히 무시하지 않고 tool error로 반환합니다.
- 기간 미지정 시 기본값은 **`PIOS_TIMEZONE`(기본 Asia/Seoul) 기준 오늘 포함 최근 N일**입니다.
- `size`(1~100), `page`(0~10000), `limit`(1~50), `days`(0~3650), enum 인자는 스키마로 검증됩니다.
- 백엔드 응답 매핑: 401 → 재인증 안내, 403 → 권한 부족, 404 → 데이터 없음, 429 → rate limit(`Retry-After` 반영), 5xx/네트워크 오류/타임아웃 → 일시 장애 안내. 모두 `isError: true` 결과이며 raw 예외는 노출하지 않습니다.
- 토큰 검증 중 백엔드(introspect)에 연결할 수 없으면 401 대신 **503 + `Retry-After: 5`** 를 반환해 클라이언트가 토큰을 폐기하지 않게 합니다.

### `pios_ask_coach` 응답 형식

텍스트(`content`) 예시:

```text
🧠 코치의 답변

최근 HRV 저하와 수면 시간 감소가 함께 관찰됩니다.

📅 분석 기간: 2026-06-14 ~ 2026-06-20
📊 기준선 기간: 2026-05-17 ~ 2026-06-13

📎 근거:
- 평균 HRV: 최근 7일 평균 42ms (기준선보다 12% 낮음)
    현재 42ms / 기준선 48ms / 12.0% 감소

🎯 신뢰도: HIGH (82%)
  판정 이유:
  - 분석 기간 데이터 7일 확보
  - 28일 기준선 비교 가능

💡 추가 질문:
- 수면과 HRV 변화를 날짜별로 비교해줘

🏷️ 의도: CONDITION
```

`structuredContent`: `{question, answer, intent, question_id, insight_id, period, confidence{level, score, reasons}, evidences[], follow_up_questions[]}`.

## 설치 및 실행

### 1. Docker Compose (권장)

```bash
# .env: PIOS_PUBLIC_BASE_URL=https://<host>, PIOS_MCP_INTERNAL_SECRET=<32자 이상 랜덤>
docker compose up -d --build
```

- MCP 서버는 컨테이너 내부 `:8001`에서 동작하고, 외부에는 Caddy를 통해 `https://<host>/mcp`로만 노출됩니다 (호스트 포트는 `127.0.0.1:8001` 로컬 전용).
- 컨테이너는 비루트 사용자(uid 10001)로 실행되며 `python:3.12.13-slim` 기반입니다.
- 헬스체크: `GET /healthz` → `200 {"status":"ok"}` (무인증, 데이터 없음). `GET /.well-known/oauth-protected-resource`도 200을 반환합니다. `/mcp`는 토큰 없이 401이므로 헬스체크로 쓰지 마세요.

### 2. 로컬 Python — Streamable HTTP

```bash
cd mcp
python -m venv .venv && . .venv/bin/activate
pip install -r requirements.txt
PIOS_API_URL=http://localhost:8080/api \
PIOS_PUBLIC_BASE_URL=http://localhost:8080 \
PIOS_MCP_RESOURCE_URL=http://localhost:8001/mcp \
PIOS_MCP_INTERNAL_SECRET=<backend와 같은 값> \
    python server.py --transport streamable-http --host 127.0.0.1 --port 8001
```

> 로컬에서 OAuth를 테스트하려면 백엔드의 `PIOS_MCP_RESOURCE_URL`도 같은 값이어야 합니다 (토큰 `aud` 검증).

### 3. 로컬 Python — stdio

stdio에서는 OAuth를 쓰지 않고 환경 변수 `PIOS_API_KEY`(`pios_` API 키)를 사용합니다. `PIOS_MCP_INTERNAL_SECRET`은 선택입니다 (백엔드는 API 키를 내부 시크릿 없이도 허용).

```bash
cd mcp
pip install -r requirements.txt
PIOS_API_URL=http://localhost:8080/api PIOS_API_KEY=pios_xxxxxxxx \
    python server.py --transport stdio
```

### 테스트

```bash
cd mcp
pip install -r requirements-dev.txt
python -m pytest -q
```

## 클라이언트 연동

아래에서 `<host>`는 PIOS 공개 도메인(예: `personal-insight.kro.kr`)입니다.

### 기본: OAuth (URL만 등록 → 브라우저 로그인)

OAuth를 지원하는 클라이언트는 URL만 등록하면 됩니다. 첫 연결 시 클라이언트가 브라우저를 열고, PIOS 로그인 → 동의 화면(`/oauth/consent`)에서 **승인**하면 연결됩니다. 연결된 앱은 PIOS 웹 **MCP 연동** 화면의 "Connected apps (OAuth)"에서 확인·해제할 수 있습니다.

#### Claude Code

```bash
claude mcp add --transport http pios-coach https://<host>/mcp
```

Claude Code 세션에서 `/mcp` → `pios-coach` → **Authenticate**를 선택하면 브라우저가 열립니다.

#### Claude Desktop / claude.ai (Custom Connector)

**Settings → Connectors → Add custom connector**에서 이름 `PIOS Coach`, URL `https://<host>/mcp`를 입력하고 **Connect**를 누르면 브라우저 로그인/승인 화면으로 이동합니다. (claude.ai 커넥터는 Anthropic 서버에서 접속하므로 공개 HTTPS 주소가 필요합니다.)

#### Cursor

`~/.cursor/mcp.json` (또는 프로젝트 `.cursor/mcp.json`):

```json
{
  "mcpServers": {
    "pios-coach": { "url": "https://<host>/mcp" }
  }
}
```

저장 후 Cursor 설정의 MCP 목록에서 `pios-coach`의 **Login/Connect**를 눌러 인증합니다.

#### Codex CLI

`~/.codex/config.toml`:

```toml
[mcp_servers.pios-coach]
url = "https://<host>/mcp"
```

```bash
codex mcp login pios-coach   # 브라우저에서 승인
```

(Codex 버전에 따라 원격 MCP OAuth 사용 시 `experimental_use_rmcp_client = true` 설정이 필요할 수 있습니다.)

#### Kimi Code CLI

```bash
kimi mcp add --transport http --auth oauth pios-coach https://<host>/mcp
kimi mcp auth pios-coach     # 브라우저에서 승인
```

CLI 버전별 옵션은 `kimi mcp add --help`로 확인하세요.

### 보조: API 키 (헤드리스/CI/OAuth 미지원 클라이언트)

PIOS 웹앱 **MCP 연동** 메뉴에서 API 키를 발급합니다 (`pios_xxx`, 생성 직후 한 번만 표시). 요청에 다음 중 하나를 보냅니다.

- `Authorization: Bearer pios_xxxxxxxx` (권장)
- `X-API-Key: pios_xxxxxxxx` (구버전 호환 — 서버가 `Authorization: Bearer`로 변환)

```bash
# Claude Code
claude mcp add --transport http pios-coach https://<host>/mcp \
  --header "Authorization: Bearer pios_xxxxxxxx"

# Kimi Code CLI
kimi mcp add --transport http pios-coach https://<host>/mcp \
  --header "Authorization: Bearer pios_xxxxxxxx"
```

Cursor (`~/.cursor/mcp.json`):

```json
{
  "mcpServers": {
    "pios-coach": {
      "url": "https://<host>/mcp",
      "headers": { "Authorization": "Bearer pios_xxxxxxxx" }
    }
  }
}
```

Codex (`~/.codex/config.toml`, 키는 환경 변수 `PIOS_API_KEY`에서 읽음):

```toml
[mcp_servers.pios-coach]
url = "https://<host>/mcp"
bearer_token_env_var = "PIOS_API_KEY"
```

Claude Desktop의 `claude_desktop_config.json`은 로컬(stdio) 서버만 직접 지원하므로, API 키 방식은 `mcp-remote` 브리지 또는 아래 stdio 방식을 사용합니다.

```json
{
  "mcpServers": {
    "pios-coach": {
      "command": "npx",
      "args": ["-y", "mcp-remote", "https://<host>/mcp", "--header", "Authorization:Bearer ${PIOS_API_KEY}"],
      "env": { "PIOS_API_KEY": "pios_xxxxxxxx" }
    }
  }
}
```

### stdio (로컬 실행)

저장소의 `mcp/mcp.json`에 예시가 있습니다.

```json
{
  "mcpServers": {
    "pios-coach": {
      "command": "python",
      "args": ["mcp/server.py", "--transport", "stdio"],
      "env": {
        "PIOS_API_URL": "http://localhost:8080/api",
        "PIOS_API_KEY": "pios_xxxxxxxx"
      }
    }
  }
}
```

## 환경 변수

| 변수 | 기본값 | 설명 |
|------|--------|------|
| `PIOS_API_URL` | `http://localhost:8080/api` | 백엔드 API base (Docker: `http://backend:8080/api`) |
| `PIOS_PUBLIC_BASE_URL` | `http://localhost:8080` | 공개 base URL = OAuth issuer (끝 `/` 제거) |
| `PIOS_MCP_RESOURCE_URL` | `${PIOS_PUBLIC_BASE_URL}/mcp` | Resource 식별자. 백엔드 토큰 `aud`와 **정확히 일치**해야 함. path가 MCP 엔드포인트 경로가 됨 |
| `PIOS_MCP_INTERNAL_SECRET` | (없음, HTTP 모드 필수) | backend↔MCP 공유 시크릿(32자 이상). introspect 및 API 호출 시 `X-PIOS-Internal-Secret`로 전송 |
| `PIOS_INTROSPECT_URL` | `${PIOS_API_URL}/oauth/introspect` | introspect 엔드포인트 재정의 |
| `PIOS_API_KEY` | (없음) | **stdio 전용** 자격증명 (`pios_` API 키). HTTP 모드에서는 사용하지 않음 |
| `PIOS_TIMEZONE` | `Asia/Seoul` | 기본 조회 기간 계산 타임존 |
| `PIOS_MCP_TRUST_PROXY` | `true` | `X-Forwarded-For` 첫 hop(없으면 `X-Real-IP`)을 클라이언트 IP로 사용 (백엔드 감사용 `X-MCP-Client-IP`) |
| `PIOS_MCP_ALLOWED_HOSTS` | (없음) | Host 헤더 허용 목록 추가 (콤마 구분, `host:*` 와일드카드 포트) |
| `PIOS_MCP_ALLOWED_ORIGINS` | (없음) | Origin 허용 목록 추가 |
| `PIOS_MCP_TOKEN_CACHE_TTL` | `60` | introspection 양성 캐시 TTL(초, 최대 60) |
| `PIOS_MCP_HTTP_TIMEOUT` | `30` | 백엔드 호출 기본 타임아웃(초). ask/브리핑/동기화는 90~120초 |
| `PIOS_MCP_LOG_LEVEL` | `INFO` | 로그 레벨 (로그는 stderr, 토큰 값은 기록하지 않음) |

> mcp SDK 2.x는 `MCP_*` 환경 변수를 읽지 않습니다. 모든 설정은 위 `PIOS_*` 변수로 합니다.

허용 Host 기본값: `PIOS_PUBLIC_BASE_URL`/`PIOS_MCP_RESOURCE_URL`의 host(및 host:port), `mcp`, `mcp:8001`, `pios-mcp:8001`, `localhost[:*]`, `127.0.0.1[:*]`, `[::1][:*]`. 그 외 Host로 `/mcp`에 접근하면 **421**(DNS rebinding 방어).

## 보안 고려사항

- **무인증 접근 없음**: HTTP 모드에서 `/mcp`는 항상 유효한 Bearer 토큰(OAuth access token 또는 API 키)을 요구합니다. 토큰이 없거나 무효/만료/다른 리소스용(`aud` 불일치)이면 `401` + `WWW-Authenticate: Bearer ..., resource_metadata="..."`.
- **토큰 검증**: 매 요청 backend introspect로 검증하고 양성 결과는 최대 60초(토큰 `exp` 이내), 음성 결과는 5초만 캐시합니다. 따라서 연결 해제/키 폐기는 최대 60초 내 반영됩니다. 캐시 키는 토큰의 SHA-256입니다.
- **Audience 바인딩**: OAuth access token은 `aud == PIOS_MCP_RESOURCE_URL`일 때만 통과합니다 (토큰 passthrough 방지). 백엔드도 내부 시크릿이 있는 요청에서만 OAuth 토큰을 받아들입니다.
- **내부 시크릿**: `PIOS_MCP_INTERNAL_SECRET`은 backend와 MCP 컨테이너에만 둡니다. `/api/oauth/introspect`는 Caddy에서 외부 차단됩니다.
- **최소 권한**: MCP 주체는 백엔드 allowlist API만 호출 가능하며, 쓰기 도구는 `mcp:write` 스코프가 필요합니다. 삭제 도구는 없습니다.
- **API 키**: 서버에 해시로만 저장되며 생성 직후에만 원본이 노출됩니다. 유출 시 MCP 연동 화면에서 즉시 폐기하세요.
- **전송 보안**: Host/Origin 검증(DNS rebinding 방어), 요청 본문 4 MiB 제한(SDK 기본), 컨테이너 비루트 실행, 공개 노출은 Caddy TLS 경유만.
- **로그**: 접근 로그에 토큰·API 키 값을 남기지 않습니다.
