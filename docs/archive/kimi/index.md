# Personal Insight OS MVP 기획 산출물 — 문서 목차

> 원본: [`personal_insight_os_mvp_deliverables.md`](../personal_insight_os_mvp_deliverables.md)

이 폰더는 Personal Insight OS의 MVP 개발을 위한 기획 산출물을 성격별로 분리하여 정리한 문서들입니다.

---

## 문서 목록

| # | 문서 | 설명 |
|---|------|------|
| 1 | [product-vision.md](product-vision.md) | 제품 정의, 핵심 가치, 제품형 설명 |
| 2 | [mvp-scope.md](mvp-scope.md) | MVP 범위, 포함/제외 기능, MVP 성공 기준 |
| 3 | [user-scenarios.md](user-scenarios.md) | 핵심 사용자 시나리오 4가지 |
| 4 | [sitemap-and-screens.md](sitemap-and-screens.md) | 사이트맵 및 화멸별 상세 스토리보드 |
| 5 | [domain-model.md](domain-model.md) | 핵심 도메인 모델 정의 |
| 6 | [database-schema.md](database-schema.md) | PostgreSQL 테이블 초안 (DDL) |
| 7 | [graph-model.md](graph-model.md) | Neo4j 그래프 모델 (노드, 관계, 속성) |
| 8 | [rag-pipeline.md](rag-pipeline.md) | RAG 파이프라인 설계 및 환각 방지 원칙 |
| 9 | [llm-provider.md](llm-provider.md) | LLM Provider 설계 및 Adapter 인터페이스 |
| 10 | [data-pipeline.md](data-pipeline.md) | 데이터 동기화 및 그래프 투영 파이프라인 |
| 11 | [api-draft.md](api-draft.md) | MVP API 초안 (엔드포인트 목록) |
| 12 | [future-roadmap.md](future-roadmap.md) | 확장 아이디어 및 후속 통합 후보 |
| 13 | [development-phases.md](development-phases.md) | 6단계 개발 Phase 제안 |
| 14 | [ui-prompts.md](ui-prompts.md) | v0 UI 생성용 프롬프트 초안 |
| 15 | [risk-management.md](risk-management.md) | 리스크 분석 및 대응 전략 |
| 16 | [conclusion-and-next-steps.md](conclusion-and-next-steps.md) | 결론 및 다음 작업 후보 |

---

## 한 눈에 보는 구조

```
┌─────────────────────────────────────────┐
│ 1. 제품 정의 (product-vision.md)         │
├─────────────────────────────────────────┤
│ 2. MVP 범위 (mvp-scope.md)               │
├─────────────────────────────────────────┤
│ 3. 사용자 시나리오 (user-scenarios.md)    │
├─────────────────────────────────────────┤
│ 4. 사이트맵 & 화면 (sitemap-and-screens) │
├─────────────────────────────────────────┤
│ 5. 도메인 모델 (domain-model.md)         │
├─────────────────────────────────────────┤
│ 6. DB 스키마 (database-schema.md)        │
├─────────────────────────────────────────┤
│ 7. 그래프 모델 (graph-model.md)          │
├─────────────────────────────────────────┤
│ 8. RAG 파이프라인 (rag-pipeline.md)      │
├─────────────────────────────────────────┤
│ 9. LLM Provider (llm-provider.md)        │
├─────────────────────────────────────────┤
│ 10. 데이터 파이프라인 (data-pipeline.md) │
├─────────────────────────────────────────┤
│ 11. API 초안 (api-draft.md)              │
├─────────────────────────────────────────┤
│ 12. 확장 로드맵 (future-roadmap.md)      │
├─────────────────────────────────────────┤
│ 13. 개발 Phase (development-phases.md)   │
├─────────────────────────────────────────┤
│ 14. UI 프롬프트 (ui-prompts.md)          │
├─────────────────────────────────────────┤
│ 15. 리스크 관리 (risk-management.md)     │
├─────────────────────────────────────────┤
│ 16. 결론 & 다음 단계                     │
└─────────────────────────────────────────┘
```
