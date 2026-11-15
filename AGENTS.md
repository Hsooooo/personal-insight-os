# AGENTS.md — Personal Insight OS

> 이 파일은 AI 코딩 에이전트가 프로젝트를 이해하고 작업할 때 참고해야 하는 핵심 정보를 담고 있습니다. 프로젝트의 모든 문서와 코드는 주로 한국어를 기준으로 작성되었습니다.

---

## 1. 프로젝트 개요

**Personal Insight OS (PIOS)**는 개인의 건강, 운동, 기록 데이터를 도메인별 노드와 관계 그래프로 구조화하고, LLM 기반 RAG를 통해 근거 있는 개인화 인사이트를 제공하는 웹서비스입니다.

- 사용자의 Garmin 데이터를 수집하여 PostgreSQL에 원천/정형 데이터를 저장합니다.
- 수집된 데이터를 도메인 모델(Activity, Sleep, HealthMetric 등)로 변환하여 Neo4j 그래프에 노드/엣지를 생성합니다.
- 사용자가 자연어로 질문하면 최근 7~30일 데이터를 기반으로 LLM(OpenAI 호환 API, 기본 gpt-4o-mini)이 근거 기반 답변을 생성합니다.
- 생성된 인사이트는 저장하고 피드백(CORRECT, UNCLEAR, WRONG, IMPORTANT)할 수 있습니다.

### MVP 기능
- JWT(access) + httpOnly refresh 쿠키 기반 로그인 (회원가입은 `PIOS_REGISTRATION_ENABLED`로 제어)
- Garmin 데이터 연동 및 동기화 (Mock 데이터 생성은 `PIOS_MOCK_DATA_ENABLED`)
- 대시보드 (주간 브리핑, 요약 카드, 7일 트렌드 차트, 목표 진행률, 최근 인사이트)
- 활동/건강/수면 데이터 조회, 가계(Finance) 관리
- 개인 지식 그래프 시각화 (Cytoscape) — 날짜/뷰/레이스 필터, 표 보기(접근성)
- 자연어 질의 (Ask My Data) + RAG 응답
- 인사이트 저장 및 피드백
- 목표 설정 및 실제 데이터 기반 진행률 추적
- 자동 주간 브리핑, 능동 이상 신호 알림
- LLM Provider 설정
- MCP(Model Context Protocol) 연동 — OAuth 2.1로 외부 LLM이 PIOS 데이터를 읽도록 생체스포츠 코치 페르소나 제공, 연결된 앱 관리, API 키(`pios_...`)

---

## 2. 기술 스택

### Backend
- **Spring Boot 3.5.x** (parent 3.5.16) + **Java 21**
- **Spring Security** + JWT (jjwt) + 자체 **OAuth 2.1 Authorization Server** (`com.pios.oauth`)
- **Spring Data JPA** (Hibernate), **Flyway** — 마이그레이션 V1~V27 (`backend/src/main/resources/db/migration`)
- **Neo4j Java Driver** — 그래프 데이터 접근 (선택)
- **LlmClient** (`service/llm`) — OpenAI 호환 REST 호출 (OpenAI Java SDK 사용 안 함)
- **Apache POI** — 가계 엑셀 import
- **Lombok**, **Maven**, Spring Actuator (`/actuator/health`)

### Frontend
- **React 19** + **TypeScript 5.9**, **Vite 8**
- **Tailwind CSS 3.4**, shadcn/ui (custom, Radix UI)
- **Recharts 3**, **Cytoscape.js + cytoscape-fcose**
- **TanStack Query 5** (서버 상태), **Zustand 5** (Auth Store), **React Router 7** (`react-router-dom`), 페이지는 `lazy` 라우트
- **ESLint 10** + **Vitest 5** (+ Testing Library), lucide-react, sonner, react-markdown

### MCP 서버
- Python 3.12, **mcp SDK 2.x (`MCPServer`)**, Starlette/uvicorn, httpx, pydantic
- **OAuth 2.1 Resource Server**: 토큰은 백엔드 introspect로 검증. 코드는 `mcp/pios_mcp/`(config/backend/auth/tools/models/app), 엔트리포인트 `mcp/server.py`, 테스트 `mcp/tests/` (pytest)

