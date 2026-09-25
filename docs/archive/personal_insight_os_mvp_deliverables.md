# Personal Insight OS MVP 기획 산출물

## 0. 문서 목적

이 문서는 개인화 인사이트 웹사이트 MVP 개발을 위한 1차 산출물입니다.

현재 아이디어는 단순한 Garmin 대시보드가 아니라, 개인의 건강/운동/기록 데이터를 도메인 노드와 관계 그래프로 구조화하고, LLM 기반 RAG를 통해 근거 있는 개인화 인사이트를 제공하는 웹서비스입니다.

---

# 1. 제품 정의

## 1.1 제품 한 줄 정의

나의 건강, 운동, 기록, 생각, 행동 데이터를 수집하고, 이를 도메인별 노드와 관계 그래프로 구조화한 뒤, LLM을 통해 근거 기반 개인화 인사이트를 제공하는 웹서비스입니다.

## 1.2 제품형 설명

흩어진 개인 데이터를 하나의 지식 그래프로 연결해 “내가 왜 이런 상태인지”를 설명해주는 Personal Insight OS입니다.

## 1.3 핵심 가치

기존 건강/운동 앱은 데이터를 보여주는 데 집중합니다.

이 서비스는 데이터를 연결하고 해석하는 데 집중합니다.

예를 들어 단순히 아래처럼 보여주는 것이 아닙니다.

```text
오늘 수면 6시간 10분
러닝 8.2km
평균 심박 152
스트레스 높음
```

이 서비스는 다음과 같은 질문에 답하는 것을 목표로 합니다.

```text
최근 컨디션이 떨어진 이유는 무엇인가?

러닝 기록이 좋았던 날들의 공통점은 무엇인가?

수면 부족이 내 운동 성과에 어떤 영향을 주는가?

최근 내 생활 패턴에서 위험 신호가 보이는 부분은 무엇인가?

내 목표와 실제 행동은 얼마나 일치하고 있는가?
```

---

# 2. MVP 범위

## 2.1 MVP에서 반드시 포함할 기능

```text
1. 사용자 로그인
2. Garmin 데이터 연동
3. Garmin 데이터 동기화
4. PostgreSQL 원천 데이터 저장
5. 도메인 모델 변환
6. Neo4j 노드/엣지 생성
7. 기본 대시보드
8. 개인 그래프 조회
9. LLM Provider API Key 등록
10. Ask My Data 자연어 질의
11. 근거 기반 RAG 응답
12. 인사이트 저장
13. 인사이트 피드백
```

## 2.2 MVP에서 제외할 기능

```text
1. Obsidian 완전 연동
2. Notion 연동
3. Google Calendar 연동
4. 모바일 앱
5. 의료 진단 기능
6. 실시간 동기화
7. 복잡한 그래프 자동 추론
8. 조직/팀 단위 기능
9. 결제 기능
10. 고급 권한 관리
```

## 2.3 MVP 판단 기준

MVP는 다음 질문에 답할 수 있으면 성공입니다.

```text
Garmin 데이터를 수집해서 내 운동/수면/건강 지표를 조회할 수 있는가?

수집된 데이터를 Activity, Sleep, HealthMetric, Date, Insight 같은 도메인으로 변환할 수 있는가?

PostgreSQL에는 원천/정형 데이터를 저장하고, Neo4j에는 의미 있는 관계를 저장할 수 있는가?

사용자가 자연어로 질문했을 때, 실제 데이터 근거를 기반으로 답변할 수 있는가?

생성된 인사이트를 저장하고, 사용자가 맞음/틀림/애매함으로 피드백할 수 있는가?
```

---

# 3. 사용자 시나리오

## 3.1 시나리오 A: Garmin 데이터 연결

```text
사용자는 서비스에 로그인한다.
사용자는 Data Sources 화면으로 이동한다.
사용자는 Garmin 계정 정보를 연결한다.
시스템은 garminconnect >= 0.3.0 기반으로 데이터를 가져온다.
시스템은 운동 기록, 수면, 건강 지표를 PostgreSQL에 저장한다.
시스템은 주요 데이터를 도메인 노드로 변환한다.
시스템은 Neo4j에 Activity, Sleep, HealthMetric, Date 노드를 생성한다.
```

## 3.2 시나리오 B: 오늘 상태 확인

```text
사용자는 Home Dashboard에 접속한다.
시스템은 최근 수면, 안정시 심박, 스트레스, Body Battery, 운동 기록을 요약한다.
시스템은 최근 7일 기준 컨디션 흐름을 계산한다.
사용자는 오늘 운동 강도를 올려도 되는지 판단할 수 있다.
```

