# Personal Insight OS — MVP 개발 문서

> **기획 문서 1개**를 기반으로 **6개 Phase, 총 103개 소스 파일**로 MVP를 완성한 전체 개발 기록입니다.

---

## 한눈에 보는 전체 구조

```mermaid
flowchart TB
    subgraph 사용자["👤 사용자"]
        A[React 19 Frontend<br/>localhost:3000]
    end

    subgraph 백엔드["⚙️ Spring Boot Backend<br/>localhost:8080"]
        B[JWT 인증]
        C[REST API 10개 Controller]
        D[Service Layer 12개]
    end

    subgraph 데이터["💾 데이터 계층"]
        E[(PostgreSQL 16<br/>원천/정형 데이터)]
        F[(Neo4j 5<br/>그래프 관계)]
    end

    subgraph 외부["🌐 외부 연동"]
        G[OpenAI API<br/>GPT-4o-mini]
        H[Garmin Connect<br/>(Mock 데이터)]
    end

    A -->|Bearer JWT| B
    B --> C
    C --> D
    D --> E
    D --> F
    D --> G
    D --> H

    style A fill:#6366f1,color:#fff
    style B fill:#10b981,color:#fff
    style E fill:#f59e0b,color:#fff
    style F fill:#06b6d4,color:#fff
    style G fill:#f43f5e,color:#fff
```

---

## 문서 목차

| # | 문서 | 내용 |
|---|------|------|
| 1 | [📐 아키텍처](architecture.md) | 시스템 아키텍처, 데이터 흐름, 배포 구조 |
| 2 | [🛠 기술 스택](tech-stack.md) | Backend/Frontend/Infra 스택 비교표 |
| 3 | [📡 API 명세](api-specification.md) | 30+ 엔드포인트, 요청/응답 예시 |
| 4 | [🗄️ DB 스키마](database-schema.md) | ERD, 12개 테이블 DDL, Neo4j 그래프 모델 |
| 5 | [🖥️ 화면 스펙](ui-screens.md) | 10개 화면, 기능-API 매핑 |
| 6 | [📁 프로젝트 구조](project-structure.md) | 파일 트리, 103개 소스 파일 목록 |
| 7 | [🚀 실행 가이드](getting-started.md) | Docker Compose 빌드 & 실행 방법 |
| 8 | [📋 MVP 기능 체크리스트](mvp-features.md) | 13개 MVP 기능 상세 설명 |

---

## 실행 한 줄 명령

```bash
cd /path/to/personal-insight-os
docker-compose up --build
```

| 서비스 | URL |
|--------|-----|
| 웹 앱 | http://localhost:3000 |
| API | http://localhost:8080 |
| Neo4j Browser | http://localhost:7474 |
