# 📡 API 명세

> 모든 API는 `ApiResponse<T>` 래퍼로 응답합니다.

```json
{
  "success": true,
  "message": null,
  "data": { ... }
}
```

**인증**: `Authorization: Bearer <jwt_token>`

---

## API 엔드포인트 맵

```mermaid
flowchart LR
    subgraph 인증["🔐 인증 (Public)"]
        A1["POST /api/auth/register"]
        A2["POST /api/auth/login"]
    end

    subgraph 대시보드["📊 대시보드"]
        B1["GET /api/dashboard/summary"]
    end

    subgraph 데이터소스["📡 데이터 소스"]
        C1["GET /api/data-sources"]
        C2["POST /api/data-sources/garmin/connect"]
        C3["POST /api/data-sources/garmin/sync"]
        C4["GET /api/data-sources/garmin/sync-logs"]
        C5["POST /api/data-sources/garmin/mock"]
        C6["DELETE /api/data-sources/garmin"]
    end

    subgraph 활동["🏃 활동"]
        D1["GET /api/activities"]
        D2["GET /api/activities/{id}"]
        D3["PATCH /api/activities/{id}/tag"]
        D4["POST /api/activities/weight"]
        D5["PATCH /api/activities/{id}"]
        D6["DELETE /api/activities/{id}"]
    end

    subgraph 건강["❤️ 건강"]
        E1["GET /api/health/metrics"]
        E2["GET /api/health/sleep"]
    end

    subgraph 그래프["🕸️ 그래프"]
        F1["GET /api/graph"]
    end

    subgraph 질의["💬 질의"]
        G1["POST /api/ask"]
    end

    subgraph 인사이트["💡 인사이트"]
        H1["GET /api/insights"]
        H2["GET /api/insights/saved"]
        H3["POST /api/insights/{id}/save"]
        H4["POST /api/insights/{id}/feedback"]
        H5["DELETE /api/insights/{id}"]
    end

    subgraph 목표["🎯 목표"]
        I1["GET /api/goals"]
        I2["POST /api/goals"]
        I3["GET /api/goals/{id}"]
        I4["PATCH /api/goals/{id}"]
        I5["DELETE /api/goals/{id}"]
    end

    subgraph 설정["⚙️ 설정"]
        J1["GET /api/settings/llm-providers"]
        J2["POST /api/settings/llm-providers"]
        J3["PATCH /api/settings/llm-providers/{id}"]
        J4["DELETE /api/settings/llm-providers/{id}"]
    end

    style 인증 fill:#f43f5e,color:#fff
    style 대시보드 fill:#6366f1,color:#fff
    style 데이터소스 fill:#10b981,color:#fff
    style 인사이트 fill:#f59e0b,color:#fff
```

---

## 상세 명세

### 🔐 Auth API

| 메서드 | 엔드포인트 | 설명 | 인증 |
|--------|-----------|------|------|
| POST | `/api/auth/register` | 회원가입 | ❌ |
| POST | `/api/auth/login` | 로그인 | ❌ |
| POST | `/api/auth/refresh` | Access Token 갱신 (Refresh Token 쿠키 기반) | ❌ |
| POST | `/api/auth/logout` | 로그아웃 (서버측 Refresh Token 폐기) | ✅ |
| GET | `/api/auth/me` | 내 정보 조회 | ✅ |
| GET | `/api/auth/api-keys` | API 키 목록 조회 | ✅ |
| POST | `/api/auth/api-keys` | API 키 발급 | ✅ |
| DELETE | `/api/auth/api-keys/{id}` | API 키 삭제 | ✅ |

**로그인 요청**
```json
POST /api/auth/login
{
  "email": "user@example.com",
  "password": "password123"
}
```

**로그인 응답**
```json
{
  "success": true,
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9...",
    "user": {
      "id": 1,
      "email": "user@example.com",
      "displayName": "User"
    }
  }
}
```
> **Set-Cookie**: `refresh_token=<jwt_refresh_token>; HttpOnly; Secure; SameSite=Strict; Max-Age=604800`
>
> Refresh Token은 응답 body가 아닌 **HttpOnly Cookie**로 전달된다.

**Refresh 요청**
```
POST /api/auth/refresh
Cookie: refresh_token=...
```
> `credentials: include`로 호출 시 쿠키가 자동 전송된다.

