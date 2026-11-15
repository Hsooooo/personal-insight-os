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
        numeric weight_kg
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
        numeric race_distance_km
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
        jsonb evidence_data
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
        int weights_count
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
| 3 | `activities` | 운동 기록 (Garmin + 수동 입력). 날씨 정보(온도/습도/풍속/상태) 포함 | 100~500 / 사용자/년 |
| 4 | `garmin_activity_laps` | 운동 랩(구간) 기록 | 500~2000 / 사용자/년 |
| 5 | `garmin_daily_health_metrics` | 일일 건강 지표 (체중 포함) | 365 / 사용자/년 |
| 6 | `garmin_sleep_sessions` | 수면 기록 | 365 / 사용자/년 |
| 7 | `goals` | 사용자 목표 | 5~20 / 사용자 |
| 8 | `llm_providers` | LLM Provider 설정 | 1~3 / 사용자 |
| 9 | `questions` | 사용자 질문 이력 | 50~200 / 사용자 |
| 10 | `insights` | 생성된 인사이트 | 100~500 / 사용자 |
| 11 | `insight_evidences` | 인사이트 근거 데이터 (텍스트 요약 + `evidence_data` JSONB) | 3~10 / 인사이트 |
| 12 | `graph_node_mappings` | PostgreSQL-Neo4j 매핑 | 1000~5000 / 사용자 |
| 13 | `sync_logs` | 동기화 이력 (상태, 기간, 레코드 수, 에러) | 100~500 / 사용자 |
| 14 | `exercises` | 사용자 정의 웨이트 트레이닝 종목 | 20~100 / 사용자 |
| 15 | `refresh_tokens` | Refresh Token 저장 (SHA-256 hash, rotation/ revoke 지원) | 1~3 / 사용자 |
| 16 | `garmin_daily_raw` | Garmin 일일 부가 엔드포인트 원문 (HRV/BodyBattery/Stress/HeartRate/Steps/Respiration/SpO2/TrainingReadiness/TrainingStatus), (user_id, metric_date, data_type) 유니크 upsert | 9 × 365 / 사용자/년 |

| 17 | `oauth_clients` | 동적 등록(RFC 7591) 클라이언트. Client ID Metadata Document 클라이언트는 저장하지 않음 (V25) | 소수 |
| 18 | `oauth_authorization_requests` | 동의 대기 중인 `/authorize` 요청 (TTL 10분, `expires_at` 인덱스) (V25) | 일시적 |
| 19 | `oauth_grants` | 사용자의 클라이언트 승인(`user_id` FK, `revoked_at`, `last_used_at`) (V25) | 1~10 / 사용자 |
| 20 | `oauth_authorization_codes` | 인가 코드 (SHA-256 hex, 단일 사용 `used_at`, TTL 5분) (V25) | 일시적 |
| 21 | `oauth_refresh_tokens` | refresh token (SHA-256 hex, 사용 시 회전, `used_at`로 재사용 탐지, `grant_id` FK) (V25) | 1~N / grant |

> 마이그레이션은 V1~V28이 적용됩니다. 상세 DDL은 `backend/src/main/resources/db/migration/`.

### OAuth 스키마 (V25)

```text
users 1 ─ N oauth_grants 1 ─ N oauth_refresh_tokens
                     └──── N oauth_authorization_codes
oauth_clients (client_id UNIQUE; redirect_uris JSON 배열, grant_types 공백 구분)
oauth_authorization_requests (id VARCHAR(64) PK; code_challenge, scope, resource, state, expires_at)
```
- 코드/refresh token은 원문을 저장하지 않고 `code_hash`/`token_hash`(UNIQUE)만 저장합니다. grant 삭제 시 하위 코드/토큰은 `ON DELETE CASCADE`.
- 만료 행은 `OAuthCleanupService`가 정리합니다 (`expires_at` 인덱스 사용).
- access token은 JWT(stateless)라 별도 테이블이 없으며, MCP 서버가 introspect로 검증합니다.

### V27: 레이스 목표

- `goals.race_distance_km` 추가 (V28에서 `NUMERIC(8,4)`로 확장 — 하프 21.0975km) — `goal_type='RACE'`의 대회 거리(km). 목표 기록은 `target_value`(초, `target_unit='s'`), 대회일은 `target_date`

### V26: 동기화 안정성 / 성능 / 데이터 정리

