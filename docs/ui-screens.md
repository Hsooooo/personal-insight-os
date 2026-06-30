# 🖥️ 화면 스펙

## 화면-API 매핑

```mermaid
flowchart LR
    subgraph 로그인["🔐 로그인"]
        L1[Login Page]
        L1 -->|POST /api/auth/login| A1[Auth API]
        L1 -->|POST /api/auth/register| A1
    end

    subgraph 대시보드["📊 Dashboard"]
        D1[Home Dashboard]
        D1 -->|GET /api/dashboard/summary| B1[Dashboard API]
    end

    subgraph 데이터소스["📡 Data Sources"]
        DS1[Data Sources Page]
        DS1 -->|GET /api/data-sources| C1
        DS1 -->|POST /garmin/connect| C1
        DS1 -->|POST /garmin/sync| C1
        DS1 -->|GET /garmin/sync-logs| C1
    end

    subgraph 활동["🏃 Activities"]
        AC1[Activities Page]
        AC1 -->|GET /api/activities| D1
    end

    subgraph 건강["❤️ Health"]
        H1[Health Timeline]
        H1 -->|GET /api/health/metrics| E1
        H1 -->|GET /api/health/sleep| E2
    end

    subgraph 그래프["🕸️ Graph"]
        G1[Personal Graph]
        G1 -->|GET /api/graph| F1
    end

    subgraph 질의["💬 Ask"]
        Q1[Ask My Data]
        Q1 -->|POST /api/ask| G1
    end

    subgraph 인사이트["💡 Insights"]
        I1[Insights Page]
        I1 -->|GET /api/insights| H1
        I1 -->|POST /{id}/save| H1
        I1 -->|POST /{id}/feedback| H1
    end

    subgraph 목표["🎯 Goals"]
        GO1[Goals Page]
        GO1 -->|GET /api/goals| I1
        GO1 -->|POST /api/goals| I1
        GO1 -->|DELETE /{id}| I1
    end

    subgraph 설정["⚙️ Settings"]
        S1[Settings Page]
        S1 -->|GET /api/settings/llm-providers| J1
        S1 -->|POST /api/settings/llm-providers| J1
    end
```

---

## 화면별 상세

### 1. Login (`/login`)

| 항목 | 내용 |
|------|------|
| **목적** | 사용자 인증 |
| **구성** | 이메일 입력, 비밀번호 입력, 로그인/회원가입 토글 |
| **주요 상태** | `isLogin` (boolean) |
| **API 호출** | `POST /api/auth/login` 또는 `POST /api/auth/register` |
| **성공 후** | JWT 저장 → Zustand `setAuth()` → `/` 리다이렉트 |

```
┌─────────────────────────────────────┐
│      🔗 Personal Insight OS         │
│                                     │
│  ┌─────────────────────────────┐    │
│  │ Welcome back                │    │
│  │ Sign in to your account     │    │
│  │                             │    │
│  │ Email:    [user@email.com]  │    │
│  │ Password: [••••••••]        │    │
│  │                             │    │
│  │      [      Sign In      ]  │    │
│  │                             │    │
│  │ Don't have an account?      │    │
│  │ Sign up                     │    │
│  └─────────────────────────────┘    │
└─────────────────────────────────────┘
```

---

### 2. Dashboard (`/`)

| 항목 | 내용 |
|------|------|
| **목적** | 오늘의 몸 상태, 최근 흐름, 인사이트 요약 |
| **구성** | 4개 요약 카드 + 7일 트렌드 차트 + 인사이트 + 빠른 질문 + 주간 회고 복사 버튼 |
| **차트** | Recharts AreaChart (RHR + Stress) |
| **주간 회고 복사** | "Copy Weekly Report" 버튼 → 최근 7일 건강+수면+활동을 마크다운 테이블로 클립보드 복사 |
| **API 호출** | `GET /api/dashboard/summary`, `GET /api/health/sleep?start=&end=`, `GET /api/activities?startTimeFrom=&startTimeTo=` |

```
┌─────────────────────────────────────────────────────────────────┐
│ Dashboard                                    👤 User    🚪      │
│ Overview of your health and activity data                       │
├─────────────────────────────────────────────────────────────────┤
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐       │
│  │ RHR      │  │ Sleep    │  │ Activity │  │ Total    │       │
│  │ 58 bpm   │  │ 78       │  │ 8.2 km   │  │ 42       │       │
│  │ Latest   │  │ Last ngt │  │ RUNNING  │  │ All time │       │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘       │
│                                                                 │
│  ┌──────────────────────────────────┐  ┌────────────────────┐ │
│  │ 7-Day Trends                     │  │ Recent Insights    │ │
│  │                                  │  │ • 최근 컨디션 저하..│ │
│  │    📈 AreaChart                  │  │ • 수면 부족으로...│ │
│  │    (RHR + Stress)                │  │ • 운동 강도 누적..│ │
│  │                                  │  ├────────────────────┤ │
│  │                                  │  │ Quick Questions    │ │
│  └──────────────────────────────────┘  │ • 최근 컨디션이...│ │
│                                         │ • 이번 주 훈련...│ │
│                                         └────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
```

