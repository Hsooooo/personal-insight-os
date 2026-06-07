# 🗄️ 데이터베이스 스키마

## ERD (Entity Relationship Diagram)

```mermaid
erDiagram
    USERS ||--o{ PROVIDER_CONNECTIONS : has
    USERS ||--o{ ACTIVITIES : performs
    USERS ||--o{ GARMIN_DAILY_HEALTH_METRICS : has
    USERS ||--o{ GARMIN_SLEEP_SESSIONS : has
    USERS ||--o{ GOALS : sets
    USERS ||--o{ LLM_PROVIDERS : configures
    USERS ||--o{ QUESTIONS : asks
    USERS ||--o{ INSIGHTS : receives
    USERS ||--o{ EXERCISES : defines
    USERS ||--o{ GRAPH_NODE_MAPPINGS : owns

    ACTIVITIES ||--o{ GARMIN_ACTIVITY_LAPS : contains
    INSIGHTS ||--o{ INSIGHT_EVIDENCES : has
    QUESTIONS ||--o{ INSIGHTS : generates

    USERS {
        bigint id PK
        varchar email UK
        varchar password_hash
        varchar display_name
        timestamptz created_at
        timestamptz updated_at
    }

    PROVIDER_CONNECTIONS {
        bigint id PK
        bigint user_id FK
        varchar provider_type
        varchar connection_status
        jsonb auth_payload
        jsonb sync_config
        timestamptz last_synced_at
    }

    ACTIVITIES {
        bigint id PK
        bigint user_id FK
        bigint provider_connection_id FK
        varchar external_activity_id
        varchar source_type
        varchar activity_type
        varchar activity_name
        timestamp start_time
        int duration_seconds
        numeric distance_meters
        numeric average_pace_seconds
        int average_heart_rate
        int max_heart_rate
        int calories
        numeric elevation_gain_meters
        varchar user_tag
        jsonb raw_payload
        jsonb weight_training_detail
    }

    EXERCISES {
        bigint id PK
        bigint user_id FK
        varchar name UK
        varchar body_part
        timestamptz created_at
        timestamptz updated_at
    }

    GARMIN_ACTIVITY_LAPS {
        bigint id PK
        bigint activity_id FK
        int lap_index
        timestamptz start_time
        int duration_seconds
        numeric distance_meters
    }

    GARMIN_DAILY_HEALTH_METRICS {
        bigint id PK
        bigint user_id FK
        date metric_date UK
        int resting_heart_rate
        numeric hrv_avg
        numeric stress_avg
        int body_battery_min
        int body_battery_max
        int steps
        int calories_total
        jsonb raw_payload
    }

    GARMIN_SLEEP_SESSIONS {
        bigint id PK
        bigint user_id FK
        date sleep_date UK
        timestamptz start_time
        timestamptz end_time
        int total_sleep_seconds
        int deep_sleep_seconds
        int light_sleep_seconds
        int rem_sleep_seconds
        int awake_seconds
        int sleep_score
        jsonb raw_payload
    }

    GOALS {
        bigint id PK
        bigint user_id FK
        varchar title
        varchar goal_type
        text description
        numeric target_value
        varchar target_unit
        date start_date
        date target_date
        varchar status
    }

    INSIGHTS {
        bigint id PK
        bigint user_id FK
        bigint question_id FK
        varchar category
        varchar title
        text summary
        numeric confidence
        varchar model_provider
        varchar model_name
        varchar feedback_status
        boolean is_saved
    }

    INSIGHT_EVIDENCES {
        bigint id PK
        bigint insight_id FK
        varchar evidence_type
        varchar source_table
        bigint source_id
        text evidence_summary
        numeric weight
    }

    GRAPH_NODE_MAPPINGS {
        bigint id PK
        bigint user_id FK
        varchar source_table
        bigint source_id
        varchar neo4j_node_id
        varchar node_type
    }

    SYNC_LOGS {
        bigint id PK
        bigint user_id FK
        varchar provider_type
        varchar sync_type
        varchar status
        date date_from
        date date_to
        int activities_count
        int health_metrics_count
        int sleep_count
        text error_message
        timestamptz started_at
        timestamptz completed_at
        timestamptz created_at
    }
```

---

## 테이블 요약

