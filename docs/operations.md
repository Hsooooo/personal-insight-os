# 운영 가이드

## 구성과 포트

| 서비스 | 내부 포트 | 외부 노출 | 헬스체크 |
|--------|-----------|-----------|----------|
| caddy | 80/443 (admin 2019 내부) | 공개 | `wget :2019/config/` |
| frontend (nginx-unprivileged) | 8080 | Caddy 경유 | `GET /` |
| backend | 8080 | Caddy `/api*`, `/.well-known/oauth-authorization-server*` | `GET /actuator/health` (공개 라우팅 없음) |
| mcp | 8001 | Caddy `/mcp*`, `/.well-known/oauth-protected-resource*` / 호스트 127.0.0.1:8001 | `GET /.well-known/oauth-protected-resource` = 200 |
| postgres | 5432 | 127.0.0.1만 | `pg_isready` |

`/api/oauth/introspect`는 Caddy에서 404로 차단된다 (MCP -> backend 내부망 호출 전용).

## 환경 변수

`.env.example`을 복사해 채운다. 필수: `JWT_SECRET`, `PIOS_ENCRYPTION_KEY`, `PIOS_MCP_INTERNAL_SECRET`, `POSTGRES_PASSWORD`, `PIOS_PUBLIC_BASE_URL`, `PIOS_CORS_ALLOWED_ORIGINS`. 누락 시 `docker compose`가 즉시 실패한다.
`PIOS_MCP_RESOURCE_URL`, `PIOS_FRONTEND_BASE_URL`은 compose에서 `PIOS_PUBLIC_BASE_URL`로부터 파생된다.

## 상태 확인과 로그

```bash
docker compose ps                      # STATUS에 (healthy) 표시
docker compose logs -f --tail=200 backend
docker compose logs caddy | tail       # JSON 액세스 로그
```
모든 서비스는 json-file 드라이버(10MB x 5개)로 로테이션된다.

## 백업 / 복구

```bash
scripts/ops/backup-postgres.sh                 # ./backups/pios-<timestamp>.sql.gz
BACKUP_GARMIN_TOKENS=1 RETENTION_DAYS=30 scripts/ops/backup-postgres.sh /var/backups/pios
scripts/ops/restore-postgres.sh backups/pios-20260101-030000.sql.gz
```
복구 후에도 `PIOS_ENCRYPTION_KEY`가 백업 시점과 같아야 저장된 시크릿(LLM 키, Garmin 비밀번호)을 복호화할 수 있다. 시크릿(.env)은 별도로 안전하게 보관한다.

### cron 예시 (매일 03:10)
```cron
10 3 * * * cd /opt/pios && BACKUP_GARMIN_TOKENS=1 scripts/ops/backup-postgres.sh /var/backups/pios >> /var/log/pios-backup.log 2>&1
```

### launchd 예시 (macOS)
`~/Library/LaunchAgents/kr.pios.backup.plist`:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0"><dict>
  <key>Label</key><string>kr.pios.backup</string>
  <key>ProgramArguments</key><array>
    <string>/bin/bash</string><string>-lc</string>
    <string>cd /path/to/me-graph &amp;&amp; scripts/ops/backup-postgres.sh</string>
  </array>
  <key>StartCalendarInterval</key><dict><key>Hour</key><integer>3</integer><key>Minute</key><integer>10</integer></dict>
</dict></plist>
```
`launchctl load ~/Library/LaunchAgents/kr.pios.backup.plist`

## 시크릿 교체

- **JWT_SECRET**: 교체 시 모든 사용자 JWT와 OAuth access token이 무효화된다(재로그인). 새 값 설정 후 `docker compose up -d backend`.
- **PIOS_MCP_INTERNAL_SECRET**: backend와 mcp가 같은 값을 써야 한다. `.env` 변경 후 `docker compose up -d backend mcp`를 함께 재시작.
- **POSTGRES_PASSWORD**: 기존 볼륨은 초기 비밀번호를 유지하므로 `ALTER USER pios PASSWORD '...'` 후 `.env`를 맞추고 backend 재시작.
- **PIOS_ENCRYPTION_KEY**: (1) 기존 값을 `PIOS_ENCRYPTION_KEY_LEGACY`로 옮기고 (2) 새 값을 `PIOS_ENCRYPTION_KEY`에 설정, (3) backend 재시작 -> 기동 시 재암호화, (4) 완료 로그 확인 후 `PIOS_ENCRYPTION_KEY_LEGACY`를 비움.
- 생성: `openssl rand -base64 48`

## 업그레이드

```bash
scripts/ops/backup-postgres.sh        # 항상 먼저 백업
git pull
docker compose build --pull
docker compose up -d                  # depends_on(service_healthy) 순서로 기동, Flyway 자동 마이그레이션
docker compose ps
```
롤백: 이전 커밋으로 checkout 후 재빌드. 스키마가 바뀐 릴리스는 복구 스크립트로 백업을 되돌린다.

### 비root 전환 참고 (이번 변경)
backend 컨테이너는 uid 10001로 실행된다. 기존 `garmin_tokens` 볼륨이 root 소유면 한 번만 소유권을 바꾼다.
```bash
docker compose run --rm --user root --entrypoint chown backend -R 10001:10001 /app/data/garmin-tokens
```
frontend는 포트가 80 -> 8080으로 바뀌었다 (Caddy는 `frontend:8080`으로 프록시).

## CI

`.github/workflows/ci.yml`: backend 테스트, frontend lint/test/build, mcp pytest, 스크립트 컴파일, 모든 이미지 docker build(푸시 없음), `docker compose config` 검증, `npm audit`(critical 차단), PR 의존성 리뷰. Dependabot(주 1회, 그룹화)이 npm/maven/pip/docker/actions 업데이트 PR을 만든다.