---

### 3. Data Sources (`/data-sources`)

| 항목 | 내용 |
|------|------|
| **목적** | Garmin 등 데이터 소스 연결/동기화 관리 |
| **구성** | Garmin 연결 카드 + 동기화 범위 선택 + 동기화 버튼 + Sync History + Coming Soon 목록 |
| **API 호출** | `GET/POST/DELETE /api/data-sources/*`, `GET /api/data-sources/garmin/sync-logs` |
| **동기화 흐름** | 범위 선택 → 버튼 클릭 → Rate limit 체크 → `sync_logs` RUNNING → Python 스크립트 실행 → 데이터 UPSERT → COMPLETED → Neo4j 투영 |
| **동기화 범위** | Incremental (last 7 days) / Full - 3mo / 6mo / 1yr / All |
| **Rate Limit** | 30초 쿨다운 (버튼에 타이머 표시) |
| **Smart Polling** | 탭 visible 상태에서 sync-logs 5분마다 폴리, RUNNING 상태일 때만 활성화 |
| **Mock 데이터** | 개발/테스트용 "Generate Mock Data" 버튼 별도 제공 |

---

### 4. Activities (`/activities`)

| 항목 | 내용 |
|------|------|
| **목적** | 운동 기록 목록 조회, 필터, 페이징, 태그 관리, 수동 웨이트 입력 |
| **구성** | 필터 바 + 테이블 + 페이징 컨트롤 + 웨이트 입력 폼 |
| **테이블 컬럼** | 날짜 / 이름 / 유형 / 태그 / 거리 / 시간 / 심박 / 칼로리 |
| **필터** | 타입(러닝/사이클/수영/웨이트/기타), 태그(태그 없음 포함), 이름 검색, 기간(시작일~종료일), 거리 범위(m), 정렬 |
| **페이징** | 이전/다음 버튼 + 현재/총 페이지 + 총 건수 |
| **태그 편집** | 태그 셀 클릭 → 프리셋 선택(5K/10K/하프/풀) 또는 직접 입력 / 제거 |
| **수동 웨이트 입력** | "+ 웨이트 기록" 버튼 → 운�� 이름/시간/부위/종목/세트/반복/무게 입력 → JSONB 저장 |
| **수동 웨이트 수정/삭제** | MANUAL 타입 행 클릭 → 폼 열림 → 수정 또는 삭제 가능 |
| **러닝 상세 / 스플릿 복사** | 러닝 타입 행 클릭 → Dialog 모달 열림. 활동 요약(6개 카드: 거리/시간/페이스/평균심박/최대심박/칼로리) + Lap 테이블(구간/거리/시간/페이스/심박). 하단 "Copy as Text" 버튼으로 클립보드 복사 |
| **거리 필터 호환** | 거리 범위 필터 적용 시 웨이트 항목(distance=null) 자동 제외 |
| **API 호출** | `GET /api/activities`, `POST /api/activities/weight`, `PATCH /api/activities/{id}`, `DELETE /api/activities/{id}`, `PATCH /api/activities/{id}/tag`, `GET /api/activities/{id}/laps` |

```
┌─────────────────────────────────────────────────────────────────┐
│ Activities                                     👤 User    🚪      │
│ Your workout and activity history                               │
├─────────────────────────────────────────────────────────────────┤
│  ┌─ Filter Bar ─────────────────────────────────────────────┐  │
│  │ [타입▼] [태그▼] [정렬▼] [🔍 이름검색] [적용] [초기화]     │  │
│  │ 기간: [2026-01-01] ~ [2026-05-01]  거리: [5000] ~ [max]  │  │
│  └───────────────────────────────────────────────────────────┘  │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │ Date       Name      Type   Tag    Distance  Duration ... │  │
│  │ 2026-04-28 Morning   RUNNING 5K/레 5.01 km   28:15  ...  │  │
│  │ 2026-04-27 Evening   RUNNING [+태그] 8.20 km   45:30  ...  │  │
│  │ ...                                                       │  │
│  └───────────────────────────────────────────────────────────┘  │
│  [이전]          1 / 5 페이지 (총 87건)          [다음]          │
└─────────────────────────────────────────────────────────────────┘
```

---

