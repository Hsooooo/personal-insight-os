# Personal Insight OS (PIOS) Backend

## 프로젝트 개요

**Personal Insight OS (PIOS)**의 MVP 백엔드로, 개인 건강 및 활동 인사이트 플랫폼입니다. 이 애플리케이션은 건강 및 피트니스 데이터(주로 Garmin으로부터)를 수집하고, 관계형 데이터베이스에 저장하며, 지식 그래프(Neo4j)로 투영하고, LLM API(OpenAI)를 사용하여 사용자의 질문에 근거 기반 인사이트를 생성합니다.

주요 기능:
- JWT를 이용한 사용자 인증
- Garmin Connect 실제 데이터 동기화 (Python `garminconnect` 라이브러리 via ProcessBuilder)
- 동기화 이력 관리 (`sync_logs` 테이블)
- 자동 동기화 (Spring Scheduler, 매일 새벽 3시)
- Rate limit (30초) 및 청크 단위 처리 (30일)
- 건강 지표, 수면, 활동 추적
- 목표 관리
- 사용자별 LLM 제공자 설정
- AI 생성 인사이트를 활용한 자연어 Q&A
- Neo4j를 통한 지식 그래프 시각화
- 대시보드 요약
- Mock 데이터 생성 (개발/테스트용)

## 기술 스택

- **Language**: Java 21
- **Framework**: Spring Boot 3.3.0
- **Web**: Spring Web (REST API)
- **Data Access**: Spring Data JPA (Hibernate)
- **Security**: Spring Security + JWT (jjwt 0.12.5)
- **Validation**: Jakarta Bean Validation
- **Database**: PostgreSQL (`pgcrypto`, `vector` 확장 포함)
- **Graph Database**: Neo4j 5.x (`neo4j-java-driver` 5.20.0)
- **Migrations**: Flyway
- **LLM Integration**: OpenAI API (`openai-java` 0.13.0, raw REST 호출 fallback)
- **Build Tool**: Maven
- **Utilities**: Lombok, Spring Actuator
- **Container**: Docker (`eclipse-temurin:21` 멀티스테이지 빌드) + Python 3 + `garminconnect>=0.3.0`

## 프로젝트 구조

```
src/main/java/com/pios/
├── PiosApplication.java              # Entry point
├── config/                           # Spring 설정 빈
│   ├── AppConfig.java                # Neo4j Driver, RestTemplate 빈
│   ├── AsyncConfig.java              # @EnableAsync + @EnableScheduling + sync ThreadPool
│   └── GlobalExceptionHandler.java   # @RestControllerAdvice (429 TooFrequentSync 추가)
├── controller/                       # REST 컨트롤러 (모두 /api 접두어)
│   ├── ActivityController.java       # GET /api/activities
│   ├── AskController.java            # POST /api/ask
│   ├── AuthController.java           # POST /api/auth/{register,login}, GET /api/auth/me
│   ├── DashboardController.java      # GET /api/dashboard/summary
│   ├── DataSourceController.java     # GET/POST/DELETE /api/data-sources/garmin/*
│   ├── GoalController.java           # CRUD /api/goals
│   ├── GraphController.java          # GET /api/graph
│   ├── HealthController.java         # GET /api/health/{metrics,sleep}
│   ├── InsightController.java        # CRUD /api/insights
│   └── LlmProviderController.java    # CRUD /api/settings/llm-providers
├── domain/                           # JPA 엔티티 클래스
│   ├── GarminActivity.java
│   ├── GarminActivityLap.java
│   ├── GarminDailyHealthMetric.java
│   ├── GarminSleepSession.java
│   ├── Goal.java
│   ├── GraphNodeMapping.java
│   ├── Insight.java
│   ├── InsightEvidence.java
│   ├── LlmProvider.java
│   ├── ProviderConnection.java        # + sync_config (JSONB)
│   ├── Question.java
│   ├── SyncLog.java                   # 동기화 이력
│   ├── User.java
│   └── enums/                         # SyncStatus, SyncType
│       ├── SyncStatus.java
│       └── SyncType.java
├── dto/                              # Request/response DTO
│   ├── ApiResponse.java              # Generic wrapper: {success, message, data}
│   ├── AskRequest.java
│   ├── AskResponse.java
│   ├── AuthRequest.java
│   ├── AuthResponse.java
│   └── ... (many more DTOs)
├── repository/                       # Spring Data JPA 레포지토리
├── security/                         # JWT 필터, 유틸리티, 보안 설정
│   ├── JwtAuthenticationFilter.java
│   ├── JwtUtil.java
│   └── SecurityConfig.java
└── service/                          # 비즈니스 로직 레이어
    ├── ActivityService.java
    ├── AskService.java               # LLM 오케스트레이션 + 인사이트 생성
    ├── AuthService.java
    ├── DashboardService.java
    ├── DataSourceService.java
    ├── GarminPythonClient.java       # ProcessBuilder로 Python 스크립트 실행
    ├── GarminSyncService.java        # 실제 Garmin 동기화 + Rate limit + 청크 처리
    ├── GoalService.java
    ├── GraphProjectorService.java    # 관계형 데이터를 Neo4j로 동기화
    ├── GraphService.java             # 시각화를 위한 그래프 데이터 조회
    ├── HealthService.java
    ├── InsightService.java
    ├── LlmProviderService.java
    ├── MockDataService.java          # 합성 Garmin 데이터 생성 (개발/테스트용)
    └── SyncScheduleService.java      # Spring Scheduler 자동 동기화

src/main/resources/
├── application.yml                   # 전체 설정 (아래 참고)
└── db/migration/
    ├── V1__init.sql                  # Flyway 기본 스키마
    └── V2__add_sync_system.sql       # sync_logs + provider_connections.sync_config

scripts/
└── garmin_sync.py                    # Python garminconnect 라이브러리 호출 스크립트
```