### Infrastructure
- **PostgreSQL + pgvector** (이미지 digest 고정), **Neo4j** (외부/클라우드 인스턴스)
- **Docker Compose** — postgres, backend, frontend(nginx-unprivileged :8080), mcp, caddy
- **Caddy** — 리버스 프록시 + 자동 HTTPS (유일한 외부 진입점). 라우팅은 `Caddyfile` 참고
- **Python 3 + garminconnect** — Garmin 수집 (backend 이미지에 포함, backend 컨테이너는 비root uid 10001)
- CI: `.github/workflows/ci.yml` (backend/frontend/mcp 테스트, 이미지 빌드, compose/Caddyfile 검증)

---

## 3. 프로젝트 구조

```
.
├── docker-compose.yml          # postgres, backend, frontend, mcp, caddy
├── Caddyfile                   # 라우팅/보안 헤더 (/api, /mcp, /.well-known/*, 나머지는 frontend)
├── .env.example                # 필수/선택 환경변수 템플릿
├── scripts/ops/                # backup-postgres.sh, restore-postgres.sh
├── docs/                       # 설계/운영 문서 (operations.md, mcp-integration.md 포함)
├── CHANGELOG.md
├── mcp/                        # MCP Server (Python)
│   ├── server.py               # 엔트리포인트 (--transport stdio|streamable-http)
│   ├── pios_mcp/               # config.py, backend.py, auth.py, tools.py, models.py, app.py
│   ├── tests/                  # pytest
│   ├── requirements.txt / requirements-dev.txt
│   └── Dockerfile, mcp.json
├── backend/                    # Spring Boot
│   ├── pom.xml, Dockerfile
│   ├── scripts/garmin_sync.py  # Garmin 수집 스크립트 (ProcessBuilder로 실행)
│   └── src/main/java/com/pios/
│       ├── common/             # NotFoundException, AppTimeZones
│       ├── config/             # AppConfig, AsyncConfig, GlobalExceptionHandler, SecretMigrationRunner 등
│       ├── controller/         # REST Controller (/api/*)
│       ├── domain/ (+enums)    # JPA Entity
│       ├── dto/ (+finance)     # Request/Response DTO
│       ├── repository/, spec/  # Spring Data JPA, Specification
│       ├── oauth/              # controller/domain/dto/repository/service (Authorization Server)
│       ├── security/           # SecurityConfig, JwtAuthenticationFilter, McpAccessPolicy, InternalSecretVerifier, LoginAttemptService ...
│       └── service/            # 비즈니스 로직 + 하위 패키지 alert/ ask/ briefing/ finance/ garmin/ goal/ llm/ pattern/
│   └── src/main/resources/     # application.yml, application-local.yml, db/migration/V1~V27
│   └── src/test/java/com/pios/ # JUnit 5 (controller/oauth/security/service)
└── frontend/                   # React + Vite
    ├── Dockerfile, nginx.conf  # 정적 SPA만 서빙 (:8080). /api 프록시 없음
    ├── vite.config.ts          # @/ alias, dev 프록시(/api → localhost:8080), vitest 설정
    └── src/
        ├── App.tsx             # 인증 라우팅, lazy 페이지
        ├── pages/              # Dashboard, DataSources, Activities, Health, Finance, Graph, Ask, Insights, Goals, McpSettings, Settings, Login, OAuthConsent
        ├── components/         # ui/, layout/, common/, 페이지별 폴더(activities, ask, auth, dashboard, data-sources, finance, graph, mcp, oauth)
        ├── lib/                # api.ts, apiClient.ts, oauth.ts, utils.ts 등
        ├── stores/authStore.ts, hooks/, types/index.ts, styles/globals.css
```

---

## 4. 빌드 및 실행 명령어

### 전체 시스템 (Docker Compose)
```bash
cp .env.example .env
# 필수 값 채우기: JWT_SECRET, PIOS_ENCRYPTION_KEY, PIOS_MCP_INTERNAL_SECRET, POSTGRES_PASSWORD,
#               PIOS_PUBLIC_BASE_URL, PIOS_CORS_ALLOWED_ORIGINS (누락 시 compose가 즉시 실패)
docker compose up -d --build
```
- 외부 진입점: Caddy (`https://${PIOS_DOMAIN}`, 개발 기본 `https://localhost`)
- Postgres(5432), MCP(8001)는 `127.0.0.1`에만 바인딩. backend/frontend는 호스트에 노출하지 않음

