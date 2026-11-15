# PIOS Backend

프로젝트 전체 규약(스택, 데이터 흐름, API/오류 규약, 환경 변수, 보안)은 루트 [`../AGENTS.md`](../AGENTS.md)를 먼저 읽으세요. 이 파일은 backend 작업 시에만 필요한 사항을 담습니다.

## 스택

Java 21, Spring Boot 3.5.x (parent 3.5.16), Spring Security + JWT(jjwt), Spring Data JPA, Flyway(V1~V27), Neo4j Java Driver, Apache POI, Lombok, Maven. LLM 호출은 `service/llm/LlmClient`(OpenAI 호환 REST).

## 패키지 구조 (`src/main/java/com/pios`)

| 패키지 | 내용 |
|--------|------|
| `common/` | `NotFoundException`(404), `AppTimeZones` |
| `config/` | `AppConfig`, `AsyncConfig`(동기화 스레드풀), `GlobalExceptionHandler`, `SecretMigrationRunner`(암호화 키 교체), `GraphSchemaInitializer`, `TimeZoneConsistencyCheck` |
| `controller/` | REST Controller (`/api/*`) |
| `domain/`, `dto/`, `repository/`, `spec/` | JPA Entity, DTO, Spring Data, Specification |
| `oauth/` | OAuth 2.1 Authorization Server (controller/domain/dto/repository/service) |
| `security/` | `SecurityConfig`, `JwtAuthenticationFilter`, `McpAccessPolicy`, `InternalSecretVerifier`, `LoginAttemptService`, `SecretCryptoService` 등 |
| `service/` | 비즈니스 로직. 하위: `alert/`, `ask/`, `briefing/`, `finance/`, `garmin/`, `goal/`, `llm/`, `pattern/` |

## 빌드 / 실행 / 테스트

```bash
docker compose up -d postgres                      # 저장소 루트에서
SPRING_PROFILES_ACTIVE=local mvn spring-boot:run   # :8080, application-local.yml 개발용 기본값
mvn -B test                                        # JUnit 5 (~180개)
mvn clean package -DskipTests
```
JDK 21이 없으면: `docker run --rm -v "$PWD":/app -v "$HOME/.m2":/root/.m2 -w /app maven:3.9.9-eclipse-temurin-21 mvn -B test`

`local` 프로필이 아니면 `SPRING_DATASOURCE_PASSWORD`, `JWT_SECRET`, `PIOS_ENCRYPTION_KEY`, `PIOS_MCP_INTERNAL_SECRET`, `SPRING_NEO4J_URI`, `SPRING_NEO4J_AUTHENTICATION_PASSWORD`에 기본값이 없어 기동에 실패합니다 (fail-fast).

Dockerfile: Maven 빌더 → `eclipse-temurin:21-jre-alpine` + python3/`garminconnect`, 비root(uid 10001), `/app/data/garmin-tokens` 볼륨, `HEALTHCHECK /actuator/health`.

## DB

- Flyway(`classpath:db/migration`), `ddl-auto: validate`. 새 마이그레이션은 **V28**부터.
- 주요 테이블과 DDL은 `../docs/database-schema.md`.

## 구현 노트

- Controller는 `@AuthenticationPrincipal Long userId`를 받고 `ApiResponse.ok(...)`를 반환 (OAuth 프로토콜 엔드포인트만 raw JSON). 서비스는 항상 `userId`로 범위를 제한하고 없거나 남의 리소스는 `NotFoundException`.
- 동기화: `GarminSyncService`(요청/rate limit) → `GarminSyncExecutor`(@Async) → `GarminPythonClient`(`scripts/garmin_sync.py`, 7일 청크) → `service/garmin/*Persister`. 상태는 `COMPLETED`/`PARTIAL`/`FAILED`. 설정은 `application.yml`의 `sync.*`, `garmin.*`.
- 스케줄: 동기화 12:00, 이상 신호는 스케줄 동기화 직후(이벤트) + 12:30 fallback, 주간 브리핑 일요일 13:00 (모두 KST, `app.timezone`).
- LLM: `LlmCredentialsResolver`가 사용자 Provider 키를 먼저, 없으면 `openai.*` 전역 설정을 사용. 사용자 키/Garmin 비밀번호는 `SecretCryptoService`로 AES-GCM 암호화(`ENC:` prefix).
- 보안: `ROLE_MCP` 주체는 `McpAccessPolicy` allowlist만 접근. 새 API를 MCP에 노출하려면 allowlist에 추가해야 합니다.
- 테스트 위치: `src/test/java/com/pios/{controller,oauth,security,service}`. 문서/스키마가 바뀌면 `docs/`와 `CHANGELOG.md`도 함께 갱신하세요.
