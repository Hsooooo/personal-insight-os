# 10. LLM Provider 설계

## 10.1 Provider 후보

```text
OpenAI
Anthropic
Google Gemini
OpenRouter
Ollama
Local LLM
```

## 10.2 모델 사용 분리

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

## 10.3 Provider Adapter 인터페이스 초안

```java
public interface LlmProviderAdapter {
    String providerName();

    ChatResponse chat(ChatRequest request);

    EmbeddingResponse embed(EmbeddingRequest request);

    boolean supportsEmbedding();
}
```

## 10.4 주요 설정

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

---

## 10.5 설정 화면 구성

```text
1. LLM Provider 설정
   - OpenAI
   - Anthropic
   - Google Gemini
   - OpenRouter
   - Ollama
   - Local LLM

2. 모델 설정
   - 기본 Chat Model
   - 분석용 Model
   - 임베딩 Model
   - 저비용 분류 Model

3. 비용 제한
   - 월 사용량 제한
   - 요청당 토큰 제한

4. 데이터 설정
   - 동기화 주기
   - 보관 기간
   - 삭제 옵션
```
