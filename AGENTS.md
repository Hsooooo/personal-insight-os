# AGENTS.md — Personal Insight OS

> 이 파일은 AI 코딩 에이전트가 프로젝트를 이해하고 작업할 때 참고해야 하는 핵심 정보를 담고 있습니다. 프로젝트의 모든 문서와 코드는 주로 한국어를 기준으로 작성되었습니다.

---

## 1. 프로젝트 개요

**Personal Insight OS (PIOS)**는 개인의 건강, 운등, 기록 데이터를 도메인별 노드와 관계 그래프로 구조화하고, LLM 기반 RAG를 통해 근거 있는 개인화 인사이트를 제공하는 웹서비스입니다.

- 사용자의 Garmin 데이터를 수집하여 PostgreSQL에 원천/정형 데이터를 저장합니다.
- 수집된 데이터를 도메인 모델(Activity, Sleep, HealthMetric 등)로 변환하여 Neo4j 그래프에 노드/엣지를 생성합니다.
- 사용자가 자연어로 질문하면 최근 7~30일 데이터를 기반으로 LLM(OpenAI GPT-4o-mini)이 근거 기반 답변을 생성합니다.
- 생성된 인사이트는 저장하고 피드백(CORRECT, UNCLEAR, WRONG, IMPORTANT)할 수 있습니다.

### MVP 기능
- JWT 기반 사용자 로그인/회원가입
- Garmin 데이터 연동 및 동기화 (Mock 데이터 생성)
- 대시보드 (요약 카드, 7일 트렌드 차트, 최근 인사이트)
- 활동/건강/수면 데이터 조회
- 개인 지식 그래프 시각화 (Cytoscape) — 날짜/뷰/레이스 필터
- 자연어 질의 (Ask My Data) + RAG 응답
- 인사이트 저장 및 피드백
- 목표 설정 및 관리
- LLM Provider 설정

---

## 2. 기술 스택

### Backend
- **Spring Boot 3.3** + **Java 21**
- **Spring Security** + JWT (jjwt 0.12.5)
- **Spring Data JPA** (Hibernate)
- **Flyway** — DB 마이그레이션 (V1__init.sql, V2__add_sync_system.sql)
- **Neo4j Java Driver** 5.20.0 — 그래프 데이터 접근
- **OpenAI Java SDK** 0.13.0 — LLM 호출
- **Lombok** — 보일러플레이트 감소
- **Maven** — 빌드 도구

### Frontend
- **React 19** + **TypeScript 5.5**
- **Vite 5.3** — 빌드 도구 및 개발 서버
- **Tailwind CSS 3.4** — 유틸리티 기반 스타일링
- **shadcn/ui** (custom) — Radix UI 기반 컴포넌트 (button, card, input, badge, avatar, skeleton, separator, table, label, tabs, dialog, dropdown-menu, select, toast, tooltip 등)
- **Recharts** — 차트 시각화
- **Cytoscape.js + cytoscape-fcose** — 인터랙티브 그래프 시각화
- **TanStack Query (@tanstack/react-query)** — 서버 상태 관리 및 캐싱
- **Zustand** — 클라이언트 상태 관리 (Auth Store)
- **React Router v6** — 라우팅
- **lucide-react** — 아이콘

### Infrastructure
- **PostgreSQL 16** + **pgvector** — 원천 데이터 및 벡터 저장
- **Neo4j** (외부/클로드 인스턴스) — 그래프 데이터베이스
- **Docker Compose** — 3서비스 오케스트레이션 (postgres, backend, frontend + caddy)
- **exercises 테이블** — 사용자 정의 웨이트 트레이닝 종목 관리
- **Python 3 + garminconnect** — Garmin Connect 데이터 수집 (Docker에 포함)
- **Caddy** — 리버스 프록시 + 자동 HTTPS

---

## 3. 프로젝트 구조

