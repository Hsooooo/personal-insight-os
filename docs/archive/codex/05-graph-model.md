# Neo4j 그래프 모델

## 문서 목적

Personal Insight OS의 개인 지식 그래프 모델을 정의합니다. Neo4j는 원천 데이터를 그대로 저장하는 곳이 아니라, 도메인 객체 간 의미 관계를 탐색하기 위한 저장소입니다.

## 노드 타입

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

## 노드별 책임

```text
Person
- 사용자 개인 그래프의 루트 노드

Date
- Activity, Sleep, HealthMetric을 날짜 기준으로 연결하는 허브

Activity
- Garmin 운동 기록의 의미 단위

Sleep
- 수면 세션 또는 수면 일자 요약

HealthMetric
- 안정시 심박, HRV, 스트레스, Body Battery 같은 일 단위 지표

Goal
- 사용자의 명시적 목표

Insight
- 시스템 또는 LLM이 생성한 해석 결과

Question
- 사용자가 Ask My Data에서 던진 질문

DataSource
- Garmin 등 외부 데이터 제공자
```

## 관계 타입

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

## 관계 속성

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

## 중요한 설계 원칙

초기에는 `AFFECTS`, `CAUSES` 같은 강한 표현을 피하고, `POSSIBLY_AFFECTS`, `CORRELATES_WITH`, `RELATED_TO` 같은 신중한 관계명을 사용합니다.

의료적·생리학적 인과관계를 단정하지 않고, 개인 데이터 기반의 패턴 후보로 표현합니다.

## 그래프 투영 기준

```text
초 단위 스트림 데이터는 노드화하지 않는다.
일 단위 요약, 운동 단위, 수면 단위, 인사이트 단위를 우선 노드화한다.
관계는 설명 가능해야 하며 source와 reason을 남긴다.
PostgreSQL row와 Neo4j node의 매핑은 graph_node_mappings로 추적한다.
사용자 피드백으로 숨긴 관계나 낮은 신뢰도 관계는 조회 필터에서 제외할 수 있어야 한다.
```

## MVP에서 우선 생성할 그래프

```text
Person -> Activity
Person -> Sleep
Person -> HealthMetric
Activity -> Date
Sleep -> Date
HealthMetric -> Date
DataSource -> Activity/Sleep/HealthMetric
Insight -> Activity/Sleep/HealthMetric
Question -> Insight
```

## 후속 확장 후보

```text
Goal -> Activity
Goal -> HealthMetric
Sleep -> Activity
Activity -> Activity
Date -> Date
Note -> Topic
Note -> Activity/Sleep/HealthMetric
```