## 3.3 시나리오 C: 내 데이터에 질문하기

```text
사용자는 Ask My Data 화면에 접속한다.
사용자는 “최근 컨디션이 떨어진 이유가 뭐야?”라고 질문한다.
시스템은 질문 의도를 분석한다.
시스템은 최근 7일-30일 데이터를 조회한다.
시스템은 PostgreSQL에서 정형 데이터를 조회한다.
시스템은 Neo4j에서 관련 관계를 조회한다.
시스템은 벡터 검색으로 관련 인사이트/요약을 가져온다.
LLM은 근거 데이터를 기반으로 답변을 생성한다.
사용자는 답변과 근거를 함께 확인한다.
```

## 3.4 시나리오 D: 인사이트 피드백

```text
시스템이 “최근 피로는 수면 부족보다 운동 강도 누적과 관련 있어 보입니다.”라는 인사이트를 생성한다.
사용자는 해당 인사이트를 확인한다.
사용자는 “맞음”, “애매함”, “틀림”, “중요함” 중 하나로 평가한다.
시스템은 피드백을 저장한다.
향후 인사이트 생성 시 이 피드백을 참고한다.
```

---

# 4. 사이트맵

```text
/
- Home Dashboard

/data-sources
- Provider 연결 관리
- Garmin 연결 상태
- 동기화 로그
- 향후 Provider 후보

/activities
- 운동 목록
- 운동 상세
- 운동별 인사이트

/health
- 수면
- 안정시 심박
- 스트레스
- HRV
- Body Battery
- 회복 지표

/graph
- 개인 지식 그래프
- 노드 탐색
- 관계 탐색
- 노드 상세

/ask
- 내 데이터에 질문하기
- 답변 근거 확인
- 관련 데이터 보기

/insights
- 인사이트 목록
- 저장된 인사이트
- 패턴 탐지 결과
- 주간 리포트

/goals
- 목표 설정
- 목표별 관련 데이터
- 목표 진행률
- 방해 요인 분석

/settings
- LLM Provider 설정
- API Key 등록
- 모델 선택
- 데이터 동기화 설정
```

---

# 5. 화면별 스토리보드

## 5.1 Home Dashboard

### 화면 목적

오늘의 몸 상태, 최근 운동 흐름, 수면/회복 상태, 주요 인사이트를 한 화면에서 보여줍니다.

### 주요 구성

```text
1. 상단 요약 카드
   - 오늘 컨디션
   - 최근 수면
   - 안정시 심박
   - 스트레스
   - Body Battery
   - 최근 운동 부하

2. 최근 인사이트
   - 자동 생성된 인사이트 카드
   - 중요 인사이트 표시
   - 근거 보기 버튼

3. 최근 7일 흐름
   - 수면 시간
   - 운동 거리
   - 평균 심박
   - 스트레스
   - 회복 지표

4. 빠른 질문
   - 최근 컨디션이 안 좋은 이유는?
   - 이번 주 훈련 강도는 적절해?
   - 러닝 기록이 좋았던 날들의 공통점은?
```

### 주요 액션

```text
인사이트 상세 보기
Ask My Data로 이동
최근 운동 상세 보기
Health Timeline으로 이동
```

### 예외 케이스

```text
Garmin 미연동 상태
최근 데이터 없음
동기화 실패
LLM Provider 미설정
```

---

## 5.2 Data Sources

### 화면 목적

Garmin 및 향후 개인 데이터 소스 연결 상태를 관리합니다.

### 주요 구성

```text
1. 연결된 Provider 카드
   - Garmin
   - 연결 상태
   - 마지막 동기화 시간
   - 가져온 데이터 수
   - 오류 여부

2. 동기화 액션
   - 수동 동기화
   - 최근 7일 재동기화
   - 전체 재동기화

3. 향후 연동 후보
   - Obsidian
   - Notion
   - Google Calendar
   - GitHub
   - Todoist
   - Strava
```

### 주요 액션

```text
Garmin 연결
Garmin 연결 해제
수동 동기화
동기화 로그 확인
```

### 예외 케이스

```text
Garmin 로그인 실패
세션 만료
동기화 중 네트워크 오류
중복 데이터 수집
Provider 인증 정보 만료
```

---

## 5.3 Activities

### 화면 목적

Garmin에서 수집한 운동 데이터를 조회하고 분석합니다.

### 주요 구성