```
personal-insight-os/
├── docker-compose.yml          # postgres, backend, frontend, caddy
├── .env.example                # OPENAI_API_KEY, JWT_SECRET, NEO4J_*
├── docs/                       # 기획/설계 문서 (architecture, api-spec, etc.)
├── CHANGELOG.md                # 변경 이력
├── backend/                    # Spring Boot 3.3 + Java 21
│   ├── pom.xml
│   ├── Dockerfile              # Maven 멀티스테이지 빌드
│   └── src/main/java/com/pios/
│       ├── PiosApplication.java
│       ├── config/             # AppConfig (Neo4j Driver, RestTemplate), GlobalExceptionHandler
│       ├── controller/         # 10개 REST Controller (/api/*)
│       ├── domain/             # 12개 JPA Entity
│       ├── dto/                # 20개 Request/Response DTO
│       ├── repository/         # 11개 Spring Data JPA Repository
│       ├── security/           # JwtUtil, JwtAuthenticationFilter, SecurityConfig
│       └── service/            # 12개 Business Service
│   └── src/main/resources/
│       ├── application.yml     # DB, Neo4j, JWT, OpenAI 설정
│       └── db/migration/V1__init.sql
├── frontend/                   # React 19 + TypeScript + Vite
│   ├── package.json
│   ├── vite.config.ts          # @/ alias, /api → localhost:8080 proxy
│   ├── tsconfig.json           # strict 완화 (noImplicitAny: false)
│   ├── tailwind.config.js      # Pretendard 폰트, shadcn 색상 변수
│   ├── nginx.conf              # /api → backend:8080 프록시
│   ├── Dockerfile              # Node 빌드 + nginx
│   └── src/
│       ├── main.tsx            # React + QueryClient + BrowserRouter
│       ├── App.tsx             # 인증 기반 라우팅
│       ├── components/
│       │   ├── ui/             # 9개+ shadcn/ui 컴포넌트
│       │   └── layout/         # Layout, Header, Sidebar
│       ├── pages/              # 10개 화면 컴포넌트
│       ├── lib/
│       │   ├── api.ts          # fetch 래퍼 + 30+ API 메서드
│       │   └── utils.ts        # cn() + 날짜/시간/거리 포맷터
│       ├── stores/
│       │   └── authStore.ts    # Zustand + persist (localStorage)
│       ├── types/
│       │   └── index.ts        # TypeScript 인터페이스 정의
│       └── styles/
│           └── globals.css     # Tailwind + CSS 변수
└── docs/                       # 기획/설계 문서 (architecture, api-spec, etc.)
```

---

## 4. 빌드 및 실행 명령어

### 전체 시스템 (Docker Compose)
```bash
# 환경 변수 설정 (선택)
cp .env.example .env
# .env에 OPENAI_API_KEY 입력

docker-compose up --build
```
- Frontend: http://localhost:3000
- Backend API: http://localhost:8080
- PostgreSQL: localhost:5432 (pios / pios123)

### Backend 단독 실행
```bash
cd backend
./mvnw spring-boot:run
# 또는
mvn spring-boot:run
```

### Frontend 단도 실행
```bash
cd frontend
npm install
npm run dev     # http://localhost:5173
npm run build   # dist/ 디렉토리 생성
npm run preview
```

### Maven 빌드
```bash
cd backend
mvn clean package -DskipTests
```

---

## 5. 아키텍처 및 데이터 흐름

### 런타임 아키텍처
```
[React 19 + Vite] ←──HTTP──→ [Spring Boot 3.3] ←──JDBC──→ [PostgreSQL + pgvector]
                                    │
                                    └──Bolt──→ [Neo4j 5]
                                    │
                                    └──HTTPS──→ [OpenAI API] (선택)
```

### 핵심 데이터 흐름

**1. RAG 파이프라인 (Ask My Data)**
1. 사용자가 자연어 질문 (`POST /api/ask`)
2. 질문 저장 (`questions` 테이블)
3. 최근 7일 건강 지표/수면/활동 데이터 조회 (PostgreSQL)
4. 관련 그래프 관계 조회 (Neo4j)
5. OpenAI API 호출 (프롬프트 + 근거 데이터) — API Key 미설정 시 Fallback 응답
6. 인사이트 저장 (`insights` 테이블)
7. 근거 저장 (`insight_evidences` 테이블)
8. 결론 + 근거 + 신뢰도 응답

**2. 데이터 동기화 (Garmin)**
1. `POST /api/data-sources/garmin/sync` (body: syncType, dateFrom, dateTo)
2. Rate limit 체크 (30초) → `sync_logs` RUNNING 상태 기록
3. `GarminPythonClient`가 ProcessBuilder로 Python 스크립트 실행
4. Python `garminconnect` 라이브러리가 Garmin Connect API 호출
5. 30일 청크 단위로 Raw JSON 수신 → UPSERT 저장 (PostgreSQL)
6. GraphProjector 실행 → Neo4j 노드/엣지 생성
7. `graph_node_mappings` 테이블에 매핑 정보 저장
8. `sync_logs` COMPLETED/FAILED 상태 업데이트
9. Spring Scheduler가 매일 03:00 자동 증분 동기화 실행

---

## 6. API 규약

### 응답 형식
모든 API는 `ApiResponse<T>` 래퍼로 응답합니다.
```json
{
  "success": true,
  "message": null,
  "data": { ... }
}
```