### Backend 단독 실행
```bash
docker compose up -d postgres
cd backend
SPRING_PROFILES_ACTIVE=local mvn spring-boot:run     # :8080, application-local.yml의 개발용 기본값 사용
```
`local` 프로필 없이 실행하면 `SPRING_DATASOURCE_PASSWORD`, `JWT_SECRET`, `PIOS_ENCRYPTION_KEY`, `PIOS_MCP_INTERNAL_SECRET`, `SPRING_NEO4J_URI` 등이 없어 기동 실패합니다.

### Frontend 단독 실행
```bash
cd frontend
npm install
npm run dev        # http://localhost:5173 (/api → localhost:8080 프록시)
npm run lint
npm test           # vitest run
npm run build      # tsc + vite build
```

---

## 5. 아키텍처 및 데이터 흐름

### 런타임 아키텍처
```
[브라우저 / MCP 클라이언트] ──HTTPS──→ [Caddy]
                                         ├─ /api*, /.well-known/oauth-authorization-server* → [Spring Boot :8080]
                                         ├─ /mcp*, /.well-known/oauth-protected-resource*   → [MCP Server :8001]
                                         ├─ /api/oauth/introspect*                          → 404 (외부 차단)
                                         └─ 그 외                                            → [frontend nginx :8080]
[Spring Boot] ──JDBC──→ [PostgreSQL + pgvector]
              ├──Bolt──→ [Neo4j]
              └──HTTPS──→ [OpenAI 호환 API], [Open-Meteo 날씨]
[MCP Server] ──내부망 + X-PIOS-Internal-Secret──→ [Spring Boot] (introspect, /api/*)
```

### 핵심 데이터 흐름

**1. RAG 파이프라인 (Ask My Data)**
1. `POST /api/ask` → 질문 저장 (`questions`)
2. 의도/기간 분류 후 건강 지표·수면·활동 등 근거 조회 (PostgreSQL), 유사 인사이트(pgvector) 조회
3. `LlmClient` 호출 — 사용자 LLM Provider 키를 먼저 사용하고 없으면 전역 `OPENAI_API_KEY`로 fallback, 둘 다 없거나 실패하면 템플릿 응답
4. 인사이트/근거 저장 (`insights`, `insight_evidences`) 후 응답 (결론 + 근거 + 신뢰도 + 후속 질문)
5. 요청 전체가 하나의 트랜잭션이 아니다. 그래프 투영(Neo4j)과 인사이트 임베딩은 응답 이후 비동기로 실행되며 실패해도 응답에 영향이 없다.

**2. 데이터 동기화 (Garmin)**
1. `POST /api/data-sources/garmin/sync` → Rate limit(30초) 확인, `sync_logs`에 RUNNING 기록 후 비동기 실행(`GarminSyncExecutor`, 스레드풀)
   - 사용자/Provider당 RUNNING은 1건만 (부분 유니크 인덱스 `uq_sync_logs_running`)
2. `GarminPythonClient`가 ProcessBuilder로 `garmin_sync.py` 실행 (`garminconnect`, 사용자별 토큰 캐시 `GARMIN_TOKEN_DIR`)
3. **7일 청크** 단위로 Raw JSON 수신 → UPSERT (PostgreSQL). 활동 `laps`는 `garmin_activity_laps`, 일일 extras는 `garmin_daily_raw`(엔드포인트별 실패는 스킵), 수면/건강은 구조화 컬럼
4. 날씨: 활동 위치/시각 기준 Open-Meteo 호출. 약 5일 이상 지난 활동은 archive API 사용
5. GraphProjector 실행 → Neo4j 노드/엣지 + `graph_node_mappings`
6. `sync_logs` 상태: `COMPLETED` / `PARTIAL`(일부 청크 실패) / `FAILED`. 기동 시 이전 프로세스의 RUNNING은 `FAILED`로 정리하며(`SyncLogStartupSweeper`), 청크 수 × 타임아웃 + 15분을 넘긴 RUNNING도 stale로 간주해 FAILED 처리
7. Spring Scheduler가 매일 12:00 KST 자동 증분 동기화 (`sync.schedule.cron`)
8. 스케줄 동기화가 끝나면(`GarminSyncFinishedEvent`) 해당 사용자의 이상 신호 감지(`AnomalyAlertScheduleService`)가 즉시 이어서 실행된다. 12:30 KST cron은 오늘 아직 감지하지 않았고 동기화 중이 아닌 사용자에 대한 fallback이다. 최근 3일 vs 28일 기준선 비교, 3일 중복 억제, 실행당 최대 2건, Insight(`ANOMALY_ALERT`)로 저장
9. 일요일 13:00 KST 주간 브리핑 자동 생성

