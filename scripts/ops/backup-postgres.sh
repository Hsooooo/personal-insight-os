#!/usr/bin/env bash
# PIOS Postgres 백업. 사용: scripts/ops/backup-postgres.sh [백업디렉터리]
# 환경변수: BACKUP_DIR(기본 ./backups), RETENTION_DAYS(기본 14), BACKUP_GARMIN_TOKENS=1 이면 garmin_tokens 볼륨도 tar
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

BACKUP_DIR="${1:-${BACKUP_DIR:-$ROOT/backups}}"
RETENTION_DAYS="${RETENTION_DAYS:-14}"
TS="$(date +%Y%m%d-%H%M%S)"
mkdir -p "$BACKUP_DIR"
umask 077

OUT="$BACKUP_DIR/pios-$TS.sql.gz"
docker compose exec -T postgres pg_dump -U pios -d pios --no-owner --clean --if-exists | gzip -9 > "$OUT.tmp"
# 비어있는/손상된 덤프 방지
gzip -t "$OUT.tmp"
mv "$OUT.tmp" "$OUT"
echo "DB backup: $OUT ($(du -h "$OUT" | cut -f1))"

if [ "${BACKUP_GARMIN_TOKENS:-0}" = "1" ]; then
  VOL="$(docker compose config --format json | python3 -c 'import json,sys; print(json.load(sys.stdin)["name"])')_garmin_tokens"
  TOK="$BACKUP_DIR/garmin-tokens-$TS.tar.gz"
  docker run --rm -v "$VOL":/data:ro alpine:3.20 tar -C /data -czf - . > "$TOK"
  echo "Garmin tokens backup: $TOK"
fi

find "$BACKUP_DIR" -maxdepth 1 -type f \( -name 'pios-*.sql.gz' -o -name 'garmin-tokens-*.tar.gz' \) -mtime +"$RETENTION_DAYS" -print -delete