### 인증
- `Authorization: Bearer <jwt_token>` 헤더 사용
- 로그인/회원가입 제외 모든 엔드포인트에 필요
- JWT 만료 시간: 24시간 (86400000ms)
- 프론트엔드에서 401 응답 시 자동 로그아웃 + `/login` 리다이렉트

### 주요 엔드포인트
| 영역 | 엔드포인트 |
|------|-----------|
| 인증 | `POST /api/auth/register`, `POST /api/auth/login`, `GET /api/auth/me` |
| 대시보드 | `GET /api/dashboard/summary` |
| 데이터 소스 | `GET /api/data-sources`, `POST /api/data-sources/garmin/connect`, `POST /api/data-sources/garmin/sync`, `GET /api/data-sources/garmin/sync-logs`, `POST /api/data-sources/garmin/mock`, `DELETE /api/data-sources/garmin` |
| 활동 | `GET /api/activities` (이름/유형/거리/시간/심박/칼로리) |
| 활동 | `GET /api/activities`, `GET /api/activities/{id}` |
| 활동 | `GET /api/activities/exercises` | 웨이트 트레이닝 종목 목록 |
| 건강 | `GET /api/health/metrics`, `GET /api/health/sleep` |
| 그래프 | `GET /api/graph?days=&view=&raceCategory=` |
| 관리자 | `POST /api/admin/backfill` | 그래프 투영 재실행 |
| 질의 | `POST /api/ask` |
| 인사이트 | `GET /api/insights`, `POST /api/insights/{id}/save`, `POST /api/insights/{id}/feedback`, `DELETE /api/insights/{id}` |
| 목표 | `GET /api/goals`, `POST /api/goals`, `PATCH /api/goals/{id}`, `DELETE /api/goals/{id}` |
| 설정 | `GET /api/settings/llm-providers`, `POST /api/settings/llm-providers`, `PATCH /api/settings/llm-providers/{id}`, `DELETE /api/settings/llm-providers/{id}` |

---

## 7. 코드 스타일 가이드라인

### Backend (Java)
- **패키지**: `com.pios` 기준, 레이어 단위 분리 (`controller`, `service`, `repository`, `domain`, `dto`, `security`, `config`)
- **의존성 주입**: 생성자 주입 + `@RequiredArgsConstructor` (Lombok)
- **DTO/Entity**: Lombok `@Data`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor` 사용
- **Entity**: JPA 어노테이션 사용, `@CreationTimestamp` / `@UpdateTimestamp`로 시간 자동 관리
- **트랜잭션**: 쓰기 작업에 `@Transactional` 명시
- **예외 처리**: `GlobalExceptionHandler`에서 전역 처리, 런타임 예외는 `IllegalArgumentException` 등 사용
- **API 응답**: Controller에서는 항상 `ApiResponse.ok(...)` 또는 `ApiResponse.error(...)` 반환
- **인증 주입**: `@AuthenticationPrincipal Long userId`로 현재 사용자 ID 주입

### Frontend (TypeScript / React)
- **경로 alias**: `@/` → `src/` (vite.config.ts 및 tsconfig.json에 설정)
- **컴포넌트**: 함수형 컴포넌트, default export로 페이지 컴포넌트 export
- **UI 컴포넌트**: `components/ui/` 아래 shadcn/ui 패턴 — `cn()` 유틸리티로 클래스 병합
- **상태 관리**:
  - 서버 상태: TanStack Query (`useQuery`, `useMutation`)
  - 클라이언트 상태: Zustand (`stores/`)
- **API 호출**: `lib/api.ts`의 `api` 객체 사용 (직접 `fetch` 호출 금지)
- **타입**: `types/index.ts`에 모든 인터페이스 중앙 정의
- **포맷터**: `lib/utils.ts`의 `formatDate`, `formatDuration`, `formatDistance` 사용
- **스타일**: Tailwind CSS 유틸리티 클래스 직접 사용, CSS 변수는 `globals.css`에 정의
- **언어**: UI 텍스트는 영문 위주 (Dashboard, Activities 등), 일부 한국어 사용자 문구 가능

### 데이터베이스
- **PostgreSQL**: Flyway 마이그레이션 사용 (`db/migration/V{version}__{name}.sql`)
- **Neo4j**: Cypher 쿼리로 직접 노드/관계 생성, `GraphNodeMapping`으로 PostgreSQL row ↔ Neo4j node 매핑 관리

---

## 8. 테스트

현재 프로젝트에 **테스트 코드가 없습니다**. 테스트를 추가해야 할 경우 다음을 참고하세요.

### Backend
- **단위 테스트**: JUnit 5 + Mockito, `src/test/java/com/pios/` 아래 작성
- **통합 테스트**: `@SpringBootTest`, Testcontainers로 PostgreSQL/Neo4j 격리
- **보안 테스트**: `spring-security-test` 의존성 이미 포함됨

### Frontend
- **단위 테스트**: Vitest (Vite 기반) 권장
- **컴포넌트 테스트**: React Testing Library
- **E2E 테스트**: Playwright 권장

---
## 9. 보안 고려사항

### 현재 구현
- **비밀번호**: BCrypt 해싱 (`PasswordEncoder`)
- **인증**: JWT (HS256), 만료 24시간
- **CORS**: `localhost:5173`, `localhost:3000` 허용 (개발 환경)
- **CSRF**: Stateless JWT로 CSRF 비활성화
- **DB 민감정보**: `llm_providers.api_key_encrypted` 필드 존재하나 현재 암호화 로직 미구현

### 주의사항
- `JWT_SECRET` 환경변수는 프로덕션에서 반드시 변경해야 합니다.
- OpenAI API Key는 `.env` 파일에 저장되며 Docker Compose를 통해 백엔드에 주입됩니다.
- 현재 MVP 단계로 입력값 검증은 기본적인 `@Valid` 수준입니다.

---

## 10. 환경 변수

| 변수 | 기본값 | 설명 |
|------|--------|------|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/pios` | PostgreSQL 접속 URL |
| `SPRING_DATASOURCE_USERNAME` | `pios` | PostgreSQL 사용자 |
| `SPRING_DATASOURCE_PASSWORD` | `pios123` | PostgreSQL 비밀번호 |
| `NEO4J_URI` | (없음) | Neo4j 접속 URI (예: `neo4j+s://xxxxx.databases.neo4j.io`) |
| `NEO4J_USERNAME` | (없음) | Neo4j 사용자명 |
| `NEO4J_PASSWORD` | (없음) | Neo4j 비밀번호 |
| `JWT_SECRET` | `pios-jwt-secret-key-2026-change-in-production` | JWT 서명 키 |
| `OPENAI_API_KEY` | (빈 문자열) | OpenAI API Key (선택) |

