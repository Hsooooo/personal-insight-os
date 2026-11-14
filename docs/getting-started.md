# 🚀 실행 가이드

## 사전 준비

| 항목 | 버전 | 확인 명령 |
|------|------|----------|
| Docker / Docker Compose | 20.10+ / 2.0+ | `docker compose version` |
| (개발) JDK 21 + Maven | 21 | `java -version` (없으면 아래 Docker maven 사용) |
| (개발) Node.js | 22.13+ | `node -v` |
| (개발) Python | 3.10+ (mcp) | `python3 --version` |
| (선택) OpenAI API Key, Neo4j 인스턴스 | - | - |

---

## 빠른 시작 (Docker Compose)

```bash
cp .env.example .env
# .env 필수 값 채우기 (누락 시 docker compose가 실패):
#   JWT_SECRET, PIOS_ENCRYPTION_KEY, PIOS_MCP_INTERNAL_SECRET   -> openssl rand -base64 48 (서로 다른 값)
#   POSTGRES_PASSWORD
#   PIOS_PUBLIC_BASE_URL (개발: https://localhost), PIOS_CORS_ALLOWED_ORIGINS (동일 값)
# 첫 계정을 만들려면 PIOS_REGISTRATION_ENABLED=true 로 기동

docker compose up -d --build
docker compose ps        # 모든 서비스 (healthy) 확인
```

| 서비스 | 접속 | 비고 |
|--------|------|------|
| 웹 앱 / API / MCP | `https://localhost` (`PIOS_DOMAIN`) | Caddy가 유일한 외부 진입점. 개발 기본값은 Caddy 내부 CA라 브라우저 경고가 뜰 수 있음 |
| PostgreSQL | `127.0.0.1:5432` | 사용자 `pios`, 비밀번호 `.env`의 `POSTGRES_PASSWORD` |
| MCP 서버 | `127.0.0.1:8001` | 외부 접근은 Caddy `/mcp` 경유 |

backend(8080)와 frontend(8080)는 호스트에 노출되지 않습니다. 직접 접근이 필요하면 `docker compose exec`를 쓰거나 아래 개발 모드를 사용하세요.

---

## 첫 사용 흐름

1. `https://localhost` 접속 → 회원가입(`PIOS_REGISTRATION_ENABLED=true`일 때) 또는 로그인
2. **Data Sources**에서 Garmin 계정 연결 후 동기화 (실데이터). `PIOS_MOCK_DATA_ENABLED=true`이면 Mock 데이터 생성 가능
3. **Dashboard / Activities / Health / Graph**에서 데이터 확인
4. **Ask My Data**로 질문 (LLM Provider 키 또는 `OPENAI_API_KEY` 없으면 템플릿 응답)
5. **MCP 연동** 화면에서 MCP URL을 복사해 외부 AI 클라이언트에 등록 → OAuth 승인 화면(`/oauth/consent`)에서 연결 ([mcp-integration.md](mcp-integration.md))

---

## 개발 모드

### Backend
```bash
docker compose up -d postgres                       # DB만 컨테이너로 (저장소 루트)
cd backend
SPRING_PROFILES_ACTIVE=local mvn spring-boot:run    # http://localhost:8080
```
`local` 프로필(`application-local.yml`)이 개발용 DB 비밀번호/시크릿/Neo4j 기본값을 채웁니다. 프로필 없이 실행하려면 `SPRING_DATASOURCE_PASSWORD`, `JWT_SECRET`, `PIOS_ENCRYPTION_KEY`, `PIOS_MCP_INTERNAL_SECRET`, `SPRING_NEO4J_URI`, `SPRING_NEO4J_AUTHENTICATION_PASSWORD`를 직접 지정해야 합니다. 운영에서 `local` 프로필은 사용하지 마세요. compose의 postgres 비밀번호가 `pios123`이 아니면 `SPRING_DATASOURCE_PASSWORD`로 맞추세요.

### Frontend
```bash
cd frontend
npm install
npm run dev        # http://localhost:5173 (/api → http://localhost:8080 프록시)
npm run lint       # ESLint
npm test           # Vitest
npm run build      # tsc + vite build
```

### MCP 서버
```bash
cd mcp
python3 -m venv .venv && . .venv/bin/activate
pip install -r requirements-dev.txt
pytest tests
# stdio 실행 (로컬 클라이언트): PIOS_API_URL=http://localhost:8080/api PIOS_API_KEY=pios_xxx python server.py --transport stdio
```

### 백엔드 테스트 (JDK 21이 없을 때)
```bash
cd backend
mvn -B test
# JDK 21이 없으면 Docker로:
docker run --rm -v "$PWD":/app -v "$HOME/.m2":/root/.m2 -w /app maven:3.9.9-eclipse-temurin-21 mvn -B test
```

---

## 문제 해결

| 증상 | 원인 | 해결 |
|------|------|------|
| `docker compose`가 `... must be set in .env`로 실패 | 필수 환경변수 누락 | `.env`에 필수 값 채우기 |
| 백엔드가 `Could not resolve placeholder 'JWT_SECRET'` 등으로 기동 실패 | `local` 프로필/환경변수 없이 실행 | `SPRING_PROFILES_ACTIVE=local` 지정 |
| `Connection refused` | DB 아직 준비 안 됨 | `docker compose ps`로 healthy 대기 |
| `401 Unauthorized` | JWT 만료/누락 (프론트는 자동 refresh) | 재로그인 |
| `429 Too Many Requests` (로그인) | 로그인 실패 제한 | `Retry-After` 이후 재시도 |
| 브라우저 CORS 오류 | `PIOS_CORS_ALLOWED_ORIGINS`에 접속 origin 없음 | 값 수정 후 backend 재시작 |
| Garmin 토큰 쓰기 오류 (비root 전환 후) | `garmin_tokens` 볼륨 소유권 | [operations.md](operations.md#비root-전환-참고-이번-변경) |
| MCP 클라이언트 연결 실패 | `PIOS_PUBLIC_BASE_URL`과 실제 접속 URL 불일치 | [mcp-integration.md](mcp-integration.md) |
| LLM 응답이 템플릿 | OpenAI 키 미설정 | Settings에서 Provider 등록 또는 `OPENAI_API_KEY` |

운영(백업/복구, 시크릿 교체, 업그레이드)은 [operations.md](operations.md)를 참고하세요.
