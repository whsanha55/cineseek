#!/usr/bin/env bash
# E2E 서버 — start | stop | status
#   start  : 인프라(postgres, qdrant, embed, stub) 기동 → bootJar 빌드 → java -jar(e2e profile) 실행 → health 대기
#   stop   : api 종료, 컨테이너와 데이터 삭제 (embed 모델 캐시 볼륨은 남긴다)
#   status : api 프로세스·health와 컨테이너 상태
set -euo pipefail

E2E_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "$E2E_DIR/../.." && pwd)"
RUN_DIR="$E2E_DIR/.run"
PID_FILE="$RUN_DIR/app.pid"
LOG_FILE="$RUN_DIR/app.log"
APP_URL="http://localhost:18080"
STUB_URL="http://localhost:18089"
MODELS_VOLUME="${E2E_EMBED_MODELS_VOLUME:-gonamu-cineseek_gonamu_embed_models}"

compose() {
  docker compose -f "$E2E_DIR/docker-compose.yml" "$@"
}

app_running() {
  [ -f "$PID_FILE" ] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null
}

# wait_http 이름 URL 제한초 — 2xx가 올 때까지 기다린다
wait_http() {
  local name=$1 url=$2 limit=$3 waited=0
  until curl -fsS -o /dev/null "$url" 2>/dev/null; do
    if [ "$waited" -ge "$limit" ]; then
      echo "$name 대기 시간 초과 (${limit}s): $url" >&2
      return 1
    fi
    sleep 2
    waited=$((waited + 2))
  done
  echo "$name 준비 완료: $url"
}

start() {
  if app_running; then
    echo "이미 실행 중이다. pid=$(cat "$PID_FILE")"
    return 0
  fi
  mkdir -p "$RUN_DIR"
  docker volume create "$MODELS_VOLUME" >/dev/null

  echo "[1/3] 인프라 기동 — 첫 기동은 embed 이미지 빌드와 bge-m3 모델(~2GB) 다운로드로 오래 걸린다"
  compose up -d --build --wait --wait-timeout 900
  wait_http stub "$STUB_URL/__admin/health" 60

  echo "[2/3] bootJar 빌드"
  (cd "$ROOT_DIR/kotlin" && ./gradlew -q bootJar)

  echo "[3/3] api 기동 — 로그: $LOG_FILE"
  # 작업 디렉터리를 .run으로 둔다 — application.yml이 작업 디렉터리의 .env를 읽으므로 로컬 비밀값이 섞이지 않게
  (
    cd "$RUN_DIR"
    nohup java -jar "$ROOT_DIR/kotlin/build/libs/cineseek.jar" \
      --spring.profiles.active=e2e \
      --spring.config.additional-location="file:$E2E_DIR/" >"$LOG_FILE" 2>&1 &
    echo $! >"$PID_FILE"
  )
  if ! wait_http api "$APP_URL/actuator/health" 180; then
    tail -50 "$LOG_FILE" >&2
    exit 1
  fi
}

stop() {
  if app_running; then
    local pid waited=0
    pid=$(cat "$PID_FILE")
    kill "$pid"
    while kill -0 "$pid" 2>/dev/null && [ "$waited" -lt 30 ]; do
      sleep 1
      waited=$((waited + 1))
    done
    kill -0 "$pid" 2>/dev/null && kill -9 "$pid"
    echo "api 종료. pid=$pid"
  fi
  rm -f "$PID_FILE"
  compose down -v --remove-orphans
}

status() {
  local healthy=0
  if app_running; then
    echo "api: 실행 중 (pid=$(cat "$PID_FILE"))"
    if curl -fsS "$APP_URL/actuator/health"; then
      echo
      healthy=1
    fi
  else
    echo "api: 중지"
  fi
  compose ps
  [ "$healthy" -eq 1 ]
}

case "${1:-}" in
  start) start ;;
  stop) stop ;;
  status) status ;;
  *)
    echo "사용법: $0 start|stop|status" >&2
    exit 2
    ;;
esac