---

## 11. 개발 워크플로우 팁

### 새로운 API 추가 시
1. `dto/`에 Request/Response DTO 추가
2. `controller/`에 REST 엔드포인트 추가 (`@RestController`, `@RequestMapping("/api/...")`)
3. `service/`에 비즈니스 로직 추가
4. `repository/`에 필요 시 JPA Repository 인터페이스 추가
5. `domain/`에 필요 시 Entity 추가
6. `lib/api.ts`에 프론트엔드 API 메서드 추가
7. `types/index.ts`에 TypeScript 타입 추가

### 새로운 페이지 추가 시
1. `pages/`에 새로운 TSX 컴포넌트 작성 (default export)
2. `App.tsx`에 라우트 추가
3. `components/layout/Sidebar.tsx`에 네비게이션 메뉴 추가 (필요시)

### DB 스키마 변경 시
1. `backend/src/main/resources/db/migration/`에 새 Flyway 마이그레이션 파일 추가
   - 파일명 규칙: `V{version}__{description}.sql` (예: `V2__add_new_table.sql`)
2. `domain/`에 Entity 수정/추가
3. `application.yml`의 `ddl-auto: validate` 유지 (스키마 검증만 수행)

### 문서 최신화 규칙 (필수)
- **로직, 아키텍처, API 규약, DB 스키마, 화면 구성 등 코드의 핵심 변경이 발생하면 반드시 `docs/` 하위 문서를 함께 최신화**해야 합니다.
- 변경 사항과 관련된 문서를 누락 없이 수정한 뒤에만 작업을 완료로 간주합니다.
- 특히 아래 문서는 로직 변경 시 우선적으로 점검합니다:
  - `docs/architecture.md` — 시스템 아키텍처 및 데이터 흐름
  - `docs/api-specification.md` — API 명세
  - `docs/database-schema.md` — DB 스키마
  - `docs/mvp-features.md` — MVP 기능 목록
  - `docs/ui-screens.md` — 화멳별 스토리보드

---

## 12. 참고 문서

- `README.md` — 프로젝트 소개 및 Quick Start
- `docs/architecture.md` — 시스템 아키텍처 상세
- `docs/api-specification.md` — API 명세
- `docs/database-schema.md` — DB 스키마
- `docs/project-structure.md` — 파일 트리 상세
- `docs/tech-stack.md` — 기술 스택 선택 이유
- `docs/mvp-features.md` — MVP 기능 목록
- `docs/ui-screens.md` — 화면별 스토리보드
- `docs/getting-started.md` — 개발 환경 설정
- `personal_insight_os_mvp_deliverables.md` — 1차 기획 산출물 (도메인 모델, RAG 파이프라인 등)
