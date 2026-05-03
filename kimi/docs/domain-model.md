# 6. 도메인 모델

## 6.1 핵심 도메인

```text
User
- 서비스 사용자

ProviderConnection
- Garmin, Obsidian, Notion 등 외부 데이터 연결 정보

Activity
- 운등 기록

ActivityLap
- 운등 구간 기록

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

---

## 6.2 도메인 간 관계 개요

```
┌─────────┐     ┌─────────────────┐     ┌───────────┐
│  User   │────▶│ ProviderConnection│───▶│ DataSource│
└────┬────┘     └─────────────────┘     └─────┬─────┘
     │                                         │
     │    ┌─────────┐    ┌──────────┐         │
     ├───▶│ Activity│    │ ActivityLap       │
     │    └────┬────┘    └──────────┘         │
     │         │                               │
     │    ┌────┴────┐    ┌──────────┐         │
     ├───▶│  Sleep  │    │HealthMetric       │
     │    └────┬────┘    └────┬─────┘         │
     │         │              │                │
     │    ┌────┴────┐    ┌────┴────┐          │
     ├───▶│  Goal   │    │ Insight │◀─────────┘
     │    └─────────┘    └────┬────┘
     │                        │
     │                   ┌────┴────┐
     └──────────────────▶│Question │
                        └─────────┘
```
