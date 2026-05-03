# 로드맵, 리스크, 백로그

## 문서 목적

MVP 개발 Phase, 각 Phase의 완료 기준, 주요 리스크와 대응 전략, 다음 작업 후보를 정리합니다.

## Phase 1. 도메인 모델과 저장 구조

### 범위

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

### 완료 기준

```text
PostgreSQL 스키마 생성
기본 Entity/DTO/Mapper 구성
Garmin raw 저장 가능
```

## Phase 2. Garmin 동기화

### 범위

```text
Garmin 연결 정보 저장
운동 데이터 수집
수면 데이터 수집
건강 지표 수집
중복 방지
동기화 로그 저장
```

### 완료 기준

```text
사용자 기준 Garmin 데이터를 DB에 저장할 수 있음
수동 동기화 가능
```

## Phase 3. 그래프 변환

### 범위

```text
Person 노드 생성
Date 노드 생성
Activity 노드 생성
Sleep 노드 생성
HealthMetric 노드 생성
기본 관계 생성
PostgreSQL-Neo4j 매핑 저장
```

### 완료 기준

```text
Garmin 데이터가 Neo4j 그래프로 투영됨
Personal Graph 화면에서 조회 가능
```

## Phase 4. RAG 질의

### 범위

```text
질문 입력
질문 의도 분석
PostgreSQL 조회
Neo4j 조회
LLM 답변 생성
근거 표시
```

### 완료 기준

```text
사용자가 자신의 데이터에 질문하고 근거 기반 답변을 받을 수 있음
```

## Phase 5. 인사이트 저장과 피드백

### 범위

```text
인사이트 저장
근거 저장
피드백 저장
저장된 인사이트 목록 조회
```

### 완료 기준

```text
LLM 답변이 일회성으로 사라지지 않고 개인 지식으로 누적됨
```

## Phase 6. 목표 관리

### 범위

```text
목표 등록
목표 관련 데이터 연결
목표 기준 인사이트 생성
```

### 완료 기준

```text
사용자의 목표와 실제 생활 데이터가 연결됨
```

## 리스크: Garmin 데이터 안정성

### 리스크

```text
garminconnect 라이브러리 기반 연동은 비공식 흐름일 수 있어 변경에 취약할 수 있음
```

### 대응

```text
Raw JSON 저장
동기화 실패 로그 저장
Provider Adapter 구조로 분리
FIT/TCX/GPX 파일 업로드 fallback 고려
Strava 연동 확장 후보 유지
```

## 리스크: 그래프 과설계

### 리스크

```text
모든 데이터를 노드로 만들면 그래프가 복잡해지고 의미가 흐려짐
```

### 대응

```text
Raw 데이터는 PostgreSQL에 저장
의미 있는 요약 단위만 Neo4j에 저장
초 단위 스트림은 노드화하지 않음
일/활동/수면/인사이트 단위 중심으로 시작
```

## 리스크: LLM 환각

### 리스크

```text
LLM이 실제 데이터에 없는 조언을 생성할 수 있음
```

### 대응

```text
Evidence-first RAG
근거 없는 답변 금지
답변에 신뢰도 표시
사용자 피드백 수집
의료적 단정 표현 금지
```

## 리스크: 개인정보 보안

### 리스크

```text
건강 데이터와 개인 기록은 민감 정보에 해당할 수 있음
```

### 대응

```text
API Key 암호화 저장
Provider 인증 정보 암호화
데이터 삭제 기능 제공
LLM 전송 데이터 최소화
로컬 LLM 옵션 고려
민감 데이터 마스킹 옵션 제공
```

## 확장 아이디어

### Obsidian 연동

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

### 다른 통합 후보

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

### 주간 리포트

```text
이번 주 운동 요약
이번 주 수면/회복 요약
이번 주 컨디션 변화
가장 좋았던 날
가장 무너졌던 날
반복 패턴
다음 주 제안
```

### 패턴 탐지

```text
수면 부족 다음 날 운동 성과 저하
고강도 운동 2일 후 피로 증가
스트레스 높은 날 안정시 심박 상승
휴식일 이후 페이스 개선
특정 요일마다 컨디션 저하
```

### Simulation 기능

```text
이번 주에 인터벌을 한 번 더 넣으면 회복 리스크가 커질까?
다음 달 하프마라톤을 목표로 하면 현재 패턴상 가장 큰 리스크는 무엇인가?
수면을 평균 30분 늘리면 운동 성과에 어떤 변화가 예상되는가?
```

## 다음 작업 후보

```text
1. PostgreSQL DDL 상세화
2. Neo4j Cypher 생성 스크립트
3. Spring Boot 패키지 구조 설계
4. API 명세서 상세화
5. 화면별 상세 스토리보드 확장
6. v0용 프롬프트 세트 정리
7. MVP 개발 태스크 백로그 작성
```

## 추천 순서

```text
1. PostgreSQL DDL 상세화
2. Neo4j 그래프 모델 확정
3. API 명세서 작성
4. 화면별 스토리보드 확장
5. 개발 태스크 백로그 작성
```