- **동기화**: 마이그레이션 시점의 `sync_logs.status='RUNNING'`을 모두 `FAILED`로 정리한 뒤 부분 유니크 인덱스 `uq_sync_logs_running (user_id, provider_type) WHERE status='RUNNING'`을 생성 — 사용자/Provider당 RUNNING은 1건. `idx_sync_logs_user_provider_started (user_id, provider_type, started_at DESC)` 추가. `sync_logs.status` 값: `PENDING`/`RUNNING`/`COMPLETED`/`PARTIAL`/`FAILED`
- **sync_config**: 미사용 `auto_sync_cron` 키 제거, 기본값은 `sync_range_days: 7` (스케줄은 전역 `sync.schedule.cron`)
- **조회 인덱스**: `idx_insights_user_category_created`, `idx_insights_user_feedback`, `idx_insights_user_created`, `idx_insight_evidences_insight`, `idx_activities_user_start`, `idx_goals_user`, `idx_questions_user_created`, `idx_graph_node_mappings_user`
- **데이터 정리(0 → NULL)**: 과거 수집기가 값이 없을 때 0으로 저장해 평균/기준선을 오염시켰으므로 `resting_heart_rate=0`, `hrv_avg=0`, `stress_avg<=0`, `sleep_score=0`, `total_sleep_seconds=0`, 활동 `average_heart_rate=0`/`max_heart_rate=0`을 NULL로 변경 (되돌릴 수 없음)

### Garmin 구조화 확장 컬럼 (V24)

- `garmin_sleep_sessions`: `nap_seconds`(낮잠), `avg_sleep_stress`, `sleep_need_minutes`(Garmin 수면 필요량), `hrv_status`
- `garmin_daily_health_metrics`: `average_spo2`, `lowest_spo2`, `avg_waking_respiration`, `floors_ascended/descended`, `moderate/vigorous_intensity_minutes`, `active_kilocalories`, `bmr_kilocalories`, `body_battery_at_wake/charged/drained`, `total_distance_meters`
- 기존 `raw_payload`(jsonb)에서 SQL 백필로 복구되며, Garmin 재수집 없이 적용됨

### `insight_evidences.evidence_data` JSONB

RAG v2에서 생성된 근거는 텍스트 요약과 함께 구조화된 수치 데이터를 JSONB로 저장합니다.

```json
{
  "metric": "평균 HRV",
  "currentValue": 42,
  "baselineValue": 48,
  "changeRate": -12,
  "unit": "ms",
  "date": "2026-06-19",
  "route": "/health?date=2026-06-19"
}
```

| 필드 | 설명 |
|------|------|
| `metric` | 지표명 |
| `currentValue` | 분석 기간 평균/합계 값 |
| `baselineValue` | 기준선 기간 평균/합계 값 |
| `changeRate` | 변화율(%) |
| `unit` | 단위(ms, bpm, 시간, km, 회 등) |
| `date` | 근거의 대표 날짜 |
| `route` | 프론트엔드 라우트(활동 상세 또는 Health 화면) |

---

## Neo4j 그래프 모델

```mermaid
flowchart LR
    subgraph 노드["노드 타입"]
        P["Person<br/>userId, name"]
        A["Activity<br/>name, type, distance"]
        S["Sleep<br/>date, score, duration"]
        H["HealthMetric<br/>date, rhr, stress, weight"]
        R["Race<br/>name, category"]
        G["Goal<br/>title, goalType, status"]
        Q["Question<br/>text, intent"]
        I["Insight<br/>title, category, confidence"]
    end

    subgraph 관계["관계 타입"]
        R1["PERFORMED"]
        R2["HAS_SLEEP"]
        R3["HAS_METRIC"]
        R4["TAGGED_AS"]
        R5["HAS_GOAL"]
        R6["ASKED"]
        R7["HAS_INSIGHT"]
        R8["ANSWERED_BY"]
        R9["DERIVED_FROM"]
        R10["SUPPORTED_BY"]
    end

    P -->|PERFORMED| A
    P -->|HAS_SLEEP| S
    P -->|HAS_METRIC| H
    P -->|HAS_GOAL| G
    P -->|ASKED| Q
    P -->|HAS_INSIGHT| I
    A -->|TAGGED_AS| R
    Q -->|ANSWERED_BY| I
    I -->|DERIVED_FROM| A
    I -->|DERIVED_FROM| S
    I -->|DERIVED_FROM| H
    I -->|SUPPORTED_BY| A
```

### 시크릿 저장

| 컬럼 | 암호화 |
|------|--------|
| `llm_providers.api_key_encrypted` | AES-GCM (`ENC:` prefix). `SecretCryptoService` + 기동 시 평문 마이그레이션 |
| `provider_connections.auth_payload.password` | 동일 AES-GCM. email은 평문 유지 |