```text
1. 운동 목록
   - 날짜
   - 운동 유형
   - 거리
   - 시간
   - 평균 페이스
   - 평균 심박
   - 칼로리

2. 필터
   - 기간
   - 운동 유형
   - 거리 범위
   - 강도

3. 운동 상세
   - 기본 정보
   - 랩 정보
   - 심박 변화
   - 페이스 변화
   - 고도 변화
   - 관련 인사이트
```

### 주요 액션

```text
운동 상세 보기
관련 인사이트 생성
그래프에서 보기
Ask My Data에 이 운동 기준으로 질문하기
```

---

## 5.4 Health Timeline

### 화면 목적

수면, 심박, 스트레스, HRV, Body Battery 같은 건강 지표를 시간 흐름으로 보여줍니다.

### 주요 구성

```text
1. 날짜별 건강 요약
2. 수면 그래프
3. 안정시 심박 그래프
4. 스트레스 그래프
5. HRV 그래프
6. Body Battery 그래프
7. 운동일/휴식일 마커
```

### 주요 액션

```text
기간 변경
지표 선택
특정 날짜 상세 보기
관련 운동 보기
관련 인사이트 보기
```

---

## 5.5 Personal Graph

### 화면 목적

운동, 수면, 건강 지표, 목표, 인사이트 간의 관계를 그래프로 탐색합니다.

### 주요 구성

```text
1. 그래프 영역
   - Person
   - Date
   - Activity
   - Sleep
   - HealthMetric
   - Goal
   - Insight

2. 필터 영역
   - 기간
   - 노드 타입
   - 관계 타입
   - 신뢰도

3. 노드 상세 패널
   - 노드 타입
   - 원천 데이터
   - 연결된 노드
   - 관련 인사이트

4. 관계 상세 패널
   - 관계 타입
   - 신뢰도
   - 생성 근거
   - 생성 방식
```

### 주요 액션

```text
노드 클릭
노드 확장
관계 상세 보기
해당 노드로 질문하기
잘못된 관계 숨기기
```

---

## 5.6 Ask My Data

### 화면 목적

사용자가 자연어로 자신의 데이터에 질문하고, 근거 기반 답변을 받습니다.

### 주요 구성

```text
1. 질문 입력창
2. 추천 질문 템플릿
3. 답변 영역
4. 근거 데이터 영역
5. 관련 노드/관계 영역
6. 추가 질문 추천
7. 인사이트 저장 버튼
8. 피드백 버튼
```

### 질문 예시

```text
최근 컨디션이 떨어진 이유가 뭐야?

러닝 기록이 좋았던 날들의 공통점은 뭐야?

수면이 부족하면 내 운동 성과가 얼마나 떨어져?

이번 주 훈련 강도는 적절했어?

최근 한 달 동안 가장 좋은 루틴은 뭐였어?
```

### 답변 구조

```text
1. 결론
2. 근거 요약
3. 관련 데이터
4. 관련 그래프 관계
5. 신뢰도
6. 추가 확인 질문
```

---

## 5.7 Insights

### 화면 목적

생성된 인사이트를 저장하고, 검색하고, 다시 확인할 수 있게 합니다.

### 주요 구성

```text
1. 인사이트 목록
2. 카테고리 필터
   - 운동
   - 수면
   - 회복
   - 스트레스
   - 목표
   - 패턴

3. 중요도 필터
4. 피드백 상태 필터
   - 맞음
   - 애매함
   - 틀림
   - 중요함

5. 인사이트 상세
   - 결론
   - 근거
   - 관련 데이터
   - 관련 노드
   - 생성 일시
   - 사용 모델
```

---

## 5.8 Goals

### 화면 목적

사용자의 목표를 등록하고, 실제 데이터와 목표의 정렬 여부를 분석합니다.

### 목표 예시

```text
하프마라톤 기록 단축
체지방 감량
수면 개선
운동 루틴 유지
회복 관리
금연 유지
영어 학습
사이드프로젝트 완성
```

### 주요 구성

```text
1. 목표 목록
2. 목표 상세
3. 목표 관련 지표
4. 목표와 연결된 데이터
5. 방해 요인 분석
6. 주간 목표 리뷰
```

---

## 5.9 Settings

### 화면 목적

LLM Provider, 모델, API Key, 동기화 설정을 관리합니다.

### 주요 구성