**Refresh 응답**
```json
{
  "success": true,
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9...",
    "user": { ... }
  }
}
```
> **Set-Cookie**: 새로운 `refresh_token` 쿠키 (Rotation 적용 — 기존 토큰 폐지 후 재발급)

**Logout 요청**
```
POST /api/auth/logout
Authorization: Bearer <access_token>
Cookie: refresh_token=...
```

**Logout 응답**
```json
{
  "success": true,
  "data": null
}
```
> **Set-Cookie**: `refresh_token=; Max-Age=0` (쿠키 만료)

**API 키 발급 요청**
```json
POST /api/auth/api-keys
{
  "name": "내 노트북 Kimi"
}
```

**API 키 발급 응답** (생성 시에만 `key` 필드 포함)
```json
{
  "success": true,
  "data": {
    "id": 1,
    "name": "내 노트북 Kimi",
    "key": "pios_xxxxxxxxxxxxxxxx",
    "createdAt": "2026-05-13T10:00:00"
  }
}
```

---

### 📊 Dashboard API

| 메서드 | 엔드포인트 | 설명 |
|--------|-----------|------|
| GET | `/api/dashboard/summary` | 대시보드 요약 데이터 |

**응답 예시**
```json
{
  "latestHealth": { "restingHeartRate": 58, "stressAvg": 32.5, "weightKg": 72.5, ... },
  "latestSleep": { "sleepScore": 78, "totalSleepSeconds": 25200, ... },
  "latestActivity": { "activityType": "RUNNING", "distanceMeters": 8200, ... },
  "totalActivities": 42,
  "last7DaysHealth": [...],
  "last7DaysActivities": [...],
  "recentInsights": [...],
  "suggestedQuestions": [
    "최근 컨디션이 안 좋은 이유는?",
    "이번 주 훈련 강도는 적절해?",
    "러닝 기록이 좋았던 날들의 공통점은?"
  ]
}
```

---

### 📡 Data Sources API

| 메서드 | 엔드포인트 | 설명 |
|--------|-----------|------|
| GET | `/api/data-sources` | 연결된 데이터 소스 목록 |
| POST | `/api/data-sources/garmin/connect` | Garmin 계정 연결 |
| POST | `/api/data-sources/garmin/sync` | Garmin 데이터 동기화 — 활동, 건강지표, 수면, **체중** 포함 (body: syncType, dateFrom, dateTo) |
| GET | `/api/data-sources/garmin/sync-logs` | 동기화 이력 조회 |
| POST | `/api/data-sources/garmin/mock` | Mock 데이터 생성 |
| DELETE | `/api/data-sources/garmin` | Garmin 연결 해제 |

---

### 🏃 Activities API

| 메서드 | 엔드포인트 | 설명 |
|--------|-----------|------|
| GET | `/api/activities?page=&size=&activityType=&userTag=&activityName=&startTimeFrom=&startTimeTo=&minDistance=&maxDistance=&sortBy=&sortDir=` | 활동 목록 (페이징 + 필터 + 정렬) |
| GET | `/api/activities/{id}` | 활동 상세 |
| GET | `/api/activities/{id}/laps` | 활동 구간(lap) 목록 |
| POST | `/api/activities/weight` | 웨이트 트레이닝 수동 등록 |
| PATCH | `/api/activities/{id}` | 수동 웨이트 트레이닝 수정 (MANUAL만) |
| DELETE | `/api/activities/{id}` | 수동 웨이트 트레이닝 삭제 (MANUAL만) |
| PATCH | `/api/activities/{id}/tag` | 태그 수정 |

**필터 파라미터**

| 파라미터 | 타입 | 설명 |
|----------|------|------|
| `page` | int | 페이지 번호 (0-based), 기본값 0 |
| `size` | int | 페이지 크기, 기본값 20 |
| `activityType` | string | 활동 타입 exact match (예: `running`) |
| `userTag` | string | 사용자 태그 exact match. 빈 문자열(`""`) = 태그 없음 조건 |
| `activityName` | string | 활동 이름 부분 검색 (case-insensitive LIKE) |
| `startTimeFrom` | date | 시작일 (`YYYY-MM-DD`) 이후 |
| `startTimeTo` | date | 종료일 (`YYYY-MM-DD`) 이전 |
| `minDistance` | decimal | 최소 거리 (m). 적용 시 거리가 null인 항목(웨이트)은 자동 제외 |
| `maxDistance` | decimal | 최대 거리 (m). 적용 시 거리가 null인 항목(웨이트)은 자동 제외 |
| `sortBy` | string | 정렬 기준: `startTime` \| `distance` \| `duration` \| `calories` |
| `sortDir` | string | 정렬 방향: `asc` \| `desc` |

