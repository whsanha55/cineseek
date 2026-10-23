#!/usr/bin/env bash
# E2E 공통 함수 — 리소스 스크립트가 source한다. curl, jq만 쓰고 Bash 3.2(macOS 기본)와 호환되게 쓴다.
# 요청마다 메서드·경로·HTTP 상태·응답 본문을 출력하고, 검증이 실패하면 기대값을 출력하고 바로 종료한다

E2E_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_URL="http://localhost:18080"
STUB_URL="http://localhost:18089"
# 응답 본문 임시 파일 — EXIT trap으로 지우지 않는다. Bash 3.2는 EXIT trap이 있으면 set -u(미정의 변수) 오류의
# 종료 코드를 0으로 바꿔, 실패한 스크립트가 run-all.sh에서 PASS로 집계된다. 파일은 server.sh stop이 지운다
mkdir -p "$E2E_DIR/.run"
RES_FILE="$E2E_DIR/.run/response-$$.json"

STATUS=""
BODY=""

step() {
  echo
  echo "▶ $*"
}

pass() {
  echo "  ✓ $*"
}

fail() {
  echo "  ✗ FAIL: $*" >&2
  exit 1
}

# 고유 키 — 스크립트마다 다른 TMDB id·이름을 만든다 (스크립트끼리 데이터를 공유하지 않는다).
# jq가 정수를 double로 다루므로 2^53 아래로 만든다. 각 스크립트는 다른 PID로 돌기 때문에 같은 초에 시작해도 겹치지 않는다
unique_key() {
  echo $((($(date +%s) % 100000000) * 100000 + ($$ % 1000) * 100))
}

# ---------- 요청 ----------

# get 경로 [키=값 ...] — 쿼리 값은 URL 인코딩한다. STATUS, BODY를 채운다
get() {
  local path=$1 shown=$1 sep="?" p
  shift
  local args=()
  for p in "$@"; do
    args+=(--data-urlencode "$p")
    shown="$shown$sep$p"
    sep="&"
  done
  STATUS=$(curl -sS -G ${args[@]+"${args[@]}"} -o "$RES_FILE" -w '%{http_code}' "$APP_URL$path") ||
    fail "요청을 보내지 못했다: GET $shown"
  BODY=$(cat "$RES_FILE")
  echo "  GET $shown → $STATUS"
  echo "    $BODY"
}

# ---------- 응답 검증 ----------

# expect_status 상태코드
expect_status() {
  [ "$STATUS" = "$1" ] || fail "HTTP 상태 기대=$1, 실제=$STATUS"
  pass "HTTP $1"
}

# expect_eq jq필터 기대값 — 응답 본문에 필터를 적용한 값(jq -r)이 기대값과 같아야 한다
expect_eq() {
  local actual
  actual=$(printf '%s' "$BODY" | jq -r "$1" 2>&1) || fail "jq 필터 실패: $1 ($actual)"
  [ "$actual" = "$2" ] || fail "$1 기대=$2, 실제=$actual"
  pass "$1 == $2"
}

# expect_true jq필터 [설명] — 필터 결과가 true여야 한다
expect_true() {
  printf '%s' "$BODY" | jq -e "$1" >/dev/null 2>&1 || fail "${2:-$1} — 기대: $1 이 true"
  pass "${2:-$1}"
}

# expect_error 상태코드 에러코드 — ProblemDetail(code) 오류 응답
expect_error() {
  expect_status "$1"
  expect_eq .code "$2"
}

# json jq필터 — 직전 응답 본문에서 값을 꺼낸다
json() {
  printf '%s' "$BODY" | jq -r "$1"
}

# wait_for 설명 제한초 jq필터 경로 [키=값 ...] — 200이고 필터가 true가 될 때까지 반복 호출한다 (수집 스케줄러 대기)
wait_for() {
  local desc=$1 limit=$2 filter=$3 started
  shift 3
  started=$(date +%s)
  while :; do
    get "$@"
    if [ "$STATUS" = 200 ] && printf '%s' "$BODY" | jq -e "$filter" >/dev/null 2>&1; then
      pass "$desc"
      return 0
    fi
    [ $(($(date +%s) - started)) -lt "$limit" ] ||
      fail "$desc — ${limit}s 안에 조건을 만족하지 못했다. 기대: HTTP 200, $filter 이 true"
    sleep 2
  done
}