**3. MCP OAuth 연결**
클라이언트 → `/mcp` 401 + PRM → Authorization Server(backend) 메타데이터 → `/api/oauth/authorize` → 프론트 `/oauth/consent` 승인 → `/api/oauth/token` → MCP가 토큰을 introspect 후 backend API 호출. 상세는 `docs/mcp-integration.md`, `docs/architecture.md`.

---

## 6. API 규약

### 응답 형식
일반 API는 `ApiResponse<T>` 래퍼로 응답합니다. (OAuth 프로토콜 엔드포인트 `/api/oauth/{register,token,revoke,introspect,authorize}`와 `/.well-known/*`는 RFC 형식의 raw JSON)
```json
{ "success": true, "message": null, "data": { ... } }
```

### 오류 규약
| 상태 | 의미 |
|------|------|
| 400 | 검증 실패. `{success:false, message:"Validation failed: field: msg; ...", data:{field: msg}}`. 읽을 수 없는 본문은 `data` 없는 메시지 |
| 401 | 미인증/토큰 만료, 잘못된 로그인 (프론트는 refresh 후 재시도) |
| 403 | 권한 없음 (ROLE_MCP가 allowlist 밖 API 호출 등) |
| 404 | 존재하지 않거나 **본인 소유가 아닌** 리소스 (`NotFoundException`) |
| 409 | 데이터 무결성 충돌 |
| 429 | 로그인 시도 제한, 동기화 빈도 제한(30초). 로그인 제한은 `Retry-After` 헤더 포함 |
| 500 | 내부 정보는 숨기고 서버 로그에만 기록 |

### 인증
- `Authorization: Bearer <jwt | OAuth access token | pios_ API 키>`
- 공개: `POST /api/auth/{login,register,refresh}`, `/actuator/health`, OAuth 공개 엔드포인트
- 웹 로그인은 access JWT(24시간) + httpOnly `refresh_token` 쿠키(7일). 프론트는 401 시 `/api/auth/refresh` 후 재시도, 실패 시 `/login`
- `ROLE_MCP` 주체(OAuth 토큰/API 키)는 `McpAccessPolicy` allowlist의 API만 호출 가능 (`mcp:read`, `mcp:write` 스코프)