**웨이트 트레이닝 등록 요청**
```json
POST /api/activities/weight
{
  "activityName": "가슴 운��",
  "startTime": "2026-05-02T19:00:00",
  "durationSeconds": 3600,
  "averageHeartRate": 125,
  "calories": 350,
  "bodyPart": "CHEST",
  "exercises": [
    {
      "name": "Bench Press",
      "sets": [
        { "reps": 10, "weightKg": 60, "durationSeconds": 45 },
        { "reps": 8, "weightKg": 70, "durationSeconds": 60 }
      ]
    }
  ]
}
```

**태그 수정 요청**
```json
PATCH /api/activities/123/tag
{
  "userTag": "5K / 레이스"
}
```

---

### 💬 Ask API (RAG)

| 메서드 | 엔드포인트 | 설명 |
|--------|-----------|------|
| POST | `/api/ask` | 자연어 질의 |

**요청**
```json
{
  "question": "최근 컨디션이 떨어진 이유가 뭐야?"
}
```

**응답**
```json
{
  "questionId": 1,
  "insightId": 5,
  "conclusion": "최근 컨디션 저하는 수면 시간 자첳보다 운등 강도 누적과 회복 지표 저하와 더 관련 있어 보입니다.",
  "evidenceSummary": [
    "최근 7일 데이터 기준",
    "건강 지표 7일",
    "수면 기록 7일",
    "활동 기록 3개",
    "관련 태그 활동 2개 (5K / 레이스)"
  ],
  "confidence": "중간",
  "followUpQuestion": "최근 업무 스트레스나 주관적 피로 기록이 있다면 함께 분석할 수 있습니다."
}
```

**특수 질문 — 운등 요약**

키워드: "이번주 운등", "운등 정리", "훈련 일지", "weekly summary" 등

- Garmin 활동은 랩(lap) 단위로, 수동 웨이트 트레이닝은 종목/세트 단위로 표 형태로 정리
- 마지막에 전체 주간 요약(총 활동 횟수, 총 거리, 총 볼륨 등) 추가

---

### 🕸️ Graph API

| 메서드 | 엔드포인트 | 설명 |
|--------|-----------|------|
| GET | `/api/graph?days=&view=&raceCategory=` | 개인 지식 그래프 데이터 |

### 🛠️ Admin API

| 메서드 | 엔드포인트 | 설명 |
|--------|-----------|------|
| POST | `/api/admin/backfill` | 그래프 투영 재실행 |
| POST | `/api/admin/backfill` | 그래프 투영 재실록 (관리자용) |

**응답**
```json
{
  "nodes": [
    { "id": "Person_1", "type": "Person", "label": "Me", "properties": {} },
    { "id": "123", "type": "Activity", "label": "Morning Run", "properties": {"type": "RUNNING"} },
    { "id": "456", "type": "Sleep", "label": "2026-04-28", "properties": {"score": 78} }
  ],
  "relationships": [
    { "id": "r_1", "type": "PERFORMED", "sourceId": "Person_1", "targetId": "123" },
    { "id": "r_2", "type": "HAS_SLEEP", "sourceId": "Person_1", "targetId": "456" }
  ]
}
```

---

### 💡 Insights API

| 메서드 | 엔드포인트 | 설명 |
|--------|-----------|------|
| GET | `/api/insights?category=&feedbackStatus=` | 인사이트 목록 (필터 가능) |
| GET | `/api/insights/saved` | 저장된 인사이트 |
| GET | `/api/insights/{id}` | 인사이트 상세 |
| POST | `/api/insights/{id}/save` | 인사이트 저장 |
| POST | `/api/insights/{id}/feedback` | 피드백 등록 |
| DELETE | `/api/insights/{id}` | 인사이트 삭제 |

**피드백 요청**
```json
{
  "feedbackStatus": "CORRECT"
}
```

피드백 상태: `CORRECT`, `UNCLEAR`, `WRONG`, `IMPORTANT`