| # | 테이블 | 설명 | 레코드 수 예상 |
|---|--------|------|---------------|
| 1 | `users` | 서비스 사용자 | 1~N |
| 2 | `provider_connections` | 외부 데이터 소스 연결 정보 + 동기화 설정 | 1~5 / 사용자 |
| 3 | `activities` | 운등 기록 (Garmin + 수동 입력). 날씨 정보(온도/습도/풍속/상태) 포함 | 100~500 / 사용자/년 |
| 4 | `garmin_activity_laps` | 운등 랩(구간) 기록 | 500~2000 / 사용자/년 |
| 5 | `garmin_daily_health_metrics` | 일일 건강 지표 | 365 / 사용자/년 |
| 6 | `garmin_sleep_sessions` | 수면 기록 | 365 / 사용자/년 |
| 7 | `goals` | 사용자 목표 | 5~20 / 사용자 |
| 8 | `llm_providers` | LLM Provider 설정 | 1~3 / 사용자 |
| 9 | `questions` | 사용자 질문 이력 | 50~200 / 사용자 |
| 10 | `insights` | 생성된 인사이트 | 100~500 / 사용자 |
| 11 | `insight_evidences` | 인사이트 근거 데이터 | 3~10 / 인사이트 |
| 12 | `graph_node_mappings` | PostgreSQL-Neo4j 매핑 | 1000~5000 / 사용자 |
| 13 | `sync_logs` | 동기화 이력 (상태, 기간, 레코드 수, 에러) | 100~500 / 사용자 |
| 14 | `exercises` | 사용자 정의 웨이트 트레이닝 종목 | 20~100 / 사용자 |
| 15 | `refresh_tokens` | Refresh Token 저장 (SHA-256 hash, rotation/ revoke 지원) | 1~3 / 사용자 |

---

## Neo4j 그래프 모델

```mermaid
flowchart LR
    subgraph 노드["노드 타입"]
        P["👤 Person<br/>{userId, name}"]
        D["📅 Date<br/>{date}"]
        A["🏃 Activity<br/>{name, type, distance}"]
        S["😴 Sleep<br/>{date, score, duration}"]
        H["❤️ HealthMetric<br/>{date, rhr, stress}"]
        G["🎯 Goal<br/>{title, type}"]
        I["💡 Insight<br/>{title, category}"]
        DS["📡 DataSource<br/>{type}"]
    end

    subgraph 관계["관계 타입"]
        R1["PERFORMED"]
        R2["HAS_SLEEP"]
        R3["HAS_METRIC"]
        R4["OCCURRED_ON"]
        R5["POSSIBLY_AFFECTS"]
        R6["DERIVED_FROM"]
        R7["ANSWERED_BY"]
        R8["PROVIDED"]
    end

    P -->|PERFORMED| A
    P -->|HAS_SLEEP| S
    P -->|HAS_METRIC| H
    A -->|OCCURRED_ON| D
    S -->|OCCURRED_ON| D
    H -->|MEASURED_ON| D
    S -->|POSSIBLY_AFFECTS| A
    I -->|DERIVED_FROM| A
    I -->|DERIVED_FROM| S
    I -->|DERIVED_FROM| H
    DS -->|PROVIDED| A
    DS -->|PROVIDED| S
    DS -->|PROVIDED| H

    style P fill:#6366f1,color:#fff
    style A fill:#10b981,color:#fff
    style S fill:#8b5cf6,color:#fff
    style H fill:#f43f5e,color:#fff
    style I fill:#f59e0b,color:#fff
```

### 관계 속성

| 속성 | 타입 | 설명 |
|------|------|------|
| `confidence` | float | 관계 신뢰도 (0~1) |
| `source` | string | SYSTEM_RULE / LLM_ANALYSIS / USER_CONFIRMED |
| `period_start` | date | 관계 분석 시작일 |
| `period_end` | date | 관계 분석 종료일 |
| `created_at` | datetime | 관계 생성 시각 |
| `reason` | string | 관계 생성 이유 |

---

## 설계 원칙

```text
1. Raw 데이터는 PostgreSQL에 저장
2. 의미 있는 요약 단위만 Neo4j에 저장
3. 초 단위 스트림은 노드화하지 않음
4. 일/활동/수면/인사이트 단위 중심으로 시작
5. 강한 인과관계(CAUSES) 대신 신중한 표현(POSSIBLY_AFFECTS) 사용
```
