# Personal Insight OS

> 개인의 건강, 운동, 기록 데이터를 도메인별 노드와 관계 그래프로 구조화하고, LLM 기반 RAG를 통해 근거 있는 개인화 인사이트를 제공하는 웹서비스

## Architecture

```
┌─────────────┐     ┌──────────────┐     ┌─────────────┐
│   React 19  │────▶│ Spring Boot  │────▶│ PostgreSQL  │
│  + Vite     │     │   3.3 (Java) │     │  + pgvector │
│  + Tailwind │◀────│  + JWT Auth  │◀────│   (Data)    │
└─────────────┘     └──────────────┘     └─────────────┘
                           │
                           ▼
                    ┌──────────────┐
                    │   Neo4j 5    │
                    │  (Graph)     │
                    └──────────────┘
                           │
                           ▼
                    ┌──────────────┐
                    │  OpenAI API  │
                    │  (Optional)  │
                    └──────────────┘
```

## Quick Start

### Prerequisites
- Docker & Docker Compose
- (Optional) OpenAI API Key for LLM insights
- (Optional) Neo4j 인스턴스 (외부/클라우드, `NEO4J_*` 환경변수)

### Environment

```bash
cp .env.example .env
# 필수: 값이 없으면 docker compose / 백엔드가 기동을 거부한다
#   JWT_SECRET           openssl rand -base64 48
#   PIOS_ENCRYPTION_KEY  openssl rand -base64 48   (JWT_SECRET과 다른 값)
```

- 회원가입은 기본 비활성화입니다. 첫 계정을 만들 때만 `PIOS_REGISTRATION_ENABLED=true`로 기동하세요.
- 암호화 키를 교체할 때는 이전 값을 `PIOS_ENCRYPTION_KEY_LEGACY`에 넣고 기동하면 저장된 비밀값이 새 키로 재암호화됩니다.

### Run (Docker Compose)

```bash
docker compose up -d --build
```

- 외부 진입점은 Caddy(80/443) 하나입니다: `/api` → backend, `/mcp` → MCP 서버, 그 외 → frontend.
- Postgres(5432)와 MCP(8001)는 `127.0.0.1`에만 바인딩됩니다.

### Local Development

```bash
docker compose up -d postgres          # DB만 컨테이너로
cd backend && JWT_SECRET=... PIOS_ENCRYPTION_KEY=... mvn spring-boot:run   # :8080
cd frontend && npm install && npm run dev                                  # :5173
```

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

## Tech Stack

### Backend
- Spring Boot 3.3 + Java 21
- Spring Security + JWT
- Spring Data JPA
- Neo4j Java Driver
- Flyway (DB Migration)
- OpenAI Java SDK

### Frontend
- React 19 + TypeScript
- Vite
- Tailwind CSS
- shadcn/ui (custom)
- Recharts (charts)
- Cytoscape.js + cytoscape-fcose (graph visualization)
- TanStack Query
- Zustand
- Pretendard (font)

### Infrastructure
- PostgreSQL 15 + pgvector 0.5.1
- Neo4j (외부/클라우드 인스턴스)
- Docker Compose + Caddy

## Project Structure

```
.
├── docker-compose.yml
├── backend/
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/java/com/pios/
│       ├── PiosApplication.java
│       ├── config/
│       ├── controller/
│       ├── domain/
│       ├── dto/
│       ├── repository/
│       ├── security/
│       ├── service/
│       └── graph/
│   └── src/main/resources/
│       ├── application.yml
│       └── db/migration/
├── frontend/
│   ├── Dockerfile
│   ├── package.json
│   ├── vite.config.ts
│   ├── nginx.conf
│   └── src/
│       ├── main.tsx
│       ├── App.tsx
│       ├── components/
│       ├── pages/
│       ├── stores/
│       ├── lib/
│       └── types/
├── mcp/                 # MCP 서버 (Python, streamable-http)
└── docs/                # 설계/운영 문서 (docs/archive: 초기 기획 초안)
```

## Screens

1. **Dashboard** — 요약 카드, 7일 트렌드 차트, 최근 인사이트, 빠른 질문
2. **Data Sources** — Garmin 연결/동기화
3. **Activities** — 운동 목록 및 필터
4. **Health Timeline** — 수면, 심박, 스트레스, 걸음수 차트
5. **Personal Graph** — Neo4j 그래프 시각화 (Cytoscape) — 날짜/활동/컨디션/레이스 필터
6. **Ask My Data** — 자연어 질의 + 근거 기반 답변
7. **Insights** — 인사이트 목록, 저장, 피드백
8. **Goals** — 목표 설정 및 관리
9. **Settings** — LLM Provider 설정

## Development

### Tests & CI
```bash
cd backend && mvn test          # 백엔드 단위 테스트
cd frontend && npm run build    # 타입 체크 + 빌드
```

GitHub Actions(`.github/workflows/ci.yml`)가 push/PR마다 위 두 가지와 Python 스크립트 컴파일을 검사합니다.

### API Documentation

All APIs return `ApiResponse<T>`:
```json
{
  "success": true,
  "message": null,
  "data": { ... }
}
```

Authentication: `Authorization: Bearer <token>`

## License

MIT
