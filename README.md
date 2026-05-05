# Personal Insight OS

> 개인의 건강, 운등, 기록 데이터를 도메인별 노드와 관계 그래프로 구조화하고, LLM 기반 RAG를 통해 근거 있는 개인화 인사이트를 제공하는 웹서비스

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

### Run

```bash
# 1. Clone and navigate
cd personal-insight-os

# 2. Set environment (optional)
cp .env.example .env
# Edit .env and add your OPENAI_API_KEY

# 3. Start all services
docker-compose up --build

# 4. Open browser
# Frontend: http://localhost:3000
# Backend API: http://localhost:8080
```

### Default Accounts
- PostgreSQL: `pios` / `pios123`

## MVP Features

- [x] 사용자 로그인 (JWT)
- [x] Garmin 데이터 연동 (Mock 데이터 생성)
- [x] Garmin 데이터 동기화
- [x] PostgreSQL 원천 데이터 저장
- [x] 도메인 모델 변환
- [x] Neo4j 노드/엣지 생성
- [x] 기본 대시보드 (차트, 요약)
- [x] 개인 그래프 조회 (Cytoscape) — 날짜/뷰/레이스 필터 지원
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
- PostgreSQL 16 + pgvector
- Neo4j (외부/클로드 인스턴스)
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
└── kimi/docs/           # 기획 문서
```

## Screens

1. **Dashboard** — 요약 카드, 7일 트렌드 차트, 최근 인사이트, 빠른 질문
2. **Data Sources** — Garmin 연결/동기화
3. **Activities** — 운등 목록 및 필터
4. **Health Timeline** — 수면, 심박, 스트레스, 걸음수 차트
5. **Personal Graph** — Neo4j 그래프 시각화 (Cytoscape) — 날짜/활동/컨디션/레이스 필터
6. **Ask My Data** — 자연어 질의 + 근거 기반 답변
7. **Insights** — 인사이트 목록, 저장, 피드백
8. **Goals** — 목표 설정 및 관리
9. **Settings** — LLM Provider 설정

## Development

### Backend Only
```bash
cd backend
./mvnw spring-boot:run
```

### Frontend Only
```bash
cd frontend
npm install
npm run dev
```

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
