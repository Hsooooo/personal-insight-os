# 📁 프로젝트 구조

## 전체 파일 트리

```
personal-insight-os/
├── docker-compose.yml              # 4서비스 오케스트레이션
├── .env.example                    # 환경변수 템플릿
├── README.md                       # 프로젝트 가이드
├── .gitignore
│
├── backend/                        # Spring Boot 3.3 + Java 21
│   ├── Dockerfile                  # Maven 멀티스테이지 빌드
│   ├── pom.xml                     # 의존성: Web, JPA, Security, Neo4j, OpenAI, Flyway
│   └── src/
│       └── main/
│           ├── java/com/pios/
│           │   ├── PiosApplication.java
│           │   ├── config/
│           │   │   ├── AppConfig.java              # Neo4j Driver, RestTemplate Bean
│           │   │   └── GlobalExceptionHandler.java # 400/500 전역 예외 처리
│           │   ├── controller/                     # 10개 REST Controller
│           │   │   ├── AuthController.java
│           │   │   ├── DashboardController.java
│           │   │   ├── DataSourceController.java
│           │   │   ├── ActivityController.java
│           │   │   ├── HealthController.java
│           │   │   ├── GraphController.java
│           │   │   ├── AskController.java
│           │   │   ├── InsightController.java
│           │   │   ├── GoalController.java
│           │   │   └── LlmProviderController.java
│           │   ├── domain/                         # 12개 JPA Entity
│           │   │   ├── User.java
│           │   │   ├── ProviderConnection.java
│           │   │   ├── GarminActivity.java
│           │   │   ├── GarminActivityLap.java
│           │   │   ├── GarminDailyHealthMetric.java
│           │   │   ├── GarminSleepSession.java
│           │   │   ├── Goal.java
│           │   │   ├── LlmProvider.java
│           │   │   ├── Question.java
│           │   │   ├── Insight.java
│           │   │   ├── InsightEvidence.java
│           │   │   └── GraphNodeMapping.java
│           │   ├── dto/                            # 20개 Request/Response DTO
│           │   │   ├── ApiResponse.java
│           │   │   ├── AuthRequest.java
│           │   │   ├── AuthResponse.java
│           │   │   ├── UserDto.java
│           │   │   ├── ProviderConnectionDto.java
│           │   │   ├── ActivityDto.java
│           │   │   ├── HealthMetricDto.java
│           │   │   ├── SleepDto.java
│           │   │   ├── GoalDto.java
│           │   │   ├── InsightDto.java
│           │   │   ├── EvidenceDto.java
│           │   │   ├── AskRequest.java
│           │   │   ├── AskResponse.java
│           │   │   ├── LlmProviderDto.java
│           │   │   ├── LlmProviderRequest.java
│           │   │   ├── DashboardSummaryDto.java
│           │   │   ├── GraphNodeDto.java
│           │   │   ├── GraphRelationshipDto.java
│           │   │   ├── GraphDataDto.java
│           │   │   └── FeedbackRequest.java
│           │   ├── repository/                     # 11개 Spring Data JPA Repository
│           │   │   ├── UserRepository.java
│           │   │   ├── ProviderConnectionRepository.java
│           │   │   ├── GarminActivityRepository.java
│           │   │   ├── GarminDailyHealthMetricRepository.java
│           │   │   ├── GarminSleepSessionRepository.java
│           │   │   ├── GoalRepository.java
│           │   │   ├── LlmProviderRepository.java
│           │   │   ├── QuestionRepository.java
│           │   │   ├── InsightRepository.java
│           │   │   ├── InsightEvidenceRepository.java
│           │   │   └── GraphNodeMappingRepository.java
│           │   ├── security/                       # JWT 인증 계층
│           │   │   ├── JwtUtil.java                # 토큰 생성/검증
│           │   │   ├── JwtAuthenticationFilter.java # 요청 필터
│           │   │   └── SecurityConfig.java         # CORS + Stateless
│           │   └── service/                        # 12개 Business Service
│           │       ├── AuthService.java
│           │       ├── DashboardService.java
│           │       ├── DataSourceService.java
│           │       ├── ActivityService.java
│           │       ├── HealthService.java
│           │       ├── GraphService.java
│           │       ├── GraphProjectorService.java
│           │       ├── MockDataService.java
│           │       ├── AskService.java
│           │       ├── InsightService.java
│           │       ├── GoalService.java
│           │       └── LlmProviderService.java
│           └── resources/
│               ├── application.yml                 # DB/Neo4j/JWT/OpenAI 설정
│               └── db/migration/
│                   └── V1__init.sql                # 12개 테이블 + 인덱스 DDL
│
├── frontend/                       # React 19 + TypeScript + Vite
│   ├── Dockerfile                  # Node 빌드 + nginx
│   ├── nginx.conf                  # /api 프록시 설정
│   ├── package.json                # 20+ 의존성
│   ├── vite.config.ts              # @/ alias + dev proxy
│   ├── tsconfig.json               # strict + noImplicitAny 완화
│   ├── tsconfig.node.json
│   ├── tailwind.config.js          # Pretendard + shadcn 색상
│   ├── postcss.config.js
│   ├── index.html                  # Pretendard CDN
│   └── src/
│       ├── main.tsx                # React + QueryClient + Router
│       ├── App.tsx                 # 인증 라우팅
│       ├── vite-env.d.ts
│       ├── components/
│       │   ├── ui/                 # 9개 shadcn/ui 컴포넌트
│       │   │   ├── button.tsx
│       │   │   ├── card.tsx
│       │   │   ├── input.tsx
│       │   │   ├── label.tsx
│       │   │   ├── badge.tsx
│       │   │   ├── avatar.tsx
│       │   │   ├── skeleton.tsx
│       │   │   ├── separator.tsx
│       │   │   └── table.tsx
│       │   └── layout/             # 3개 레이아웃 컴포넌트
│       │       ├── Sidebar.tsx
│       │       ├── Header.tsx
│       │       └── Layout.tsx
│       ├── pages/                  # 10개 화면
│       │   ├── Login.tsx
│       │   ├── Dashboard.tsx
│       │   ├── DataSources.tsx
│       │   ├── Activities.tsx
│       │   ├── Health.tsx
│       │   ├── Graph.tsx
│       │   ├── Ask.tsx
│       │   ├── Insights.tsx
│       │   ├── Goals.tsx
│       │   └── Settings.tsx
│       ├── lib/
│       │   ├── api.ts              # 30+ API 메서드, fetch 래퍼
│       │   └── utils.ts            # cn() + 포맷터
│       ├── stores/
│       │   └── authStore.ts        # Zustand + persist
│       ├── types/
│       │   └── index.ts            # 20+ TypeScript 인터페이스
│       └── styles/
│           └── globals.css         # Tailwind + CSS 변수
│
└── docs/                           # 📚 이 문서 폰더
    ├── README.md
    ├── architecture.md
    ├── tech-stack.md
    ├── api-specification.md
    ├── database-schema.md
    ├── ui-screens.md
    ├── project-structure.md
    ├── getting-started.md
    └── mvp-features.md
```

---

## 파일 수 집계

| 영역 | 파일 수 | 설명 |
|------|---------|------|
| **Backend Java** | 59개 | Controller 10 + Service 12 + Repository 11 + Domain 12 + DTO 20 + Config 2 + Security 3 + Main 1 |
| **Backend Resources** | 2개 | application.yml + V1__init.sql |
| **Frontend TSX/TS** | 28개 | Pages 10 + Components 12 + Lib 2 + Stores 1 + Types 1 + Main/App 2 |
| **Frontend Config** | 7개 | vite, tsconfig, tailwind, postcss, nginx, html, css |
| **Infra** | 4개 | docker-compose, 2×Dockerfile, .env.example |
| **Docs** | 9개 | 이 문서들 |
| **총계** | **109개** | |

---

## 코드량 추정

| 영역 | 예상 줄 수 | 비고 |
|------|-----------|------|
| Backend Java | ~4,500줄 | 59개 파일 |
| Frontend TSX/TS | ~3,200줄 | 28개 파일 |
| SQL (Flyway) | ~250줄 | 12개 테이블 |
| Config/YAML | ~400줄 | Docker, nginx, etc |
| **총계** | **~8,350줄** | |