### 주요 엔드포인트
| 영역 | 엔드포인트 |
|------|-----------|
| 인증 | `POST /api/auth/register`, `login`, `refresh`, `logout`, `GET /api/auth/me` |
| API 키 | `GET/POST /api/auth/api-keys`, `DELETE /api/auth/api-keys/{keyId}` |
| OAuth (공개) | `GET /.well-known/oauth-authorization-server`, `POST /api/oauth/register`, `GET /api/oauth/authorize`, `POST /api/oauth/token`, `POST /api/oauth/revoke` |
| OAuth (내부) | `POST /api/oauth/introspect` (내부 시크릿 필수, Caddy에서 외부 차단) |
| OAuth (웹 UI) | `GET /api/oauth/requests/{id}`, `POST /api/oauth/requests/{id}/decision`, `GET /api/oauth/grants`, `DELETE /api/oauth/grants/{grantId}` |
| 대시보드 | `GET /api/dashboard/summary` |
| 브리핑 | `GET /api/briefings/latest`, `POST /api/briefings/generate` |
| 데이터 소스 | `GET /api/data-sources`, `POST /api/data-sources/garmin/{connect,sync,mock}`, `GET /api/data-sources/garmin/sync-logs[/{logId}]`, `DELETE /api/data-sources/garmin` |
| 활동 | `GET /api/activities`, `GET /api/activities/{id}`, `GET /api/activities/{id}/laps`, `GET /api/activities/exercises`, `POST /api/activities/weight`, `PATCH /api/activities/{id}`, `PATCH /api/activities/{id}/tag`, `DELETE /api/activities/{id}` |
| 건강 | `GET /api/health/metrics`, `GET /api/health/sleep` (start, end 필수) |
| 가계 | `/api/finance/{cycles,transactions,accounts,recurring-bills,import/preview,import/confirm}` 등 |
| 그래프 | `GET /api/graph?days=&view=&raceCategory=` |
| 관리자 | `POST /api/admin/backfill` (그래프 투영 재실행) |
| 질의 | `POST /api/ask` |
| 인사이트 | `GET /api/insights?category=&feedbackStatus=&page=&size=` (size 기본 100, 최대 200), `GET /api/insights/saved`, `GET /api/insights/{id}`, `POST /api/insights/{id}/save`, `POST /api/insights/{id}/feedback`, `DELETE /api/insights/{id}`, `POST /api/insights/anomaly-alerts/detect` |
| 목표 | `GET/POST /api/goals`, `GET/PATCH/DELETE /api/goals/{id}` |
| 설정 | `GET/POST /api/settings/llm-providers`, `PATCH/DELETE /api/settings/llm-providers/{id}` |

전체 요청/응답은 `docs/api-specification.md` 참고.

---

## 7. 코드 스타일 가이드라인

