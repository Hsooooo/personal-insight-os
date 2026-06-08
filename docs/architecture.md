# 📐 아키텍처

## 전체 시스템 다이어그램

```mermaid
flowchart TB
    subgraph 클라이언트["🖥️ 클라이언트 (React 19)"]
        direction TB
        UI1[Login 화면]
        UI2[Dashboard 화면]
        UI3[Ask My Data 화면]
        UI4[Graph 탐색 화면]
        UI5[Settings 화면]
        Zustand[Zustand<br/>Auth Store]
        RQ[TanStack Query<br/>Server State]
    end

    subgraph 백엔드["⚙️ 백엔드 (Spring Boot 3.3)"]
        direction TB
        subgraph 보안["🔐 Security Layer"]
            JWT[JWT Filter]
            SC[SecurityConfig<br/>CORS 설정]
        end

        subgraph API["📡 REST API Layer"]
            AuthC[AuthController]
            DashC[DashboardController]
            AskC[AskController]
            GraphC[GraphController]
            DataC[DataSourceController]
            ActivityC[ActivityController]
            HealthC[HealthController]
            InsightC[InsightController]
            GoalC[GoalController]
            LLMC[LlmProviderController]
        end

        subgraph 비즈니스["💼 Business Layer"]
            AuthS[AuthService]
            AskS[AskService<br/>RAG Pipeline]
            GraphS[GraphService]
            GraphP[GraphProjectorService]
            GarminSync[GarminSyncService]
            GarminPython[GarminPythonClient]
            SyncSchedule[SyncScheduleService]
            MockS[MockDataService]
        end

        subgraph 도메인["📦 Domain Layer"]
            Entity1[User Entity]
            Entity2[GarminActivity Entity]
            Entity3[Insight Entity]
            Entity4[... 9개 더]
        end
    end

    subgraph 데이터["💾 데이터 계층"]
        direction TB
        PG[(PostgreSQL 16<br/>12개 테이블<br/>pgvector 확장)]
        NEO[(Neo4j<br/>외부/클로드 인스턴스)]
    end

    subgraph 외부["🌐 외부 서비스"]
        OAI[OpenAI API]
        Garmin[Garmin Connect<br/>Mock 데이터]
    end

    UI1 -->|POST /api/auth/login| AuthC
    UI2 -->|GET /api/dashboard/summary| DashC
    UI3 -->|POST /api/ask| AskC
    UI4 -->|GET /api/graph| GraphC
    UI5 -->|CRUD /api/settings| LLMC

    AuthC --> AuthS
    AskC --> AskS
    GraphC --> GraphS
    DataC --> GarminSync
    GarminSync --> GarminPython
    GarminPython --> Python[Python Script<br/>garminconnect lib]
    SyncSchedule --> GarminSync

    AuthS --> Entity1
    AskS --> Entity3
    AskS --> OAI
    GraphP --> PG
    GraphP --> NEO
    Python --> Garmin[Garmin Connect API]

    Zustand -->|Bearer Access Token| JWT
    Zustand -->|Cookie Refresh Token| AuthC
    RQ --> API

    style 클라이언트 fill:#6366f1,color:#fff
    style 백엔드 fill:#10b981,color:#fff
    style 데이터 fill:#f59e0b,color:#fff
    style 외부 fill:#f43f5e,color:#fff
```

---

## 인증 흐름 (JWT + Refresh Token)

```mermaid
sequenceDiagram
    actor U as 사용자
    participant FE as React Frontend
    participant BE as Spring Boot
    participant PG as PostgreSQL

    U->>FE: 로그인 (email, password)
    FE->>BE: POST /api/auth/login
    BE->>PG: Refresh Token SHA-256 hash 저장
    BE-->>FE: Access Token (json) + Refresh Token (HttpOnly Cookie)
    FE->>FE: Zustand에 access token 저장

    U->>FE: API 호출
    FE->>BE: Authorization: Bearer <access>
    BE-->>FE: 200 OK

    alt Access Token 만료 (401)
        FE->>BE: POST /api/auth/refresh (withCredentials)
        BE->>BE: Cookie의 refresh_token 검증
        BE->>PG: 기존 Refresh Token 삭제 (Rotation)
        BE->>PG: 새 Refresh Token 저장
        BE-->>FE: 새 Access Token + 새 Refresh Token Cookie
        FE->>BE: 원래 요청 재시도
    end

    U->>FE: 로그아웃
    FE->>BE: POST /api/auth/logout
    BE->>PG: 해당 사용자 Refresh Token 전부 삭제
    BE-->>FE: Set-Cookie: refresh_token=; Max-Age=0
    FE->>FE: Zustand 상태 초기화
```

