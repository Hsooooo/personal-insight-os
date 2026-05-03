# API 명세 초안

## 문서 목적

MVP에서 필요한 API 엔드포인트와 각 API 그룹의 책임을 정리합니다. 세부 request/response DTO는 후속 문서에서 확정합니다.

## Data Sources API

```text
GET    /api/data-sources
POST   /api/data-sources/garmin/connect
POST   /api/data-sources/garmin/sync
GET    /api/data-sources/garmin/sync-logs
DELETE /api/data-sources/garmin
```

### 책임

```text
연결된 Provider 목록 조회
Garmin 연결 정보 저장
수동 동기화 요청
동기화 로그 조회
Garmin 연결 해제
```

## Activities API

```text
GET /api/activities
GET /api/activities/{activityId}
GET /api/activities/{activityId}/insights
POST /api/activities/{activityId}/analyze
```

### 책임

```text
운동 목록 조회
운동 상세 조회
운동 관련 인사이트 조회
특정 운동 기준 분석 요청
```

## Health API

```text
GET /api/health/timeline
GET /api/health/sleep
GET /api/health/metrics
GET /api/health/summary
```

### 책임

```text
건강 지표 타임라인 조회
수면 데이터 조회
지표별 데이터 조회
대시보드용 건강 요약 제공
```

## Graph API

```text
GET /api/graph
GET /api/graph/nodes/{nodeId}
GET /api/graph/nodes/{nodeId}/neighbors
GET /api/graph/relationships/{relationshipId}
```

### 책임

```text
개인 그래프 조회
노드 상세 조회
이웃 노드 조회
관계 상세 조회
```

## Ask API

```text
POST /api/ask
GET  /api/questions
GET  /api/questions/{questionId}
```

### 책임

```text
자연어 질문 처리
질문 기록 조회
질문별 답변/인사이트 조회
```

## Insights API

```text
GET    /api/insights
GET    /api/insights/{insightId}
POST   /api/insights/{insightId}/save
POST   /api/insights/{insightId}/feedback
DELETE /api/insights/{insightId}
```

### 책임

```text
인사이트 목록 조회
인사이트 상세 조회
인사이트 저장 상태 변경
피드백 저장
인사이트 삭제
```

## Goals API

```text
GET    /api/goals
POST   /api/goals
GET    /api/goals/{goalId}
PATCH  /api/goals/{goalId}
DELETE /api/goals/{goalId}
GET    /api/goals/{goalId}/insights
```

### 책임

```text
목표 목록 조회
목표 생성
목표 상세 조회
목표 수정
목표 삭제
목표 관련 인사이트 조회
```

## LLM Provider API

```text
GET    /api/settings/llm-providers
POST   /api/settings/llm-providers
PATCH  /api/settings/llm-providers/{providerId}
DELETE /api/settings/llm-providers/{providerId}
POST   /api/settings/llm-providers/{providerId}/test
```

### 책임

```text
LLM Provider 목록 조회
Provider 설정 등록
Provider 설정 수정
Provider 삭제
API Key와 모델 설정 테스트
```

## 공통 API 원칙

```text
모든 API는 user_id 스코프를 강제한다.
민감 정보는 response에 직접 노출하지 않는다.
삭제 API는 PostgreSQL과 Neo4j 정합성을 고려한다.
LLM 답변 API는 evidence 목록을 함께 반환한다.
그래프 API는 기간, 노드 타입, 관계 타입, 신뢰도 필터를 지원한다.
```

