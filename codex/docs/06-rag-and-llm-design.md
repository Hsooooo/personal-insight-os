# RAG와 LLM Provider 설계

## 문서 목적

Ask My Data 기능의 RAG 파이프라인, 질문 의도 분류, 답변 포맷, LLM Provider 추상화 구조를 정의합니다.

## RAG 전체 흐름

```text
사용자 질문
↓
질문 의도 분석
↓
관련 기간 추론
↓
관련 데이터 타입 결정
↓
PostgreSQL 정형 데이터 조회
↓
Neo4j 관계 조회
↓
Vector Search
↓
근거 후보 수집
↓
LLM 답변 생성
↓
근거 포함 응답 반환
↓
인사이트 저장
↓
사용자 피드백 반영
```

## 질문 의도 타입

```text
CONDITION_ANALYSIS
- 최근 컨디션 분석

PERFORMANCE_ANALYSIS
- 운동 성과 분석

RECOVERY_ANALYSIS
- 회복 상태 분석

SLEEP_ANALYSIS
- 수면 영향 분석

GOAL_ALIGNMENT
- 목표와 실제 행동 정렬 분석

PATTERN_DETECTION
- 반복 패턴 탐지

DATA_LOOKUP
- 단순 데이터 조회
```

## 응답 포맷

```text
결론:
최근 컨디션 저하는 수면 시간 자체보다 운동 강도 누적과 회복 지표 저하와 더 관련 있어 보입니다.

근거:
- 최근 7일 고강도 운동 3회
- 안정시 심박 평균 상승
- Body Battery 회복 저조
- 수면 시간은 큰 폭으로 줄지 않았으나 회복 점수가 낮게 유지됨

관련 데이터:
- Activity #123
- Sleep #45
- HealthMetric 2026-04-21

신뢰도:
중간

추가 확인 질문:
최근 업무 스트레스나 주관적 피로 기록이 있다면 함께 분석할 수 있습니다.
```

## 환각 방지 원칙

```text
1. 답변보다 근거 수집이 먼저입니다.
2. 실제 조회된 데이터가 없는 내용은 말하지 않습니다.
3. 인과관계를 단정하지 않습니다.
4. "가능성이 있습니다", "관련 있어 보입니다" 수준으로 표현합니다.
5. 의료 진단처럼 보이는 표현은 금지합니다.
6. 모든 인사이트에는 근거 데이터를 연결합니다.
```

## LLM Provider 후보

```text
OpenAI
Anthropic
Google Gemini
OpenRouter
Ollama
Local LLM
```

## 모델 사용 분리

```text
일반 질문 답변
- 고성능 Chat Model

데이터 요약
- 중간급 모델

분류/태깅
- 저비용 모델

임베딩
- Embedding 전용 모델

주간 리포트
- 고성능 모델
```

## Provider Adapter 인터페이스 초안

```java
public interface LlmProviderAdapter {
    String providerName();

    ChatResponse chat(ChatRequest request);

    EmbeddingResponse embed(EmbeddingRequest request);

    boolean supportsEmbedding();
}
```

## 주요 설정

```text
provider_name
api_key_encrypted
default_chat_model
embedding_model
enabled
monthly_budget_limit
temperature
max_tokens
```

## Ask My Data 구현 책임

```text
질문 저장
질문 의도 분류
기간 추론
근거 데이터 조회
그래프 관계 조회
관련 인사이트 검색
프롬프트 조립
LLM 호출
응답 파싱
근거 저장
인사이트 저장
피드백 반영
```

## 보안과 비용 제어

```text
API Key는 암호화 저장한다.
LLM으로 전송되는 데이터는 질문 답변에 필요한 범위로 제한한다.
요청당 토큰 제한과 월 사용량 제한을 둔다.
민감 데이터 마스킹 옵션을 고려한다.
로컬 LLM 또는 Ollama를 Provider 후보로 유지한다.
```

