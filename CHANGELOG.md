# Changelog

모든 주요 변경 사항은 이 파일에 기록됩니다.

형식은 [Keep a Changelog](https://keepachangelog.com/ko/1.0.0/)를 따륩니다.

---

## [Unreleased]

### Added
- 그래프 뷰 필터링 (`GET /api/graph?days=&view=&raceCategory=`)
  - 날짜 윈도우: 7일 / 14일 / 30일 / 전체
  - 뷰 모드: 활동(activities) / 컨디션(condition) / 통합(all)
  - 레이스 카테고리: 5K / 10K / 하프(HALF) / 풀(FULL) / 커스텀(CUSTOM)
  - 백엔드: 동적 Cypher 쿼리로 조걶 필터링
  - 프론트엔드: Graph.tsx 상단 필터 바 + 탭 UI
- 웨이트 트레이닝 세트에 `durationSeconds` 필드 추가
  - 백엔드: `WeightTrainingRequest.SetRequest`, `ActivityService`
  - 프론트엔드: `Activities.tsx` 세트 입력 UI, `types/index.ts`
- Admin 백필 엔드포인트 (`POST /api/admin/backfill`)
  - `AdminController` + `GraphProjectorService` 연동

### Changed
- Neo4j 인프라: 로컬 Docker 컨테이너 → 외부/클로드 인스턴스
  - `docker-compose.yml`에서 neo4j 서비스 제거
  - 백엔드 연결 정보를 `.env` 환경변수(`NEO4J_URI`, `NEO4J_USERNAME`, `NEO4J_PASSWORD`)로 주입
  - `.env.example`에 Neo4j 변수 예시 추가
- 리버스 프록시: nginx → Caddy (자동 HTTPS)
- 그래프 시각화 라이브러리: React Flow → Cytoscape.js + cytoscape-fcose

### Fixed
- `GraphService` Cypher 쿼리 파라미터 바인딩 기호(`$`) 누락 버그 수정

### Docs
- `README.md`, `AGENTS.md`, `docs/getting-started.md`, `docs/architecture.md`,
  `docs/api-specification.md`, `docs/mvp-features.md` 최신화
- `CHANGELOG.md` 신규 생성