## 빌드 및 실행 명령어

### 필수 조건
- Java 21
- Maven
- PostgreSQL (`pgcrypto`, `vector` 확장 포함)
- Neo4j (포트 7687, Bolt 프로토콜)

### 로컬 개발
```bash
# 빌드
mvn clean package

# 실행 (기본 로컬 DB 설정으로)
mvn spring-boot:run

# 또는 JAR 실행
java -jar target/pios-backend-1.0.0.jar
```

서버는 포트 `8080`에서 시작됩니다.

### Docker
```bash
docker build -t pios-backend .
docker run -p 8080:8080 pios-backend
```

Dockerfile은 멀티스테이지 빌드를 사용합니다:
1. 빌더 스테이지: Maven으로 컴파일
2. 런타임 스테이지: JRE 21로 fat JAR 실행

## 데이터베이스 및 마이그레이션

- **Migration tool**: Flyway (활성화, baseline-on-migrate)
- **Location**: `classpath:db/migration`
- **Current baseline**: `V1__init.sql`이 모든 테이블, 인덱스, PostgreSQL 확장(`pgcrypto`, `vector`)을 생성
- **JPA mode**: `ddl-auto: validate` (Hibernate는 검증만 수행, 스키마를 수정하지 않음)

### 주요 테이블
| Table | Purpose |
|-------|---------|
| `users` | 사용자 계정 |
| `provider_connections` | 외부 데이터 소스 연결 (예: Garmin) + 동기화 설정 |
| `garmin_activities` / `garmin_activity_laps` | 운동 데이터 |
| `garmin_daily_health_metrics` | 일일 건강 지표 (RHR, HRV, 스트레스, 걸음 수 등) |
| `garmin_sleep_sessions` | 수면 기록 |
| `goals` | 사용자 목표 |
| `llm_providers` | 사용자별 LLM API 키 및 설정 |
| `questions` | `/api/ask`를 통해 사용자가 한 질문 |
| `insights` | AI가 생성한 분석 결과 |
| `insight_evidences` | 인사이트에 연결된 근거 행 |
| `graph_node_mappings` | 관계형 ID와 Neo4j 노드 ID 간의 매핑 |
| `sync_logs` | 동기화 이력 (상태, 기간, 레코드 수, 에러 메시지) |

## 설정

모든 민감한 값 및 환경별 설정은 환경 변수를 통해 외부화되며, 로컬 기본값은 `application.yml`에 정의되어 있습니다:

| Property | Env Var | Default |
|----------|---------|---------|
| `spring.datasource.url` | `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/pios` |
| `spring.datasource.username` | `SPRING_DATASOURCE_USERNAME` | `pios` |
| `spring.datasource.password` | `SPRING_DATASOURCE_PASSWORD` | `pios123` |
| `jwt.secret` | `JWT_SECRET` | `pios-jwt-secret-key-2026-change-in-production` |
| `jwt.expiration` | — | `86400000` (24시간, ms) |
| `neo4j.uri` | `SPRING_NEO4J_URI` | `bolt://localhost:7687` |
| `neo4j.username` | `SPRING_NEO4J_AUTHENTICATION_USERNAME` | `neo4j` |
| `neo4j.password` | `SPRING_NEO4J_AUTHENTICATION_PASSWORD` | `pios1234` |
| `openai.api-key` | `OPENAI_API_KEY` | *(empty)* |
| `openai.model` | — | `gpt-4o-mini` |
| `garmin.python.script-path` | — | `scripts/garmin_sync.py` |
| `garmin.python.timeout-seconds` | — | `120` |
| `sync.rate-limit-seconds` | — | `30` |
| `sync.chunk-days` | — | `30` |
| `sync.default-full-sync-months` | — | `12` |
| `sync.schedule.cron` | — | `0 0 3 * * *` (매일 03:00) |

