#!/usr/bin/env bash
# 서버 배포 — compose(base + prod) 빌드·기동 후 UI(nginx) 경유로 API 응답 확인
# 사용: ./deploy.sh            전체 서비스
#       ./deploy.sh api ui     지정한 서비스만 (의존 서비스는 재생성하지 않는다 — embed 모델 재로딩 회피)
set -uo pipefail
cd "$(dirname "$0")"
COMPOSE="docker compose -f compose.yml -f compose.prod.yml"

if [ $# -gt 0 ]; then
  echo "==> cineseek compose up -d --build --no-deps $*"
  $COMPOSE up -d --build --no-deps "$@" || { echo "==> FAIL compose"; $COMPOSE logs --tail=30 "$@"; exit 1; }
else
  echo "==> cineseek compose up -d --build"
  $COMPOSE up -d --build --remove-orphans || { echo "==> FAIL compose"; $COMPOSE logs --tail=30; exit 1; }
fi

for _ in $(seq 1 120); do
  curl -sf http://127.0.0.1:20040/cineseek/genres >/dev/null 2>&1 && { echo "==> Done. $(git rev-parse --short HEAD)"; exit 0; }
  sleep 1
done
echo "==> FAIL health"
$COMPOSE logs --tail=30
exit 1