### 5. Health Timeline (`/health`)

| 항목 | 내용 |
|------|------|
| **목적** | 건강 지표 시간 흐름 시각화 |
| **구성** | 4개 차트 (RHR Line / Sleep Stacked Bar / Sleep Score Line / Steps Bar) |
| **차트** | Recharts LineChart + BarChart |
| **API 호출** | `GET /api/health/metrics?start=&end=`, `GET /api/health/sleep?start=&end=` |

---

### 6. Personal Graph (`/graph`)

| 항목 | 내용 |
|------|------|
| **목적** | 개인 데이터의 그래프 관계 시각화 |
| **구성** | WebGL 캔버스 + 범례/필터 사이드바 + 노드/엣지 상세 패널 |
| **라이브러리** | `cytoscape` + `cytoscape-fcose` |
| **레이아웃** | fcose (fast compound spring embedder) — 자연스러운 클러스터링 |
| **노드 종류** | Person(ellipse), Activity(round-rectangle), Sleep(ellipse), HealthMetric(diamond), Race(hexagon) |
| **관계 종류** | PERFORMED, HAS_SLEEP, HAS_METRIC, TAGGED_AS (Activity→Race) — 타입별 색상/선 스타일 |
| **필터링** | 노드 타입 ON/OFF 체크박스, 관계 타입 ON/OFF 체크박스 |
| **상호작용** | 노드 클릭 → 연결된 관계 하이라이트 (나머지 dimmed), 호버 → 툴팁, 빈 공간 클릭 → 선택 해제 |
| **API 호출** | `GET /api/graph` |

```
┌─────────────────────────────────────────────────────────────────┐
│ Personal Graph                                 👤 User    🚪      │
│ Explore connections in your health data                         │
├─────────────────────────────────────────────────────────────────┤
│  ┌────────────────────────────────────┐  ┌──────────────┐       │
│  │                                    │  │ Filters      │       │
│  │   🔵 Person        🏃 Activity     │  │ ● Person     │       │
│  │      │                 │           │  │ ● Activity   │       │
│  │      ├── PERFORMED ───┤           │  │ ● Sleep      │       │
│  │      │        ├── TAGGED_AS → 🟡 Race│  │ ● HealthMetric│      │
│  │      │                 │           │  │ ● Race       │       │
│  │      │                 │           │  │ ● HealthMetric│      │
│  │      ├── HAS_SLEEP ── 😴 Sleep    │  └──────────────┘       │
│  │      │                             │  ┌──────────────┐       │
│  │      └── HAS_METRIC ─ ❤️ Metric   │  │ Node Detail  │       │
│  │                                    │  │ Label: Morning│      │
│  │    [React Flow Canvas]             │  │ Type: Activity│      │
│  │                                    │  │ ID: 123      │       │
│  └────────────────────────────────────┘  └──────────────┘       │
└─────────────────────────────────────────────────────────────────┘
```

---

### 7. Ask My Data (`/ask`)

| 항목 | 내용 |
|------|------|
| **목적** | 자연어로 데이터에 질문하고 개인 기준선 비교 기반 답변 받기 |
| **구성** | 샘플 질문 버튼 + 채팅 UI + 분석 기간/기준선 + 신뢰도 + 근거 카드 + 피드백 |
| **API 호출** | `POST /api/ask` |
| **특징** | Evidence-first RAG v2, 기간/의도 판별, 서버 산정 신뢰도, 근거별 라우팅 링크, 저장/피드백 토스트, 질문 실패 시 재시도 |

