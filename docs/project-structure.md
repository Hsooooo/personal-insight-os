# 📁 프로젝트 구조

> 파일 수/코드량은 자주 바뀌므로 기재하지 않습니다. 아래는 디렉터리 단위의 역할입니다.

## 전체 트리

```
.
├── docker-compose.yml              # postgres, backend, mcp, frontend, caddy
├── Caddyfile                       # 유일한 외부 진입점: 라우팅/보안 헤더/introspect 차단
├── .env.example                    # 필수/선택 환경변수 템플릿
├── .github/workflows/ci.yml        # CI (backend/frontend/mcp 테스트, 이미지 빌드, compose 검증)
├── README.md, AGENTS.md, CHANGELOG.md
│
├── scripts/ops/                    # backup-postgres.sh, restore-postgres.sh
│
├── backend/                        # Spring Boot 3.5.x + Java 21
│   ├── Dockerfile                  # Maven 빌드 → JRE 21 alpine + python3/garminconnect, 비root(10001)
│   ├── pom.xml
│   ├── scripts/garmin_sync.py      # Garmin 수집 (GarminPythonClient가 ProcessBuilder로 실행)
│   └── src/
│       ├── main/java/com/pios/
│       │   ├── PiosApplication.java
│       │   ├── common/             # NotFoundException, AppTimeZones
│       │   ├── config/             # AppConfig, AsyncConfig, GlobalExceptionHandler, SecretMigrationRunner,
│       │   │                       # GraphSchemaInitializer, TimeZoneConsistencyCheck
│       │   ├── controller/         # Auth, Dashboard, DataSource, Activity, Health, Graph, Ask, Insight,
│       │   │                       # Goal, LlmProvider, Briefing, Finance, Admin
│       │   ├── domain/ (+enums)    # JPA Entity (Activity, Garmin*, Insight*, Goal, Finance*, ApiKey, RefreshToken, SyncLog ...)
│       │   ├── dto/ (+finance)     # Request/Response DTO
│       │   ├── repository/         # Spring Data JPA
│       │   ├── spec/               # JPA Specification (활동 필터)
│       │   ├── oauth/              # OAuth 2.1 Authorization Server
│       │   │   ├── controller/     # OAuthController(공개 프로토콜), OAuthConsentController(웹 UI), OAuthMetadataController
│       │   │   ├── domain/ dto/ repository/
│       │   │   └── service/        # Authorization/Client/Token/Cleanup, ClientMetadataDocumentFetcher
│       │   ├── security/           # SecurityConfig, JwtUtil, JwtAuthenticationFilter, McpAccessPolicy,
│       │   │                       # InternalSecretVerifier, LoginAttemptService, SecretCryptoService, ClientIpResolver ...
│       │   └── service/            # 도메인 서비스 + 하위 패키지
│       │       ├── alert/          # 이상 신호 감지/스케줄
│       │       ├── ask/            # RAG v2 (의도/기간/통계/신뢰도/프롬프트)
│       │       ├── briefing/       # 주간 브리핑
│       │       ├── finance/        # 엑셀 파서
│       │       ├── garmin/         # ActivityPersister, DailyPersister, ActivityWeatherEnricher
│       │       ├── goal/           # 진행률/블로커 분석
│       │       ├── llm/            # LlmClient, LlmCredentialsResolver, LlmProperties, LlmPurpose
│       │       └── pattern/        # ThinPatternDetector
│       ├── main/resources/
│       │   ├── application.yml, application-local.yml   # local 프로필: 개발용 기본 시크릿
│       │   └── db/migration/       # Flyway V1 ~ V26
│       └── test/java/com/pios/     # JUnit 5 (controller, oauth, security, service)
│
├── mcp/                            # MCP 서버 (Python 3.12, mcp SDK 2.x)
│   ├── server.py                   # 엔트리포인트 (--transport stdio | streamable-http)
│   ├── pios_mcp/                   # config / backend / auth / tools / models / app
│   ├── tests/                      # pytest (test_http_auth, test_token_verifier, test_tools)
│   ├── requirements.txt, requirements-dev.txt, pytest.ini
│   ├── Dockerfile                  # 비root(10001), :8001
│   └── mcp.json                    # 클라이언트 설정 예시
│
├── frontend/                       # React 19 + TypeScript + Vite 8
│   ├── Dockerfile                  # Node 빌드 → nginx-unprivileged (:8080)
│   ├── nginx.conf                  # 정적 SPA + 캐시 헤더만 (/api 프록시 없음)
│   ├── package.json, vite.config.ts (alias, dev proxy, vitest), eslint.config.js
│   └── src/
│       ├── main.tsx, App.tsx       # 인증 라우팅, lazy 페이지, /oauth/consent
│       ├── pages/                  # Login, Dashboard, DataSources, Activities, Health, Finance, Graph,
│       │                           # Ask, Insights, Goals, McpSettings, Settings, OAuthConsent
│       ├── components/
│       │   ├── ui/                 # shadcn/ui 컴포넌트
│       │   ├── layout/             # Sidebar, Header, Layout
│       │   ├── common/             # ErrorBoundary, QueryError, EmptyState, ConfirmDialog, ChartDataTable, PageSkeleton
│       │   └── activities/ ask/ auth/ dashboard/ data-sources/ finance/ graph/ mcp/ oauth/   # 페이지별 하위 컴포넌트
│       ├── lib/                    # api.ts, apiClient.ts(401 refresh), oauth.ts, hevyParser.ts, utils.ts ...
│       ├── stores/authStore.ts     # Zustand (access token은 메모리)
│       ├── hooks/, test/, types/index.ts, styles/globals.css
│
└── docs/                           # 설계/운영 문서
    ├── README.md, architecture.md, tech-stack.md, api-specification.md, database-schema.md
    ├── ui-screens.md, project-structure.md, getting-started.md, mvp-features.md
    ├── mcp-integration.md, operations.md
    ├── graph-libraries-research.md, roadmap/, assets/
    └── archive/                    # 초기 기획 초안 (갱신하지 않음)
```

## 테스트 위치

| 영역 | 위치 | 실행 |
|------|------|------|
| Backend | `backend/src/test/java/com/pios/` | `cd backend && mvn -B test` |
| Frontend | `frontend/src/**/*.test.{ts,tsx}` | `cd frontend && npm test` |
| MCP | `mcp/tests/` | `cd mcp && pytest tests` |