```text
1. LLM Provider 설정
   - OpenAI
   - Anthropic
   - Google Gemini
   - OpenRouter
   - Ollama
   - Local LLM

2. 모델 설정
   - 기본 Chat Model
   - 분석용 Model
   - 임베딩 Model
   - 저비용 분류 Model

3. 비용 제한
   - 월 사용량 제한
   - 요청당 토큰 제한

4. 데이터 설정
   - 동기화 주기
   - 보관 기간
   - 삭제 옵션
```

---

# 6. 도메인 모델

## 6.1 핵심 도메인

```text
User
- 서비스 사용자

ProviderConnection
- Garmin, Obsidian, Notion 등 외부 데이터 연결 정보

Activity
- 운동 기록

ActivityLap
- 운동 구간 기록

Sleep
- 수면 기록

HealthMetric
- 안정시 심박, HRV, 스트레스, Body Battery 등 건강 지표

DateNode
- 특정 날짜를 나타내는 그래프 기준 노드

Goal
- 사용자가 설정한 목표

Insight
- 시스템 또는 LLM이 생성한 인사이트

Question
- 사용자가 던진 질문

Evidence
- 답변에 사용된 근거 데이터

GraphNodeMapping
- PostgreSQL row와 Neo4j node를 연결하는 매핑 정보
```

---

# 7. PostgreSQL 테이블 초안

## 7.1 users

```sql
create table users (
    id bigserial primary key,
    email varchar(255) not null unique,
    password_hash varchar(255),
    display_name varchar(100),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);
```

## 7.2 provider_connections

```sql
create table provider_connections (
    id bigserial primary key,
    user_id bigint not null references users(id),
    provider_type varchar(50) not null,
    connection_status varchar(30) not null,
    auth_payload jsonb,
    last_synced_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (user_id, provider_type)
);
```

## 7.3 garmin_activities

```sql
create table garmin_activities (
    id bigserial primary key,
    user_id bigint not null references users(id),
    provider_connection_id bigint references provider_connections(id),
    garmin_activity_id varchar(100) not null,
    activity_type varchar(50),
    activity_name varchar(255),
    start_time timestamptz,
    duration_seconds integer,
    distance_meters numeric(12,2),
    average_pace_seconds numeric(10,2),
    average_heart_rate integer,
    max_heart_rate integer,
    calories integer,
    elevation_gain_meters numeric(10,2),
    raw_payload jsonb not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (user_id, garmin_activity_id)
);
```

## 7.4 garmin_activity_laps

```sql
create table garmin_activity_laps (
    id bigserial primary key,
    activity_id bigint not null references garmin_activities(id),
    lap_index integer not null,
    start_time timestamptz,
    duration_seconds integer,
    distance_meters numeric(12,2),
    average_pace_seconds numeric(10,2),
    average_heart_rate integer,
    max_heart_rate integer,
    raw_payload jsonb,
    created_at timestamptz not null default now(),
    unique (activity_id, lap_index)
);
```

## 7.5 garmin_daily_health_metrics

```sql
create table garmin_daily_health_metrics (
    id bigserial primary key,
    user_id bigint not null references users(id),
    metric_date date not null,
    resting_heart_rate integer,
    hrv_avg numeric(10,2),
    stress_avg numeric(10,2),
    body_battery_min integer,
    body_battery_max integer,
    steps integer,
    calories_total integer,
    raw_payload jsonb not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (user_id, metric_date)
);
```

## 7.6 garmin_sleep_sessions

```sql
create table garmin_sleep_sessions (
    id bigserial primary key,
    user_id bigint not null references users(id),
    sleep_date date not null,
    start_time timestamptz,
    end_time timestamptz,
    total_sleep_seconds integer,
    deep_sleep_seconds integer,
    light_sleep_seconds integer,
    rem_sleep_seconds integer,
    awake_seconds integer,
    sleep_score integer,
    raw_payload jsonb not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (user_id, sleep_date)
);
```

## 7.7 goals

```sql
create table goals (
    id bigserial primary key,
    user_id bigint not null references users(id),
    title varchar(255) not null,
    goal_type varchar(50) not null,
    description text,
    target_value numeric(12,2),
    target_unit varchar(50),
    start_date date,
    target_date date,
    status varchar(30) not null default 'ACTIVE',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);
```

## 7.8 llm_providers

```sql
create table llm_providers (
    id bigserial primary key,
    user_id bigint not null references users(id),
    provider_name varchar(50) not null,
    api_key_encrypted text,
    default_chat_model varchar(100),
    embedding_model varchar(100),
    enabled boolean not null default true,
    monthly_budget_limit numeric(12,2),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (user_id, provider_name)
);
```

## 7.9 questions