# ---------- 스텁 등록 (WireMock admin API) ----------

# register_stub 매핑JSON
register_stub() {
  local status
  status=$(curl -sS -o "$RES_FILE" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
    --data "$1" "$STUB_URL/__admin/mappings") || fail "스텁 서버에 요청을 보내지 못했다"
  echo "  POST /__admin/mappings → $status $(printf '%s' "$1" | jq -c '[.request.method, .request.urlPath]')"
  [ "$status" = 201 ] || fail "스텁 등록 기대=201, 실제=$status: $(cat "$RES_FILE")"
}

# stub_tmdb 경로 응답JSON — TMDB GET 스텁 (경로는 /3 뒤, 예: /movie/123)
stub_tmdb() {
  register_stub "$(jq -n --arg path "/tmdb/3$1" --argjson body "$2" '{
    priority: 1,
    request: {method: "GET", urlPath: $path},
    response: {status: 200, headers: {"Content-Type": "application/json"}, jsonBody: $body}
  }')"
}

# stub_movie tmdbId 제목 줄거리 감독id 감독이름 배우id 배우이름 배역 [투표수=1200] [개봉일=2024-05-01]
#   — TMDB 영화 상세 + credits (장르: 드라마 18, 러닝타임 110)
stub_movie() {
  stub_tmdb "/movie/$1" "$(jq -n --argjson id "$1" --arg title "$2" --arg overview "$3" --argjson votes "${9:-1200}" --arg released "${10:-2024-05-01}" '{
    id: $id, title: $title, original_title: ("Original " + $title), overview: $overview,
    release_date: $released, runtime: 110, vote_average: 7.8, vote_count: $votes,
    poster_path: "/e2e-poster.jpg", original_language: "ko", origin_country: ["KR"],
    genres: [{id: 18, name: "드라마"}]
  }')"
  stub_tmdb "/movie/$1/credits" "$(jq -n --argjson d "$4" --arg dn "$5" --argjson c "$6" --arg cn "$7" --arg ch "$8" '{
    crew: [{id: $d, name: $dn, job: "Director"}],
    cast: [{id: $c, name: $cn, character: $ch, order: 0}]
  }')"
}

# stub_mood 프롬프트에_든_문자열 태그JSON배열 설명 — 해당 영화의 분위기 태깅(LLM) 응답
stub_mood() {
  local content
  content=$(jq -cn --argjson tags "$2" --arg desc "$3" '{tags: $tags, description: $desc}')
  register_stub "$(jq -n --arg text "$1" --arg content "$content" '{
    priority: 1,
    request: {method: "POST", urlPath: "/llm/v1/chat/completions", bodyPatterns: [{contains: $text}]},
    response: {
      status: 200, headers: {"Content-Type": "application/json"},
      jsonBody: {choices: [{message: {role: "assistant", content: $content}}]}
    }
  }')"
}

# stub_translation 프롬프트에_든_문자열 출생지번역 소개번역 — 해당 인물의 출생지·소개 번역(LLM) 응답
stub_translation() {
  local content
  content=$(jq -cn --arg place "$2" --arg bio "$3" '{placeOfBirth: $place, biography: $bio}')
  register_stub "$(jq -n --arg text "$1" --arg content "$content" '{
    priority: 1,
    request: {method: "POST", urlPath: "/llm/v1/chat/completions", bodyPatterns: [{contains: $text}]},
    response: {
      status: 200, headers: {"Content-Type": "application/json"},
      jsonBody: {choices: [{message: {role: "assistant", content: $content}}]}
    }
  }')"
}

# ---------- 데이터 준비 (SQL) ----------

# enqueue_task 작업종류 payloadKey 우선순위 — 수집 작업 등록. 영화·인물은 수집 스케줄러만 만들어서 API 대신 SQL로 넣는다
enqueue_task() {
  local sql="INSERT INTO collect_task (task_type, payload_key, priority) VALUES ('$1', '$2', $3)"
  echo "  SQL $sql"
  docker compose -f "$E2E_DIR/docker-compose.yml" exec -T postgres \
    psql -U e2e -d cineseek -v ON_ERROR_STOP=1 -q -c "$sql" >/dev/null || fail "SQL 실행 실패: $sql"
}
