# 11. 데이터 파이프라인

## 11.1 Garmin 동기화 흐름

```text
Garmin Sync Request
↓
garminconnect >= 0.3.0 호출
↓
울동/수면/건강 데이터 수집
↓
Raw JSON 저장
↓
정규화 테이블 저장
↓
Domain Normalizer 실행
↓
Graph Projector 실행
↓
Embedding Worker 실행
↓
Insight Candidate 생성
```

## 11.2 Graph Projector 역할

```text
PostgreSQL의 정형 데이터를 읽는다.
각 row에 대응하는 Neo4j 노드를 생성한다.
Date 노드와 연결한다.
Person 노드와 연결한다.
DataSource 노드와 연결한다.
기본 관계를 생성한다.
생성된 Neo4j node id를 graph_node_mappings에 저장한다.
```

## 11.3 Insight Worker 역할

```text
최근 데이터 변화를 분석한다.
일간/주간 패턴 후보를 찾는다.
LLM에게 근거 데이터와 함께 요약을 요청한다.
생성된 인사이트를 insights에 저장한다.
근거 데이터를 insight_evidences에 저장한다.
필요한 경우 Neo4j에 Insight 노드를 생성한다.
```

---

## 11.4 데이터 저장 전략

```text
PostgreSQL: 원천 데이터 + 정형 데이터
Neo4j: 의미 있는 노드와 관계

원칙:
- Raw 데이터는 PostgreSQL에 저장
- 의미 있는 요약 단위만 Neo4j에 저장
- 초 단위 스트림은 노드화하지 않음
- 일/활동/수면/인사이트 단위 중심으로 시작
```