```sql
create table questions (
    id bigserial primary key,
    user_id bigint not null references users(id),
    question_text text not null,
    intent varchar(100),
    time_range_start date,
    time_range_end date,
    created_at timestamptz not null default now()
);
```

## 7.10 insights

```sql
create table insights (
    id bigserial primary key,
    user_id bigint not null references users(id),
    question_id bigint references questions(id),
    category varchar(50),
    title varchar(255) not null,
    summary text not null,
    confidence numeric(4,3),
    model_provider varchar(50),
    model_name varchar(100),
    feedback_status varchar(30),
    is_saved boolean not null default false,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);
```

## 7.11 insight_evidences

```sql
create table insight_evidences (
    id bigserial primary key,
    insight_id bigint not null references insights(id),
    evidence_type varchar(50) not null,
    source_table varchar(100),
    source_id bigint,
    evidence_summary text,
    weight numeric(4,3),
    created_at timestamptz not null default now()
);
```

## 7.12 graph_node_mappings

```sql
create table graph_node_mappings (
    id bigserial primary key,
    user_id bigint not null references users(id),
    source_table varchar(100) not null,
    source_id bigint not null,
    neo4j_node_id varchar(100) not null,
    node_type varchar(50) not null,
    created_at timestamptz not null default now(),
    unique (source_table, source_id, node_type)
);
```

---

# 8. Neo4j 그래프 모델 초안

## 8.1 노드 타입

```text
Person
Date
Activity
Sleep
HealthMetric
Goal
Insight
Question
DataSource
```

## 8.2 관계 타입

```text
(:Person)-[:PERFORMED]->(:Activity)

(:Person)-[:HAS_SLEEP]->(:Sleep)

(:Person)-[:HAS_METRIC]->(:HealthMetric)

(:Activity)-[:OCCURRED_ON]->(:Date)

(:Sleep)-[:OCCURRED_ON]->(:Date)

(:HealthMetric)-[:MEASURED_ON]->(:Date)

(:Activity)-[:RELATED_TO]->(:HealthMetric)

(:Sleep)-[:POSSIBLY_AFFECTS]->(:Activity)

(:Insight)-[:DERIVED_FROM]->(:Activity)

(:Insight)-[:DERIVED_FROM]->(:Sleep)

(:Insight)-[:DERIVED_FROM]->(:HealthMetric)

(:Question)-[:ANSWERED_BY]->(:Insight)

(:Goal)-[:SUPPORTED_BY]->(:Activity)

(:Goal)-[:AFFECTED_BY]->(:HealthMetric)

(:DataSource)-[:PROVIDED]->(:Activity)

(:DataSource)-[:PROVIDED]->(:Sleep)

(:DataSource)-[:PROVIDED]->(:HealthMetric)
```

## 8.3 관계 속성

```text
confidence
- 관계 신뢰도

source
- SYSTEM_RULE
- LLM_ANALYSIS
- USER_CONFIRMED

period_start
period_end
- 관계 분석 기간

created_at
- 관계 생성 시각

reason
- 관계 생성 이유
```

## 8.4 중요한 설계 원칙

초기에는 `AFFECTS`, `CAUSES` 같은 강한 표현을 피하고, `POSSIBLY_AFFECTS`, `CORRELATES_WITH`, `RELATED_TO` 같은 신중한 관계명을 사용합니다.

의료적·생리학적 인과관계를 단정하지 않고, 개인 데이터 기반의 패턴 후보로 표현합니다.

---

# 9. RAG 파이프라인 설계

## 9.1 전체 흐름

```text
사용자 질문
↓
질문 의도 분석
↓
관련 기간 추론
↓
관련 데이터 타입 결정
↓
PostgreSQL 정형 데이터 조회
↓
Neo4j 관계 조회
↓
Vector Search
↓
근거 후보 수집
↓
LLM 답변 생성
↓
근거 포함 응답 반환
↓
인사이트 저장
↓
사용자 피드백 반영
```

## 9.2 질문 의도 타입

```text
CONDITION_ANALYSIS
- 최근 컨디션 분석

PERFORMANCE_ANALYSIS
- 운동 성과 분석

RECOVERY_ANALYSIS
- 회복 상태 분석

SLEEP_ANALYSIS
- 수면 영향 분석

GOAL_ALIGNMENT
- 목표와 실제 행동 정렬 분석

PATTERN_DETECTION
- 반복 패턴 탐지

DATA_LOOKUP
- 단순 데이터 조회
```

## 9.3 응답 포맷

