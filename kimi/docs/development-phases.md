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

---

## Phase 2. Garmin 동기화

```text
Garmin 연결 정보 저장
운등 데이터 수집
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

---

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

---

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

---

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

---

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

## Phase 요약

```
Phase 1: 도메인 모델 + DB 스키마
Phase 2: Garmin 동기화
Phase 3: 그래프 변환 (PostgreSQL → Neo4j)
Phase 4: RAG 질의 (Ask My Data)
Phase 5: 인사이트 저장 + 피드백
Phase 6: 목표 관리
```
