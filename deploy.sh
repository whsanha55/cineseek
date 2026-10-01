#!/usr/bin/env bash
# 서버 배포 — compose(base + prod) 빌드·기동 후 UI(nginx) 경유로 API 응답 확인
set -uo pipefail
cd "$(dirname "$0")"
COMPOSE="docker compose -f compose.yml -f compose.prod.yml"

echo "==> cineseek compose up -d --build"
$COMPOSE up -d --build --remove-orphans || { echo "==> FAIL compose"; $COMPOSE logs --tail=30; exit 1; }

for _ in $(seq 1 120); do
  curl -sf http://127.0.0.1:20040/cineseek/genres >/dev/null 2>&1 && { echo "==> Done. $(git rev-parse --short HEAD)"; exit 0; }
  sleep 1
done
echo "==> FAIL health"
$COMPOSE logs --tail=30
exit 1
