# Personal Insight OS

> 개인의 건강, 운동, 기록 데이터를 도메인별 노드와 관계 그래프로 구조화하고, LLM 기반 RAG를 통해 근거 있는 개인화 인사이트를 제공하는 웹서비스

## Architecture

```
[브라우저 / MCP 클라이언트] ──HTTPS──▶ [Caddy :80/:443]
                                        ├─ /api*, /.well-known/oauth-authorization-server* ─▶ [Spring Boot 3.5 :8080]
                                        ├─ /mcp*, /.well-known/oauth-protected-resource*   ─▶ [MCP Server :8001]
                                        └─ 그 외                                            ─▶ [React SPA (nginx) :8080]

[Spring Boot] ──▶ PostgreSQL + pgvector, Neo4j(선택), OpenAI 호환 API(선택), Open-Meteo
[MCP Server]  ──(내부망 + 내부 시크릿)──▶ [Spring Boot]
```

자세한 구조와 OAuth 흐름은 [docs/architecture.md](docs/architecture.md)를 참고하세요.

## Quick Start

### Prerequisites
- Docker & Docker Compose
- (선택) OpenAI API Key, Neo4j 인스턴스 (`NEO4J_*`)

### Environment

```bash
cp .env.example .env
```

`.env`의 **필수** 값 (누락 시 `docker compose`가 기동을 거부합니다):

| 변수 | 설명 |
|------|------|
| `JWT_SECRET` | `openssl rand -base64 48` |
| `PIOS_ENCRYPTION_KEY` | 저장 시크릿 암호화 키 (`JWT_SECRET`과 다른 값) |
| `PIOS_MCP_INTERNAL_SECRET` | backend ↔ MCP 내부 시크릿 (32자 이상) |
| `POSTGRES_PASSWORD` | DB 비밀번호 |
| `PIOS_PUBLIC_BASE_URL` | 외부 접근 URL, OAuth issuer (예: `https://pios.example.com`, 개발: `https://localhost`) |
| `PIOS_CORS_ALLOWED_ORIGINS` | CORS 허용 Origin (운영: `PIOS_PUBLIC_BASE_URL`과 동일) |