### Backend (Java)
- **패키지**: `com.pios` 기준, 레이어 단위 분리 (`controller`, `service`, `repository`, `domain`, `dto`, `security`, `config`, `oauth`)
- **의존성 주입**: 생성자 주입 + `@RequiredArgsConstructor` (Lombok)
- **DTO/Entity**: Lombok `@Data`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor` 사용
- **Entity**: JPA 어노테이션 사용, `@CreationTimestamp` / `@UpdateTimestamp`로 시간 자동 관리
- **트랜잭션**: 쓰기 작업에 `@Transactional` 명시
- **예외 처리**: `GlobalExceptionHandler`에서 전역 처리. 존재하지 않는/남의 리소스는 `NotFoundException`(404), 입력 오류는 `IllegalArgumentException`/Bean Validation(400)
- **API 응답**: Controller에서는 `ApiResponse.ok(...)` 반환 (OAuth 프로토콜 엔드포인트 제외)
- **인증 주입**: `@AuthenticationPrincipal Long userId`
- **LLM 호출**: 직접 HTTP 호출하지 말고 `LlmClient`(+`LlmPurpose`)를 사용

### Frontend (TypeScript / React)
- **경로 alias**: `@/` → `src/`
- **컴포넌트**: 함수형 컴포넌트, 페이지는 default export + `App.tsx`에서 `lazy` 로딩. 페이지별 하위 컴포넌트는 `components/<page>/`
- **UI 컴포넌트**: `components/ui/` shadcn/ui 패턴 — `cn()` 유틸리티
- **상태 관리**: 서버 상태 TanStack Query, 클라이언트 상태 Zustand (`stores/`)
- **API 호출**: `lib/api.ts`의 `api` 객체 사용 (직접 `fetch` 금지)
- **타입**: `types/index.ts`에 중앙 정의
- **포맷터**: `lib/utils.ts`
- **스타일**: Tailwind 유틸리티, CSS 변수는 `globals.css`
- **언어**: UI 텍스트는 영문 메뉴명 + 한국어 사용자 문구 혼용

### 데이터베이스
- **PostgreSQL**: Flyway 마이그레이션 (`db/migration/V{version}__{name}.sql`), 현재 최신 V27
- **Neo4j**: Cypher로 노드/관계 생성, `GraphNodeMapping`으로 PostgreSQL row ↔ Neo4j node 매핑

---

## 8. 테스트

| 영역 | 구성 | 실행 |
|------|------|------|
| Backend | JUnit 5 + Mockito + Spring Security Test (`backend/src/test/java/com/pios/{controller,oauth,security,service}`), 약 180개 `@Test` | `cd backend && mvn -B test` |
| Frontend | Vitest + Testing Library (`src/**/*.test.{ts,tsx}`) | `cd frontend && npm test` (lint: `npm run lint`, 타입+빌드: `npm run build`) |
| MCP | pytest (`mcp/tests/`) | `cd mcp && python -m venv .venv && . .venv/bin/activate && pip install -r requirements-dev.txt && pytest tests` |

JDK 21이 로컬에 없으면 Docker로 실행합니다.
```bash
docker run --rm -v "$PWD/backend":/app -v "$HOME/.m2":/root/.m2 -w /app maven:3.9.9-eclipse-temurin-21 mvn -B test
```
CI(`.github/workflows/ci.yml`)가 push/PR마다 위 세 가지와 스크립트 컴파일, 이미지 빌드, compose/Caddyfile 검증을 수행합니다. 통합 테스트용 Testcontainers는 사용하지 않습니다.

---

## 9. 보안 고려사항

- **비밀번호**: BCrypt. **로그인 제한**: 이메일+IP 5회, IP당 30회 실패(15분 윈도우) 시 429 + `Retry-After` (`LoginAttemptService`)
- **인증**: JWT(HS256) access + httpOnly refresh 쿠키(DB 저장, 회전). 회원가입 기본 비활성
- **OAuth 2.1 Authorization Server**: public client + PKCE(S256) 필수, DCR(RFC 7591)/Client ID Metadata Document, authorization code·refresh token은 SHA-256 해시로 저장, refresh 회전 및 재사용 탐지, 동의(consent) 화면 필수
- **MCP principal(`ROLE_MCP`)**: OAuth access token / `pios_` API 키 주체는 `McpAccessPolicy` allowlist + 스코프(`mcp:read`/`mcp:write`) 범위의 API만 호출 가능, 그 외 403
- **내부 시크릿**: `PIOS_MCP_INTERNAL_SECRET`(32자 이상) — MCP→backend 호출과 `/api/oauth/introspect`에 필요. introspect는 Caddy에서도 외부 차단
- **CORS**: `PIOS_CORS_ALLOWED_ORIGINS`(필수, 쉼표 구분)에 `PIOS_PUBLIC_BASE_URL`/프론트 origin 자동 추가. 공개 OAuth 엔드포인트만 모든 origin 허용(쿠키 없음)
- **DB 민감정보**: `llm_providers.api_key_encrypted`, Garmin `auth_payload.password`는 AES-GCM(`SecretCryptoService`, `ENC:` prefix). 키 재료 `PIOS_ENCRYPTION_KEY`(`JWT_SECRET`과 별도). 교체 시 `PIOS_ENCRYPTION_KEY_LEGACY` (`docs/operations.md`)
- **컨테이너**: backend/frontend/mcp는 비root 실행. Caddy가 HSTS/CSP 등 보안 헤더 부여
- `JWT_SECRET` 교체 시 모든 JWT/OAuth access token이 무효화됩니다.

---

## 10. 환경 변수

`docker-compose.yml`에서 `:?`로 선언된 항목은 **필수**(누락 시 compose 실패)입니다.

| 변수 | 필수 | 기본값 | 설명 |
|------|------|--------|------|
| `JWT_SECRET` | 필수 | — | JWT 서명 키 (`openssl rand -base64 48`) |
| `PIOS_ENCRYPTION_KEY` | 필수 | — | LLM/Garmin 시크릿 AES 키 재료 (JWT_SECRET과 다른 값) |
| `PIOS_MCP_INTERNAL_SECRET` | 필수 | — | backend↔MCP 내부 공유 시크릿 (32자 이상) |
| `POSTGRES_PASSWORD` | 필수 | — | PostgreSQL 비밀번호 |
| `PIOS_PUBLIC_BASE_URL` | 필수 | — | 외부 기준 URL (OAuth issuer, 끝에 `/` 없이). 예 `https://pios.example.com` |
| `PIOS_CORS_ALLOWED_ORIGINS` | 필수 | — | CORS 허용 Origin (쉼표 구분) |
| `PIOS_DOMAIN` | 선택 | `localhost` | Caddy 도메인 |
| `PIOS_MCP_RESOURCE_URL` | 파생 | `${PIOS_PUBLIC_BASE_URL}/mcp` | MCP resource URL (compose가 자동 설정, 비 Docker 실행 시에만 직접 지정) |
| `PIOS_FRONTEND_BASE_URL` | 파생 | `PIOS_PUBLIC_BASE_URL` | consent 화면 등 프론트 URL (compose가 자동 설정) |
| `PIOS_ENCRYPTION_KEY_LEGACY` | 선택 | 빈 값 | 키 교체 시 이전 키 |
| `PIOS_REGISTRATION_ENABLED` | 선택 | `false` | 회원가입 허용 |
| `PIOS_MOCK_DATA_ENABLED` | 선택 | `false` (`local` 프로필은 `true`) | Mock 데이터 API 허용 |
| `OPENAI_API_KEY` | 선택 | 빈 값 | 전역 LLM 키 (사용자 Provider 키가 우선) |
| `NEO4J_URI` / `NEO4J_PASSWORD` | **필수** (compose `:?`) | - | Neo4j 접속 정보 (compose가 `SPRING_NEO4J_*`로 전달). `NEO4J_USERNAME` 기본 `neo4j` |
| `OPENAI_BASE_URL` / `OPENAI_MODEL` | 선택 | `https://api.openai.com/v1` / `gpt-4o-mini` | OpenAI 호환 엔드포인트·모델 |
| `SPRING_PROFILES_ACTIVE` | 개발용 | — | 로컬 개발은 `local` (개발용 기본 시크릿 사용, 운영 금지) |