| 구성 요소 | 설명 |
|-----------|------|
| **Access Token** | JWT (HS256), 만료 24h, `Authorization: Bearer` 헤더로 전송 |
| **Refresh Token** | JWT (HS256), 만료 7일, `HttpOnly; Secure; SameSite=Strict` 쿠키로 저장 |
| **Rotation** | Refresh 시 기존 토큰 DB에서 삭제 후 새로 발급 (재사용 공격 방지) |
| **Revoke** | 로그아웃 시 DB에서 해당 사용자의 모든 refresh token 삭제 |
| **Silent Refresh** | 프론트에서 401 수신 시 `/api/auth/refresh` 자동 호출, 성공하면 대기 중인 요청 재시도 |

---

## 데이터 흐름 (RAG 파이프라인)

```mermaid
sequenceDiagram
    actor U as 사용자
    participant FE as React Frontend
    participant BE as Spring Boot
    participant PG as PostgreSQL
    participant NEO as Neo4j
    participant OAI as OpenAI API

    U->>FE: "최근 컨디션이 떨어진 이유가 뭐야?"
    FE->>BE: POST /api/ask

    BE->>PG: 질문 저장 (questions 테이블)
    BE->>PG: 최근 7일 건강 지표 조회
    BE->>PG: 최근 7일 수면 조회
    BE->>PG: 최근 활동 조회
    BE->>NEO: 관련 그래프 관계 조회

    alt OpenAI API Key 설정됨
        BE->>OAI: GPT-4o-mini 호출<br/>(프롬프트 + 근거 데이터)
        OAI-->>BE: 근거 기반 답변
    else API Key 없음
        BE-->>BE: Fallback 응답 생성
    end

    BE->>PG: 인사이트 저장 (insights 테이블)
    BE->>PG: 근거 저장 (insight_evidences 테이블)
    BE-->>FE: 결론 + 근거 + 신뢰도
    FE-->>U: 답변 + 피드백 버튼
```

---

## 데이터 동기화 흐름

```mermaid
sequenceDiagram
    actor U as 사용자
    participant FE as Frontend
    participant BE as Backend
    participant PG as PostgreSQL
    participant NEO as Neo4j
    participant PY as Python Script
    participant GC as Garmin Connect

    U->>FE: "Garmin 동기화" 클릭 + 범위 선택
    FE->>BE: POST /api/data-sources/garmin/sync<br/>(syncType, dateFrom, dateTo)

    BE->>PG: sync_log 생성 (status: RUNNING)
    BE->>BE: Rate limit 체크 (30초)

    alt FULL 동기화
        BE->>BE: Async 백그라운드 실행
    end

    loop 30일 청크 단위
        BE->>PY: ProcessBuilder 실행<br/>(email, password, chunk_from, chunk_to)
        PY->>GC: garminconnect API 호출<br/>(activities + splits + body_composition)
        GC-->>PY: Raw JSON
        PY-->>BE: activities + health + sleep + weights<br/>(activities에 laps 포함)
        BE->>PG: UPSERT 저장 (activities, health, sleep, weights)
        BE->>PG: 랩 데이터 저장<br/>(garmin_activity_laps, delete-insert)
    end

    BE->>PG: sync_log 완료 (status: COMPLETED)
    BE->>BE: GraphProjector 실행
    BE->>NEO: Person 노드 생성/업데이트
    BE->>NEO: Activity/Sleep/HealthMetric 노드 생성<br/>(HealthMetric에 weight 속성 포함)
    BE->>NEO: Race 노드 생성 (태그 기반 분류)
    BE->>NEO: 관계 엣지 생성<br/>(PERFORMED, HAS_SLEEP, HAS_METRIC, TAGGED_AS)
    BE->>PG: graph_node_mappings 저장

    BE-->>FE: 동기화 완료 응답
    FE-->>U: 최신 데이터 반영 + Sync History 표시
```

---

## 배포 아키텍처 (Docker Compose)

```mermaid
flowchart LR
    subgraph Docker["🐳 Docker Compose"]
        direction TB

        subgraph 네트워크["pios-network (bridge)"]
            FE["🖥️ frontend<br/>nginx:alpine<br/>port 3000"]
            BE["⚙️ backend<br/>eclipse-temurin:21-jre<br/>port 8080"]
            PG["🐘 postgres<br/>ankane/pgvector<br/>port 5432"]
            NEO["🕸️ Neo4j<br/>외부 인스턴스<br/>neo4j+s://"]
        end
    end

    사용자["👤 사용자 브라우저"] -->|HTTP| FE
    FE -->|/api proxy| BE
    BE -->|JDBC| PG
    BE -->|Bolt| NEO

    style FE fill:#6366f1,color:#fff
    style BE fill:#10b981,color:#fff
    style PG fill:#f59e0b,color:#fff
    style NEO fill:#06b6d4,color:#fff
```

| 서비스 | 이미지 | 포트 | 역할 |
|--------|--------|------|------|
| frontend | nginx:alpine | 3000 | React 빌드 결과물 정적 서빙 |
| backend | eclipse-temurin:21-jre | 8080 | Spring Boot 애플리케이션 |
| postgres | ankane/pgvector | 5432 | 원천 데이터 + 벡터 저장 |
| caddy | caddy:2-alpine | 80/443 | 리버스 프록시 + 자동 HTTPS |
