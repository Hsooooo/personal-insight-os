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

> 초기에는 `AFFECTS`, `CAUSES` 같은 강한 표현을 피하고, `POSSIBLY_AFFECTS`, `CORRELATES_WITH`, `RELATED_TO` 같은 신중한 관계명을 사용합니다.
>
> 의료적·생리학적 인과관계를 단정하지 않고, 개인 데이터 기반의 패턴 후보로 표현합니다.

---

## 8.5 그래프 구조 예시

```
                      ┌──────────┐
                      │  Person  │
                      └────┬─────┘
                           │
           ┌───────────────┼───────────────┐
           │               │               │
           ▼               ▼               ▼
      ┌─────────┐    ┌─────────┐    ┌─────────────┐
      │ Activity│    │  Sleep  │    │ HealthMetric│
      └────┬────┘    └────┬────┘    └──────┬──────┘
           │               │               │
           └───────────────┼───────────────┘
                           │
                           ▼
                      ┌──────────┐
                      │   Date   │
                      └──────────┘
```