```text
결론:
최근 컨디션 저하는 수면 시간 자체보다 운동 강도 누적과 회복 지표 저하와 더 관련 있어 보입니다.

근거:
- 최근 7일 고강도 운동 3회
- 안정시 심박 평균 상승
- Body Battery 회복 저조
- 수면 시간은 큰 폭으로 줄지 않았으나 회복 점수가 낮게 유지됨

관련 데이터:
- Activity #123
- Sleep #45
- HealthMetric 2026-04-21

신뢰도:
중간

추가 확인 질문:
최근 업무 스트레스나 주관적 피로 기록이 있다면 함께 분석할 수 있습니다.
```

## 9.4 환각 방지 원칙

```text
1. 답변보다 근거 수집이 먼저입니다.
2. 실제 조회된 데이터가 없는 내용은 말하지 않습니다.
3. 인과관계를 단정하지 않습니다.
4. “가능성이 있습니다”, “관련 있어 보입니다” 수준으로 표현합니다.
5. 의료 진단처럼 보이는 표현은 금지합니다.
6. 모든 인사이트에는 근거 데이터를 연결합니다.
```

---

# 10. LLM Provider 설계

## 10.1 Provider 후보

```text
OpenAI
Anthropic
Google Gemini
OpenRouter
Ollama
Local LLM
```

## 10.2 모델 사용 분리

```text
일반 질문 답변
- 고성능 Chat Model

데이터 요약
- 중간급 모델

분류/태깅
- 저비용 모델

임베딩
- Embedding 전용 모델

주간 리포트
- 고성능 모델
```

## 10.3 Provider Adapter 인터페이스 초안

```java
public interface LlmProviderAdapter {
    String providerName();

    ChatResponse chat(ChatRequest request);

    EmbeddingResponse embed(EmbeddingRequest request);

    boolean supportsEmbedding();
}
```

## 10.4 주요 설정

```text
provider_name
api_key_encrypted
default_chat_model
embedding_model
enabled
monthly_budget_limit
temperature
max_tokens
```

---

# 11. 데이터 파이프라인

## 11.1 Garmin 동기화 흐름

```text
Garmin Sync Request
↓
garminconnect >= 0.3.0 호출
↓
운동/수면/건강 데이터 수집
↓
Raw JSON 저장
↓
정규화 테이블 저장
↓
Domain Normalizer 실행
↓
Graph Projector 실행
↓
Embedding Worker 실행
↓
Insight Candidate 생성
```

## 11.2 Graph Projector 역할

```text
PostgreSQL의 정형 데이터를 읽는다.
각 row에 대응하는 Neo4j 노드를 생성한다.
Date 노드와 연결한다.
Person 노드와 연결한다.
DataSource 노드와 연결한다.
기본 관계를 생성한다.
생성된 Neo4j node id를 graph_node_mappings에 저장한다.
```

## 11.3 Insight Worker 역할

```text
최근 데이터 변화를 분석한다.
일간/주간 패턴 후보를 찾는다.
LLM에게 근거 데이터와 함께 요약을 요청한다.
생성된 인사이트를 insights에 저장한다.
근거 데이터를 insight_evidences에 저장한다.
필요한 경우 Neo4j에 Insight 노드를 생성한다.
```

---

# 12. API 초안

## 12.1 Data Sources API

```text
GET    /api/data-sources
POST   /api/data-sources/garmin/connect
POST   /api/data-sources/garmin/sync
GET    /api/data-sources/garmin/sync-logs
DELETE /api/data-sources/garmin
```

## 12.2 Activities API

```text
GET /api/activities
GET /api/activities/{activityId}
GET /api/activities/{activityId}/insights
POST /api/activities/{activityId}/analyze
```

## 12.3 Health API

```text
GET /api/health/timeline
GET /api/health/sleep
GET /api/health/metrics
GET /api/health/summary
```

## 12.4 Graph API

```text
GET /api/graph
GET /api/graph/nodes/{nodeId}
GET /api/graph/nodes/{nodeId}/neighbors
GET /api/graph/relationships/{relationshipId}
```

## 12.5 Ask API

```text
POST /api/ask
GET  /api/questions
GET  /api/questions/{questionId}
```

## 12.6 Insights API

```text
GET   /api/insights
GET   /api/insights/{insightId}
POST  /api/insights/{insightId}/save
POST  /api/insights/{insightId}/feedback
DELETE /api/insights/{insightId}
```

## 12.7 Goals API

```text
GET    /api/goals
POST   /api/goals
GET    /api/goals/{goalId}
PATCH  /api/goals/{goalId}
DELETE /api/goals/{goalId}
GET    /api/goals/{goalId}/insights
```