```
┌─────────────────────────────────────────────────────────────────┐
│ Ask My Data                                    👤 User    🚪      │
│ Ask natural language questions about your health data           │
├─────────────────────────────────────────────────────────────────┤
│  ┌───────────────────────────────────────────────────────────┐  │
│  │ 💡 최근 컨디션이 떨어진 이유가 뭐야?                      │  │
│  │ 💡 러닝 기록이 좋았던 날들의 공통점은 뭐야?               │  │
│  │ 💡 수면이 부족하면 내 운동 성과가 얼마나 떨어져?         │  │
│  │ 💡 이번 주 훈련 강도는 적절했어?                          │  │
│  └───────────────────────────────────────────────────────────┘  │
│                                                                 │
│  ┌─────────────────────────────────────┐                        │
│  │ 사용자: 최근 컨디션이 떨어진 이유가   │  ← user msg (indigo)   │
│  │ 뭐야?                               │                        │
│  └─────────────────────────────────────┘                        │
│                                                                 │
│  ┌─────────────────────────────────────┐                        │
│  │ 📅 분석: 2026-06-14 ~ 2026-06-20   │  ← system msg          │
│  │ 📊 기준선: 2026-05-17 ~ 2026-06-13 │                        │
│  │                                     │                        │
│  │ 시스템: 최근 HRV 저하와 수면 시간  │                        │
│  │ 감소가 함께 관찰됩니다.            │                        │
│  │                                     │                        │
│  │ 🎯 신뢰도 HIGH (82%)               │                        │
│  │ • 분석 기간 데이터 7일 확보        │                        │
│  │ • 28일 기준선 비교 가능            │                        │
│  │ • 질문과 관련된 지표 확보          │                        │
│  │                                     │                        │
│  │ 📎 근거                             │                        │
│  │ ┌─────────────────────────────┐    │                        │
│  │ │ 평균 HRV                    │🔗 │                        │
│  │ │ 최근 7일 평균 42ms          │    │                        │
│  │ │ 기준선보다 12% 낮음         │    │                        │
│  │ │ 현재 42ms  /  기준선 48ms   │    │                        │
│  │ └─────────────────────────────┘    │                        │
│  │                                     │                        │
│  │ [💾 👍 ❓ 👎]                      │                        │
│  │                                     │                        │
│  │ 💡 추가 질문                        │                        │
│  │ • 수면과 HRV 변화를 날짜별로 비교해줘                           │
│  └─────────────────────────────────────┘                        │
│                                                                 │
│  [Ask about your health data...                ] [➤]           │
└─────────────────────────────────────────────────────────────────┘
```

---

### 8. Insights (`/insights`)

| 항목 | 내용 |
|------|------|
| **목적** | AI 생성 인사이트 열람/저장/피드백 |
| **구성** | 카테고리 필터 + 피드백 필터 + 인사이트 카드 목록. 각 카드는 `evidenceData`가 있을 경우 현재값/기준값/변화율/라우트 링크를 함께 표시 |
| **API 호출** | `GET /api/insights`, `POST /{id}/save`, `POST /{id}/feedback` |

---

### 9. Goals (`/goals`)

| 항목 | 내용 |
|------|------|
| **목적** | 개인 목표 설정 및 관리 |
| **구성** | 목표 카드 그리드 + 등록 폼 |
| **API 호출** | `GET/POST/PATCH/DELETE /api/goals` |

---

### 10. Settings (`/settings`)

| 항목 | 내용 |
|------|------|
| **목적** | LLM Provider 설정 및 앱 정보 |
| **구성** | Provider 목록 + 등록 폼 + About 카드 |
| **API 호출** | `GET/POST/PATCH/DELETE /api/settings/llm-providers` |
| **지원 Provider** | OpenAI, Anthropic, Google Gemini |

---

### 11. Finance (`/finance`)

| 항목 | 내용 |
|------|------|
| **목적** | 월급일 기준 지출 cycle, 현금흐름, 소비분석, 반복 청구 템플릿 관리 |
| **구성** | Overview, Transactions, Accounts, Import, Recurring 탭 |
| **Overview** | 선택 cycle과 optional week 기준의 수입, 이체 제외 External Cash Out, Deferred Spending, Actual Spending, Net External Cashflow, 전체 카테고리별 지출과 원형 Category Mix, 은행/현금/목적자금 Account Flow, 소액결제/후불 Liability Flow 요약. Flow 영역은 긴 금액/계좌명을 위해 별도 row와 고정 컬럼으로 표시 |
| **Transactions** | 선택 cycle/week 범위 안의 거래 목록, 검색/흐름/계좌/카테고리/플래그/기간 필터, 날짜 고정 + 시:분 후보정, 행 선택 후 선택 거래 즉시 삭제, Account 컬럼, Cash/Spend/소액결제/Adjusted 플래그 표시. 원본 금액과 소비분석 금액이 다르면 Spend 금액을 별도 표시 |
| **재무 요약 복사** | "Copy Cycle Summary" 또는 "Copy Weekly Summary" 버튼 → 선택 cycle 또는 선택 week의 재무 요약을 마크다운으로 클립보드 복사. Totals, 전체 Spending Categories, Account Flow, Liability Flow를 포함하며, week는 월요일~일요일 기준이되 cycle 시작/끝 주는 cycle 범위로 잘라 표시 |
| **Accounts** | 선택 cycle/week 기준 계좌 흐름, 계좌/지갑/부채/목적자금 카드, alias 관리, opening balance 보정, unmapped asset 생성/기존 계좌 매핑 |
| **Import** | `.xlsx` 업로드 → preview → 신규/중복/확인필요 상태 확인 → 확정 저장 |
| **Recurring** | KT 통신비 같은 고정비 템플릿과 cycle 적용 버전 관리 |
| **API 호출** | `/api/finance/*` |
