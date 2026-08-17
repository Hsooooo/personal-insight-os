# Changelog

모든 주요 변경 사항은 이 파일에 기록됩니다.

형식은 [Keep a Changelog](https://keepachangelog.com/ko/1.0.0/)를 따륩니다.

---

## [Unreleased]

### Added
- Neo4j Goal/Question/Insight projection (`HAS_GOAL`, `ASKED`, `HAS_INSIGHT`, `ANSWERED_BY`, `DERIVED_FROM`, `SUPPORTED_BY`) + graph API/UI support
- AES-GCM encryption for LLM API keys and Garmin passwords (`SecretCryptoService`, startup migration)
- pgvector embeddings on `questions`/`insights` with similarity search in Ask RAG
- Feedback learning loop (IMPORTANT/WRONG) injected into Ask and weekly briefing prompts
- Goal blockers (`GoalBlockerAnalyzer`) on Goals API/UI and briefing context
- MCP write tools: create/update goal, save insight, submit feedback, trigger sync, generate briefing
- API key prefix lookup (`api_keys.key_prefix`) to avoid O(n) BCrypt scans
- **능동 이상 신호 알림** — 매일 12:30 KST 최근 3일 vs 28일 기준선 패턴 감지(`AnomalyAlertService`), Insight(`ANOMALY_ALERT`) + LLM 코칭 문구, 수동 트리거 `POST /api/insights/anomaly-alerts/detect`, Insights/Dashboard UI 배지
- **Garmin 데이터 전량 저장 (V24)** — 신규 `garmin_daily_raw` 범용 원문 테이블 + 일일 extras 9종(HRV/BodyBattery/Stress/HeartRate/Steps/Respiration/SpO2/TrainingReadiness/TrainingStatus) 수집, 수면(낮잠/수면필요량/HRV상태)·건강(SpO2/호흡/층수/활동칼로리/바디배터리 상세) 구조화 컬럼 + 기존 raw_payload SQL 백필, Health/Sleep API·Health 화면 낮잠 바 차트, RAG 통계에 평균 낮잠 메트릭 추가
- **주간 브리핑 스토리텔링 톤** — LLM 프롬프트를 스포츠 캐스터 페르소나로 변경 (내러티브 + 지표 간 인과 흐름)

### Changed
- Mock data button only in Vite DEV; Docker defaults `PIOS_MOCK_DATA_ENABLED=false`
- Garmin 자동 동기화 시각을 **KST 12:00**으로 명시 고정 (`@Scheduled zone=Asia/Seoul`)
  - 전날 수면이 Garmin에 반영될 여유를 두기 위한 의도된 시각
  - 주간 브리핑은 일요일 **13:00 KST** (sync 이후)
  - Docker backend에 `TZ=Asia/Seoul` 추가, 일 단위 집계는 `AppTimeZones.KST` 사용

### Added (earlier)
- **목표 진행률 엔진**
  - `GoalProgressCalculator` — `WEEKLY_RUN_DISTANCE` / `WEEKLY_ACTIVITY_COUNT` / `SLEEP_HOURS` / `WEIGHT_KG` 자동 집계
  - Goal API 응답에 `currentValue`, `progressPercent`, `paceStatus`, `projectedDate`, `warning` 추가
  - Goals UI: 유형 템플릿·목표값·기간·진행률 바
- **얇은 패턴 탐지**
  - `ThinPatternDetector` — 수면 점수 급락, RHR 상승, 운동량 급증, 연속 수면 부족, 바디배터리 하락
- **자동 주간 브리핑**
  - `WeeklyBriefingService` + 일요일 04:00 스케줄러
  - `GET /api/briefings/latest`, `POST /api/briefings/generate`
  - Insight `category=WEEKLY_BRIEFING` + STATS/GOAL_PROGRESS/PATTERN evidence
  - Dashboard 브리핑 카드·목표 진행률 미니 바
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
- **웨이트 트레이닝 종목명 선택 (Exercise 테이블)**
  - `V8__add_exercises_table.sql` — `exercises` 테이블 생성 + 기존 데이터 마이그레이션
  - `GET /api/activities/exercises` — 사용자별 종목명 목록
  - `Activities.tsx` — 기존 종목 `<select>` 또는 새 종목 직접 입력
- **AI 운등 요약 (WORKOUT_SUMMARY)**
  - `AskService` — "이번주 운등", "훈련 일지" 등 키워드 감지
  - `GarminActivityLapRepository` — 랩 데이터 조회
  - Garmin 활동은 랩 단위, 웨이트는 종목/세트 단위로 표 형태 포맷팅
  - LLM 프롬프트: 주간 운등 요약 + 총평
- **Garmin 랩(lap) 데이터 동기화 저장**
  - Python `garmin_sync.py` — `get_activity_splits()` 호출, 랩 데이터를 activity JSON에 `laps` 키로 첨부
  - Java `GarminSyncService` — `GarminActivityLapRepository` 주입, activity 저장 후 랩 데이터 delete-insert 저장
  - `GarminActivityLapRepository` — `deleteByActivity(Activity)` 메서드 추가
  - 랩 없는 활동(수면 등)은 예외 처리 후 빈 배열 반환

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
  `docs/api-specification.md`, `docs/mvp-features.md`, `docs/database-schema.md` 최신화
- `CHANGELOG.md` 신규 생성