**중요**: 기본 JWT secret 및 데이터베이스 자격 증명은 로컬 개발용입니다. 프로덕션에서는 반드시 환경 변수를 통해 변경하세요.

## 코드 스타일 및 규칙

### 레이어드 아키텍처
- **Controller**: HTTP 처리, 입력값 검증(`@Valid`), `@AuthenticationPrincipal`에서 `userId` 추출, `ApiResponse<T>` 반환.
- **Service**: 비즈니스 로직 및 트랜잭션 포함. 쓰기 작업에 `@Transactional` 사용.
- **Repository**: Spring Data JPA 인터페이스. 커스텀 쿼리는 JPQL과 함께 `@Query` 사용.
- **Domain**: Lombok 어노테이션(`@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor`)이 적용된 JPA 엔티티.
- **DTO**: `@Data @Builder @NoArgsConstructor @AllArgsConstructor`를 사용한 불변 스타일 데이터 클래스.

### 공통 패턴
- **Dependency injection**: 모든 곳에서 `@RequiredArgsConstructor`(Lombok)를 통한 생성자 주입.
- **API response wrapper**: 모든 컨트롤러는 `ApiResponse.ok(...)`를 반환하거나, 글로벌 예외 핸들러가 `ApiResponse.error(...)`를 반환하도록 위임.
- **Error handling**: 비즈니스 검증 오류에는 `IllegalArgumentException`을 사용하여 HTTP 400에 매핑. 처리되지 않은 예외는 HTTP 500에 매핑.
- **User isolation**: 거의 모든 쿼리는 `userId`로 범위가 제한됨. 서비스는 데이터 반환 또는 변경 전 소유권을 검증.
- **Builder pattern**: 엔티티 및 DTO 구성에 광범위하게 사용.

### 주요 구현 상세
- **Authentication**: JWT 필터는 principal을 `Long userId`로 설정 (NOT `UserDetails`). 컨트롤러는 `@AuthenticationPrincipal Long userId`로 수신.
- **OpenAI integration**: `AskService`는 한국어 시스템 프롬프트와 함께 OpenAI Chat Completions REST API를 직접 호출. API 키가 없거나 호출이 실패하면 일반 템플릿 응답으로 fallback.
- **실제 Garmin 동기화**: `GarminSyncService.sync()`가 `GarminPythonClient`를 통해 Python `garminconnect` 라이브러리를 실행. 30일 청크 단위로 데이터를 수신하여 PostgreSQL에 UPSERT 저장 후 Neo4j에 투영. Rate limit(30초), sync_logs 이력 관리, Spring Scheduler(매일 03:00) 지원.
- **Mock data**: `DataSourceService.generateMockData()`는 개발/테스트용으로 `MockDataService.generateMockData()`를 트리거하여 30일치 건강/수면 데이터와 10개의 무작위 활동을 생성. 실제 동기화와 별도로 유지.
- **Graph projection**: `GraphProjectorService`는 관계형 데이터를 읽고 Neo4j에서 관계(`PERFORMED`, `HAS_METRIC`, `HAS_SLEEP`)와 함께 노드를 생성/머지. 중복 방지를 위해 매핑 테이블을 유지.

## 테스트

- **Test dependencies**: `spring-boot-starter-test`, `spring-security-test`가 `pom.xml`에 구성되어 있음.
- **Current state**: 현재 레포지토리에 테스트 클래스가 없음. 테스트 추가 시 통합 테스트는 `@SpringBootTest`를, 슬라이스 테스트는 `@WebMvcTest` / `@DataJpaTest`를 사용.
- **Maven**: Docker 빌드에서 테스트가 스킵됨 (`-DskipTests`).

## 보안 고려사항

- **Passwords**: BCrypt (`BCryptPasswordEncoder`)로 저장.
- **JWT**: HS256 서명 토큰. 토큰 만료 시간은 설정 가능 (기본 24시간).
- **CORS**: `http://localhost:5173` 및 `http://localhost:3000`에 대해 구성. 프로덕션 origin은 `SecurityConfig.corsConfigurationSource()`를 업데이트.
- **Public endpoints**: `/api/auth/**` 및 `/actuator/health`는 permit-all. 나머지 모든 엔드포인트는 인증 필요.
- **API keys**: 현재 MVP에서 LLM 제공자 API 키는 평문(`api_key_encrypted` 컬럼)으로 저장. 실제 암호화는 아직 구현되지 않음.
- **Actuator**: Spring Boot Actuator가 클래스패스에 있음. 보안 규칙에 따라 `/actuator/health`만 공개적으로 노출; 프로덕션 배포 전 Actuator 노출 범위를 검토.
