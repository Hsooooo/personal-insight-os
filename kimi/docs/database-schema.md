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

## 7.13 ERD 개요

```
users ──┬── provider_connections
        ├── garmin_activities ─── garmin_activity_laps
        ├── garmin_daily_health_metrics
        ├── garmin_sleep_sessions
        ├── goals
        ├── llm_providers
        ├── questions ─── insights ─── insight_evidences
        └── graph_node_mappings
```
