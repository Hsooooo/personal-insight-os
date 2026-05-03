# 데이터 파이프라인

## 문서 목적

Garmin 데이터 수집부터 PostgreSQL 저장, 도메인 정규화, Neo4j 투영, 인사이트 후보 생성까지의 흐름을 정의합니다.

## Garmin 동기화 흐름

```text
Garmin Sync Request
↓
garminconnect >= 0.3.0 호출
↓
운동/수면/건강 데이터 수집
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

## 동기화 책임

```text
Provider 연결 상태 확인
인증 정보 로드와 복호화
기간 기반 데이터 수집
원본 Provider ID 기준 중복 방지
Raw JSON 저장
정규화 테이블 upsert
실패 로그 기록
last_synced_at 갱신
```

## Graph Projector 역할

```text
PostgreSQL의 정형 데이터를 읽는다.
각 row에 대응하는 Neo4j 노드를 생성한다.
Date 노드와 연결한다.
Person 노드와 연결한다.
DataSource 노드와 연결한다.
기본 관계를 생성한다.
생성된 Neo4j node id를 graph_node_mappings에 저장한다.
```

## Insight Worker 역할

```text
최근 데이터 변화를 분석한다.
일간/주간 패턴 후보를 찾는다.
LLM에게 근거 데이터와 함께 요약을 요청한다.
생성된 인사이트를 insights에 저장한다.
근거 데이터를 insight_evidences에 저장한다.
필요한 경우 Neo4j에 Insight 노드를 생성한다.
```

## 데이터 처리 단계별 산출물

| 단계 | 입력 | 출력 |
| --- | --- | --- |
| Garmin Sync | Provider 인증 정보, 기간 | Raw JSON, 정규화 row |
| Domain Normalizer | Raw JSON, 정규화 row | Activity, Sleep, HealthMetric 의미 단위 |
| Graph Projector | 정규화 row | Neo4j node, relationship, mapping |
| Embedding Worker | 인사이트/요약 텍스트 | vector index 또는 embedding row |
| Insight Worker | 최근 데이터, 그래프 관계 | Insight, Evidence, Insight node |

## 실패 처리 기준

```text
Garmin 인증 실패
- 연결 상태를 EXPIRED 또는 FAILED로 갱신한다.

일부 데이터 수집 실패
- 성공한 데이터는 저장하고 실패 범위는 로그로 남긴다.

PostgreSQL 저장 실패
- Graph Projector를 실행하지 않는다.

Neo4j 투영 실패
- PostgreSQL 원천 데이터는 유지하고 재시도 가능하게 한다.

LLM 호출 실패
- 질문과 근거 후보는 저장하되 인사이트 생성은 실패 상태로 남긴다.
```

## MVP에서 필요한 추가 테이블 후보

```text
sync_logs
- provider_connection_id
- sync_type
- status
- period_start
- period_end
- fetched_count
- error_message
- started_at
- finished_at

embedding_documents
- user_id
- source_type
- source_id
- content
- embedding_provider
- embedding_model
- created_at
```

