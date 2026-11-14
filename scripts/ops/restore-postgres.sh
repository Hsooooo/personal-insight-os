#!/usr/bin/env bash
# PIOS Postgres 복구. 사용: scripts/ops/restore-postgres.sh backups/pios-YYYYmmdd-HHMMSS.sql.gz
# 경고: 기존 데이터를 덮어쓴다. 복구 전 backend를 중지해 두는 것을 권장 (docker compose stop backend mcp).
set -euo pipefail

[ $# -eq 1 ] && [ -f "$1" ] || { echo "usage: $0 <dump.sql.gz>" >&2; exit 1; }
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

if [ "${FORCE:-0}" != "1" ]; then
  read -r -p "기존 DB를 $1 로 덮어씁니다. 계속하려면 'yes': " ans
  [ "$ans" = "yes" ] || { echo aborted; exit 1; }
fi

gzip -dc "$1" | docker compose exec -T postgres psql -U pios -d pios -v ON_ERROR_STOP=1 --single-transaction
echo "restore complete. 'docker compose up -d'로 서비스를 다시 시작하세요."