`PIOS_MCP_API_KEY`는 제거되었습니다. MCP 클라이언트는 OAuth로 연결하거나 `Authorization: Bearer pios_...` API 키를 사용합니다. 그 외 튜닝 값(`GARMIN_PYTHON`, `GARMIN_TOKEN_DIR`, `NEO4J_INIT_SCHEMA` 등)은 `backend/src/main/resources/application.yml` 참고.

---

## 11. 개발 워크플로우 팁

### 새로운 API 추가 시
1. `dto/`에 DTO, `controller/`에 엔드포인트, `service/`에 로직, 필요 시 `repository/`·`domain/`
2. `lib/api.ts`에 프론트 API 메서드, `types/index.ts`에 타입
3. MCP 노출이 필요하면 `McpAccessPolicy` allowlist와 `mcp/pios_mcp/tools.py`에 추가

### 새로운 페이지 추가 시
1. `pages/`에 컴포넌트 (default export), 하위 컴포넌트는 `components/<page>/`
2. `App.tsx`에 `lazy` 라우트 추가, 필요시 `components/layout/Sidebar.tsx` 메뉴 추가

### DB 스키마 변경 시
1. `backend/src/main/resources/db/migration/`에 새 Flyway 파일 추가 (다음 버전: **V28**, 예 `V28__add_new_table.sql`)
2. `domain/` Entity 수정, `ddl-auto: validate` 유지

### 문서 최신화 규칙 (필수)
- **로직, 아키텍처, API 규약, DB 스키마, 화면 구성 등 코드의 핵심 변경이 발생하면 반드시 `docs/` 하위 문서를 함께 최신화**해야 합니다.
- 변경 사항과 관련된 문서를 누락 없이 수정한 뒤에만 작업을 완료로 간주합니다.
- 우선 점검 문서: `docs/architecture.md`, `docs/api-specification.md`, `docs/database-schema.md`, `docs/mvp-features.md`, `docs/ui-screens.md`, `docs/mcp-integration.md`, `docs/operations.md`, `CHANGELOG.md`

---

## 12. 참고 문서

- `README.md` — 프로젝트 소개 및 Quick Start
- `docs/architecture.md`, `docs/api-specification.md`, `docs/database-schema.md`
- `docs/project-structure.md`, `docs/tech-stack.md`, `docs/mvp-features.md`, `docs/ui-screens.md`
- `docs/getting-started.md` — 개발 환경 설정
- `docs/mcp-integration.md` — MCP 연동 가이드 (OAuth, 도구 목록, 클라이언트 설정)
- `docs/operations.md` — 운영 가이드 (포트, 백업/복구, 시크릿 교체, 업그레이드)
- `docs/archive/` — 초기 기획 초안 (더 이상 갱신하지 않음)
