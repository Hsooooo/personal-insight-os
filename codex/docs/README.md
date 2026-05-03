# Personal Insight OS 문서 인덱스

이 폴더는 `personal_insight_os_mvp_deliverables.md` 단일 기획서를 기반으로, 개발과 의사결정에 바로 사용할 수 있도록 성격별로 분리한 상세 문서 모음입니다.

## 문서 구성

| 문서 | 목적 |
| --- | --- |
| [01-product-overview.md](01-product-overview.md) | 제품 정의, 핵심 가치, MVP 성공 기준 정리 |
| [02-mvp-scope-and-scenarios.md](02-mvp-scope-and-scenarios.md) | MVP 포함/제외 범위와 주요 사용자 시나리오 |
| [03-information-architecture-and-ux.md](03-information-architecture-and-ux.md) | 사이트맵, 화면별 목적, 주요 구성과 액션 |
| [04-domain-and-data-model.md](04-domain-and-data-model.md) | 핵심 도메인, PostgreSQL 테이블 초안, 설계 원칙 |
| [05-graph-model.md](05-graph-model.md) | Neo4j 노드, 관계, 관계 속성, 그래프 설계 기준 |
| [06-rag-and-llm-design.md](06-rag-and-llm-design.md) | Ask My Data, RAG 흐름, LLM Provider 설계 |
| [07-data-pipeline.md](07-data-pipeline.md) | Garmin 동기화, Graph Projector, Insight Worker 흐름 |
| [08-api-spec-draft.md](08-api-spec-draft.md) | MVP API 초안과 엔드포인트별 책임 |
| [09-roadmap-risks-and-backlog.md](09-roadmap-risks-and-backlog.md) | 개발 Phase, 리스크, 대응 전략, 다음 작업 후보 |
| [10-ui-generation-prompts.md](10-ui-generation-prompts.md) | v0 또는 UI 생성 도구용 프롬프트 세트 |

## 읽는 순서

1. 제품 방향을 잡을 때: `01` -> `02` -> `09`
2. 화면을 설계할 때: `03` -> `10`
3. 백엔드를 설계할 때: `04` -> `05` -> `07` -> `08`
4. LLM/RAG 기능을 설계할 때: `06` -> `04` -> `05`

## 원본 문서와의 관계

원본 문서의 큰 장 구성을 유지하되, 다음을 보강했습니다.

- 각 문서가 독립적으로 읽히도록 목적과 범위를 추가했습니다.
- 구현 관점의 책임 경계와 완료 기준을 명확히 했습니다.
- 개인정보, LLM 환각, 그래프 과설계 같은 리스크를 각 설계 영역에 연결했습니다.
- API와 데이터 모델은 MVP 구현 순서에 맞게 참조하기 쉽게 재정리했습니다.