## 12.8 LLM Provider API

```text
GET    /api/settings/llm-providers
POST   /api/settings/llm-providers
PATCH  /api/settings/llm-providers/{providerId}
DELETE /api/settings/llm-providers/{providerId}
POST   /api/settings/llm-providers/{providerId}/test
```

---

# 13. 확장 아이디어

## 13.1 Obsidian 연동

현재는 MVP 핵심 기능에서 제외하고, 확장 후보로 둡니다.

향후 방향:

```text
Markdown 파일 import
Frontmatter 파싱
태그 추출
백링크 추출
노트 chunking
Note 노드 생성
Topic 노드 생성
Note-Topic 관계 생성
운동/수면/건강 데이터와 노트 내용 연결
```

## 13.2 다른 통합 후보

```text
Notion
- 개인 지식 관리와 DB 기반 기록 연동

Google Calendar
- 일정 밀도와 컨디션 관계 분석

Todoist / TickTick
- 할 일 완료율과 회피 패턴 분석

GitHub
- 개발 활동량과 컨디션/몰입 패턴 분석

Strava
- Garmin 외 운동 데이터 보완

Apple Health / Health Connect
- 모바일 건강 데이터 허브 확장

Readwise / Reader
- 읽은 글, 하이라이트, 관심사 분석
```

## 13.3 주간 리포트

```text
이번 주 운동 요약
이번 주 수면/회복 요약
이번 주 컨디션 변화
가장 좋았던 날
가장 무너졌던 날
반복 패턴
다음 주 제안
```

## 13.4 패턴 탐지

```text
수면 부족 다음 날 운동 성과 저하
고강도 운동 2일 후 피로 증가
스트레스 높은 날 안정시 심박 상승
휴식일 이후 페이스 개선
특정 요일마다 컨디션 저하
```

## 13.5 Simulation 기능

```text
이번 주에 인터벌을 한 번 더 넣으면 회복 리스크가 커질까?

다음 달 하프마라톤을 목표로 하면 현재 패턴상 가장 큰 리스크는 무엇인가?

수면을 평균 30분 늘리면 운동 성과에 어떤 변화가 예상되는가?
```

---

# 14. 개발 Phase 제안

## Phase 1. 도메인 모델과 저장 구조

```text
User
ProviderConnection
GarminActivity
GarminSleep
GarminHealthMetric
Insight
Question
Goal
GraphNodeMapping
```

완료 기준:

```text
PostgreSQL 스키마 생성
기본 Entity/DTO/Mapper 구성
Garmin raw 저장 가능
```

## Phase 2. Garmin 동기화

```text
Garmin 연결 정보 저장
운동 데이터 수집
수면 데이터 수집
건강 지표 수집
중복 방지
동기화 로그 저장
```

완료 기준:

```text
사용자 기준 Garmin 데이터를 DB에 저장할 수 있음
수동 동기화 가능
```

## Phase 3. 그래프 변환

```text
Person 노드 생성
Date 노드 생성
Activity 노드 생성
Sleep 노드 생성
HealthMetric 노드 생성
기본 관계 생성
PostgreSQL-Neo4j 매핑 저장
```

완료 기준:

```text
Garmin 데이터가 Neo4j 그래프로 투영됨
Personal Graph 화면에서 조회 가능
```

## Phase 4. RAG 질의

```text
질문 입력
질문 의도 분석
PostgreSQL 조회
Neo4j 조회
LLM 답변 생성
근거 표시
```

완료 기준:

```text
사용자가 자신의 데이터에 질문하고 근거 기반 답변을 받을 수 있음
```

## Phase 5. 인사이트 저장과 피드백

```text
인사이트 저장
근거 저장
피드백 저장
저장된 인사이트 목록 조회
```

완료 기준:

```text
LLM 답변이 일회성으로 사라지지 않고 개인 지식으로 누적됨
```

## Phase 6. 목표 관리

```text
목표 등록
목표 관련 데이터 연결
목표 기준 인사이트 생성
```

완료 기준:

```text
사용자의 목표와 실제 생활 데이터가 연결됨
```

---

# 15. v0 UI 생성 프롬프트 초안

## 15.1 전체 대시보드 프롬프트