- `PIOS_DOMAIN`은 Caddy 도메인 (기본 `localhost`).
- 회원가입은 기본 비활성화입니다. 첫 계정을 만들 때만 `PIOS_REGISTRATION_ENABLED=true`로 기동하세요.
- 암호화 키 교체: 이전 값을 `PIOS_ENCRYPTION_KEY_LEGACY`에 넣고 기동하면 재암호화됩니다 ([운영 가이드](docs/operations.md)).
- 전체 환경변수 표는 [AGENTS.md](AGENTS.md#10-환경-변수) 참고.

### Run (Docker Compose)

```bash
docker compose up -d --build
```

- 외부 진입점은 Caddy(80/443) 하나입니다. frontend 컨테이너(nginx-unprivileged)는 8080에서 정적 파일만 서빙하며 `/api`를 프록시하지 않습니다.
- Postgres(5432)와 MCP(8001)는 `127.0.0.1`에만 바인딩됩니다.
- 웹 앱: `https://<PIOS_DOMAIN>` (개발 기본 `https://localhost`, Caddy 내부 CA)

### MCP 연결

MCP 클라이언트(Claude, Cursor, Codex 등)에는 `https://<PIOS_DOMAIN>/mcp` URL만 등록하면 OAuth 2.1 로그인/승인 화면(`/oauth/consent`)으로 연결됩니다. 헤드리스 환경은 웹의 "MCP 연동" 화면에서 `pios_` API 키를 발급해 `Authorization: Bearer`로 사용합니다. 자세한 내용은 [docs/mcp-integration.md](docs/mcp-integration.md).

### Local Development

```bash
docker compose up -d postgres                                              # DB만 컨테이너로
cd backend && SPRING_PROFILES_ACTIVE=local mvn spring-boot:run             # :8080 (local 프로필: 개발용 기본 시크릿)
cd frontend && npm install && npm run dev                                  # :5173 (/api → :8080 프록시)
```

자세한 내용은 [docs/getting-started.md](docs/getting-started.md).

## MVP Features

- [x] 사용자 로그인 (JWT)
- [x] Garmin 데이터 연동 (Mock 데이터 생성)
- [x] Garmin 데이터 동기화
- [x] PostgreSQL 원천 데이터 저장
- [x] 도메인 모델 변환
- [x] Neo4j 노드/엣지 생성
- [x] 기본 대시보드 (차트, 요약)
- [x] 개인 그래프 조회 (Cytoscape) — 날짜/뷰/레이스 필터 지원
- [x] 웨이트 트레이닝 종목명 선택 (기존 목록 + 신규 입력)
- [x] AI 운동 요약 (이번주 운동 정리)
- [x] LLM Provider API Key 등록
- [x] Ask My Data 자연어 질의
- [x] 근거 기반 RAG 응답
- [x] 인사이트 저장
- [x] 인사이트 피드백
- [x] 목표 진행률, 주간 브리핑, 이상 신호 알림
- [x] 가계(Finance) 관리
- [x] MCP 연동 (OAuth 2.1 + API 키), 연결된 앱 관리

## Tech Stack

### Backend
- Spring Boot 3.5 + Java 21
- Spring Security + JWT + OAuth 2.1 Authorization Server
- Spring Data JPA, Flyway (V1~V26)
- Neo4j Java Driver
- OpenAI 호환 REST (`LlmClient`)

### Frontend
- React 19 + TypeScript, React Router 7
- Vite 8, ESLint, Vitest
- Tailwind CSS
- shadcn/ui (custom)
- Recharts (charts)
- Cytoscape.js + cytoscape-fcose (graph visualization)
- TanStack Query
- Zustand
- Pretendard (font)

### Infrastructure
- MCP 서버: Python + mcp SDK 2.x (OAuth Resource Server)
- PostgreSQL 15 + pgvector 0.5.1
- Neo4j (외부/클라우드 인스턴스)
- Docker Compose + Caddy

## Project Structure

```
.
├── docker-compose.yml, Caddyfile, .env.example
├── backend/             # Spring Boot (config, controller, domain, dto, repository, security, oauth, service/*), scripts/garmin_sync.py
├── frontend/            # React + Vite (pages, components/<page>/, lib, stores, types)
├── mcp/                 # MCP 서버 (server.py, pios_mcp/, tests/)
├── scripts/ops/         # Postgres 백업/복구
└── docs/                # 설계/운영 문서 (docs/archive: 초기 기획 초안)
```

상세 트리는 [docs/project-structure.md](docs/project-structure.md).

## Screens

1. **Dashboard** — 요약 카드, 7일 트렌드 차트, 최근 인사이트, 빠른 질문
2. **Data Sources** — Garmin 연결/동기화
3. **Activities** — 운동 목록 및 필터
4. **Health Timeline** — 수면, 심박, 스트레스, 걸음수 차트
5. **Personal Graph** — Neo4j 그래프 시각화 (Cytoscape) — 날짜/활동/컨디션/레이스 필터
6. **Ask My Data** — 자연어 질의 + 근거 기반 답변
7. **Insights** — 인사이트 목록, 저장, 피드백
8. **Goals** — 목표 설정 및 관리
9. **Finance** — 가계 거래/계좌/고정 지출
10. **MCP 연동** — OAuth 연결 가이드, 연결된 앱, API 키
11. **Settings** — LLM Provider 설정
12. **OAuth Consent** (`/oauth/consent`) — 외부 앱 연결 승인

## Development

### Tests & CI
```bash
cd backend && mvn -B test                       # JUnit (JDK 21 없으면 AGENTS.md §8의 Docker 명령)
cd frontend && npm run lint && npm test && npm run build
cd mcp && pip install -r requirements-dev.txt && pytest tests
```

GitHub Actions(`.github/workflows/ci.yml`)가 위 테스트, 스크립트 컴파일, 이미지 빌드, compose/Caddyfile 검증을 수행합니다.

### 운영
백업/복구, 시크릿 교체, 업그레이드는 [docs/operations.md](docs/operations.md), 변경 이력은 [CHANGELOG.md](CHANGELOG.md).

### API Documentation

All APIs return `ApiResponse<T>`:
```json
{
  "success": true,
  "message": null,
  "data": { ... }
}
```

Authentication: `Authorization: Bearer <token>` (웹 JWT, OAuth access token, 또는 `pios_` API 키). 오류 규약은 [AGENTS.md](AGENTS.md#오류-규약) 참고.

## License

MIT