### 임베딩 / API 키 조회

| 변경 | 설명 |
|------|------|
| `questions.embedding` / `insights.embedding` | `vector(1536)` + HNSW (cosine). V22 |
| `api_keys.key_prefix` | raw key 앞 16자 인덱스. BCrypt 검증 후보 축소. V23 |

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

---

## Finance 스키마

Finance 도메인은 PostgreSQL에 우선 저장하며, 이번 1차 구현에서는 Neo4j 투영 대상에서 제외한다.

| 테이블 | 설명 |
|--------|------|
| `finance_cycles` | 월급 입금 직후부터 다음 월급 직전까지의 분석 cycle |
| `finance_transactions` | 앱 export에서 import된 수입/지출 거래. `user_id + source_fingerprint`로 멱등성 보장. 원본 `asset`과 선택적 `account_id`를 함께 저장 |
| `finance_accounts` | 사용자가 정의한 계좌/지갑/목적자금/부채 마스터 |
| `finance_account_aliases` | import 원본 `asset` 값을 계좌에 연결하는 alias |
| `recurring_bill_templates` | KT 통신비 같은 반복 청구 프로필 |
| `recurring_bill_template_versions` | 특정 `effective_cycle_id`부터 적용되는 고정비 구성 버전 |
| `recurring_bill_template_items` | 월정액, 부가서비스, 할인, 부가세 등 버전별 상세 항목 |

`finance_transactions`는 현금흐름과 소비분석을 분리하기 위해 `cashflow_included`, `spending_included`, `cashflow_amount`, `spending_amount`를 별도로 가진다. 원본 `amount`는 청구/결제 row의 실제 금액으로 보존하고, 화면 합계는 분석용 금액을 사용한다. 통신비 납부액과 단말기할부금은 실제 청구/납부 소비로 `spending_amount`에 포함한다. 소액결제 원거래는 `cashflow_amount=0`인 Deferred Spending이지만, 사용일 기준 소비분석에는 실제 소비 카테고리의 `spending_amount`로 포함한다.

엑셀 export의 년월일은 원본 날짜로 신뢰하고 `transaction_date`에 보존한다. 사용자가 Transactions 탭에서 시:분을 후보정하면 `transaction_at`만 변경하고 `time_adjusted`, `time_adjusted_at`을 기록한다. `source_fingerprint`와 `source_row`는 변경하지 않으므로 시간 보정 후에도 같은 원본 파일 재import는 멱등하게 `DUPLICATE`로 처리된다.

계좌 확장은 원본 보존과 분석용 매핑을 분리한다. `asset`은 export 원본 문자열로 유지하고, 사용자가 Accounts 탭에서 만든 `finance_accounts`와 일치하는 name/alias가 있을 때만 `account_id`를 채운다. 지원 계좌 타입은 `BANK_ACCOUNT`, `MOBILE_PAYMENT`, `SAVINGS_GOAL`, `DEBT`, `INTERNAL`, `OTHER`이며, 역할은 `SALARY`, `LIVING`, `SUBSCRIPTION`, `SINKING_FUND`, `DEBT_REPAYMENT`, `PAYMENT_METHOD`, `OTHER`이다.

`이체지출` 거래는 단일 원본 row로 저장하되, 계좌 흐름 계산 시 `asset`을 출금 계좌, `category`(`분류`)를 입금처 계좌 alias로 해석한다. 따라서 소비분석에는 포함하지 않고 계좌별 현금흐름에는 양방향으로 반영한다. Overview의 External Cash Out은 이체를 제외한 외부 현금유출이고, Account Flow는 이체 입출금까지 포함한 은행/현금/목적자금 계좌 흐름이다. `MOBILE_PAYMENT` 또는 `PAYMENT_METHOD` 계정과 `소액결제` 거래는 Account Flow 대신 Liability Flow에서 used/settled/net liability로 표시한다.

`finance_accounts.opening_balance`는 기록 시작 전부터 존재하던 계좌 잔액 seed이다. 거래 row로 만들지 않고 `opening_balance_date`, `opening_balance_memo`와 함께 계좌 상태값으로 보존한다. cycle별 Opening/Closing은 스키마에 저장하지 않고 계산한다.

- `periodOpeningBalance` = `opening_balance` + 선택 cycle 시작 이전 거래 net
- Closing(`estimatedBalance`) = `periodOpeningBalance + cycleNetFlow`

따라서 급여 사이클을 바꿔도 이전 cycle 누적 잔액이 Opening에 반영된다.