```text
Create a modern personal insight dashboard web app for a service called Personal Insight OS.

The app analyzes personal Garmin health and activity data, stores domain nodes in a personal graph, and allows users to ask questions to their own data using LLM-based RAG.

Use a clean SaaS dashboard layout with a left sidebar.

Sidebar items:
- Dashboard
- Data Sources
- Activities
- Health Timeline
- Personal Graph
- Ask My Data
- Insights
- Goals
- Settings

Dashboard main sections:
- Today Condition card
- Sleep and Recovery summary
- Recent Activities
- Health Metrics trend
- Latest Insights
- Recommended Questions

Use shadcn/ui, Tailwind CSS, responsive layout, cards, charts, and tables.
```

## 15.2 Ask My Data 프롬프트

```text
Create an Ask My Data page for a personal health and activity insight app.

The page should allow users to ask natural language questions about their Garmin health, sleep, activity, and recovery data.

Layout:
- Large question input area
- Suggested question chips
- Answer card with conclusion
- Evidence section
- Related graph nodes section
- Confidence indicator
- Follow-up question suggestions
- Feedback buttons: Correct, Unclear, Wrong, Important

Use a clean analytical interface with shadcn/ui and Tailwind CSS.
```

## 15.3 Personal Graph 프롬프트

```text
Create a Personal Graph Explorer page for a personal insight web app.

The graph shows relationships between:
- Person
- Date
- Activity
- Sleep
- HealthMetric
- Goal
- Insight
- Question
- DataSource

Layout:
- Main graph canvas
- Left filter panel with node type, relationship type, date range, confidence
- Right detail panel for selected node or relationship
- Bottom related insights panel

Use React Flow style graph visualization, shadcn/ui, and a modern dark-friendly dashboard design.
```

---

# 16. 리스크와 대응 전략

## 16.1 Garmin 데이터 안정성

리스크:

```text
garminconnect 라이브러리 기반 연동은 비공식 흐름일 수 있어 변경에 취약할 수 있음
```

대응:

```text
Raw JSON 저장
동기화 실패 로그 저장
Provider Adapter 구조로 분리
FIT/TCX/GPX 파일 업로드 fallback 고려
Strava 연동 확장 후보 유지
```

## 16.2 그래프 과설계

리스크:

```text
모든 데이터를 노드로 만들면 그래프가 복잡해지고 의미가 흐려짐
```

대응:

```text
Raw 데이터는 PostgreSQL에 저장
의미 있는 요약 단위만 Neo4j에 저장
초 단위 스트림은 노드화하지 않음
일/활동/수면/인사이트 단위 중심으로 시작
```

## 16.3 LLM 환각

리스크:

```text
LLM이 실제 데이터에 없는 조언을 생성할 수 있음
```

대응:

```text
Evidence-first RAG
근거 없는 답변 금지
답변에 신뢰도 표시
사용자 피드백 수집
의료적 단정 표현 금지
```

## 16.4 개인정보 보안

리스크:

```text
건강 데이터와 개인 기록은 민감 정보에 해당할 수 있음
```

대응:

```text
API Key 암호화 저장
Provider 인증 정보 암호화
데이터 삭제 기능 제공
LLM 전송 데이터 최소화
로컬 LLM 옵션 고려
민감 데이터 마스킹 옵션 제공
```

---

# 17. 현재 단계의 결론

이 프로젝트의 핵심은 Garmin 연동 자체가 아닙니다.

Garmin은 첫 번째 개인 데이터 소스입니다.

진짜 핵심은 다음입니다.

```text
개인의 데이터를 도메인화한다.

도메인화된 정보를 노드로 만든다.

노드 간 관계를 구성한다.

PostgreSQL에는 원천과 정형 데이터를 저장한다.

Neo4j에는 의미 관계를 저장한다.

LLM은 RAG를 통해 근거 기반으로 개인화 인사이트를 생성한다.

사용자는 인사이트를 저장하고 교정한다.
```

따라서 이 서비스는 단순 건강 대시보드가 아니라, 개인의 몸과 행동을 이해하기 위한 그래프 기반 자기이해 시스템입니다.

---

# 18. 다음 작업 후보

다음 단계에서 바로 이어서 만들 산출물은 다음 중 하나입니다.

```text
1. PostgreSQL DDL 상세화
2. Neo4j Cypher 생성 스크립트
3. Spring Boot 패키지 구조 설계
4. API 명세서 상세화
5. 화면별 상세 스토리보드 확장
6. v0용 프롬프트 세트 정리
7. MVP 개발 태스크 백로그 작성
```

추천 순서는 다음입니다.

```text
1. PostgreSQL DDL 상세화
2. Neo4j 그래프 모델 확정
3. API 명세서 작성
4. 화면별 스토리보드 확장
5. 개발 태스크 백로그 작성
```

