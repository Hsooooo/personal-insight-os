# 도메인 모델과 PostgreSQL 데이터 모델

## 문서 목적

MVP에서 다룰 핵심 도메인과 PostgreSQL 테이블 초안을 정리합니다. PostgreSQL은 원천 데이터와 정형 데이터를 보관하는 시스템 오브 레코드 역할을 맡습니다.

## 핵심 도메인

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

## PostgreSQL 설계 원칙

```text
Raw JSON은 반드시 저장한다.
사용자별 유니크 제약으로 중복 수집을 막는다.
외부 Provider의 원본 ID는 별도 컬럼으로 보관한다.
LLM 답변의 근거는 insight_evidences로 추적 가능해야 한다.
Neo4j 노드와 PostgreSQL row의 연결은 graph_node_mappings에 저장한다.
```

## users

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

## provider_connections

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

## garmin_activities

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

## garmin_activity_laps

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

## garmin_daily_health_metrics

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

## garmin_sleep_sessions

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

## goals

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

## llm_providers

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

## questions

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

## insights

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

## insight_evidences

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

## graph_node_mappings

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

## 후속 상세화 필요 항목

```text
동기화 로그 테이블
Provider 인증 정보 암호화 전략
Insight feedback enum 정리
질문/응답 원문 저장 테이블 여부
벡터 저장소 또는 embedding 컬럼 설계
삭제 요청 시 PostgreSQL/Neo4j 동시 삭제 정책
```

