#!/usr/bin/env bash
# 검색 API E2E — 자연어 검색, 유사 영화, 취향 추천
# 세 영화를 같은 감독으로 만들어 감독 필터로 이 스크립트의 영화만 보고 순위를 검증한다.
# A·B는 우주 조난 이야기, C는 요리 이야기다
set -euo pipefail
source "$(dirname "$0")/lib.sh"

KEY=$(unique_key)
DIRECTOR_ID=$((KEY + 1))
CAST_ID=$((KEY + 2))
TMDB_A=$((KEY + 11))
TMDB_B=$((KEY + 12))
TMDB_C=$((KEY + 13))
TITLE_A="궤도의 끝 $KEY"
TITLE_B="무중력 귀환 $KEY"
TITLE_C="할머니의 부엌 $KEY"
OVERVIEW_A="고장 난 우주 정거장에 홀로 남은 우주비행사가 산소가 바닥나기 전에 지구로 귀환하려고 사투를 벌인다."
OVERVIEW_B="운석에 부서진 우주선에서 살아남은 우주비행사들이 무중력 속에서 지구로 돌아가기 위해 힘을 합친다."
OVERVIEW_C="시골 마을의 할머니가 손주에게 김치 담그는 법과 집밥 요리 비법을 차근차근 가르쳐 준다."
MOOD_TAG="반딧불이"   # A의 분위기 태그 — 줄거리에 없는 단어라 검색에 걸리면 태깅이 색인에 반영된 것이다
UNKNOWN_ID=999999999999

step "데이터 준비 — TMDB·LLM 스텁 등록 후 영화 상세 수집 작업 3건 등록"
stub_movie "$TMDB_A" "$TITLE_A" "$OVERVIEW_A" "$DIRECTOR_ID" "E2E감독$KEY" "$CAST_ID" "E2E배우$KEY" "비행사"
stub_movie "$TMDB_B" "$TITLE_B" "$OVERVIEW_B" "$DIRECTOR_ID" "E2E감독$KEY" "$CAST_ID" "E2E배우$KEY" "선장"
stub_movie "$TMDB_C" "$TITLE_C" "$OVERVIEW_C" "$DIRECTOR_ID" "E2E감독$KEY" "$CAST_ID" "E2E배우$KEY" "할머니"
stub_mood "$TITLE_A" '["'"$MOOD_TAG"'", "긴장감 있는", "고독한"]' "$MOOD_TAG 불빛처럼 외로운 생존기"
enqueue_task MOVIE_DETAIL "movie:tmdb:$TMDB_A" 0
enqueue_task MOVIE_DETAIL "movie:tmdb:$TMDB_B" 0
enqueue_task MOVIE_DETAIL "movie:tmdb:$TMDB_C" 0

step "수집 완료 대기 — 감독 필터 탐색에 3편이 나타날 때까지"
wait_for "영화 3편 수집 완료" 120 '.items | length == 3' /cineseek/movies directorId="$DIRECTOR_ID" sort=release
movie_id_of() {
  json ".items[] | select(.title == \"$1\") | .movieId"
}
A=$(movie_id_of "$TITLE_A")
B=$(movie_id_of "$TITLE_B")
C=$(movie_id_of "$TITLE_C")
echo "  movieId A=$A B=$B C=$C"

step "색인 완료 대기 — 감독 필터 검색에 3편이 나올 때까지 (태깅 → 임베딩 → Qdrant)"
wait_for "영화 3편 색인 완료" 180 '.items | length == 3' /cineseek/search q="우주비행사" directorId="$DIRECTOR_ID"

step "GET /cineseek/search — 자연어 검색"
get /cineseek/search q="고장 난 우주 정거장에 홀로 남은 우주비행사" directorId="$DIRECTOR_ID"
expect_status 200
expect_eq '.items[0].movieId' "$A"
expect_true '.items[0].matchedTerms | length > 0' "1위 결과에 매칭 단어가 있다"
expect_eq '.page.limit' 20
expect_eq '.page.offset' 0

get /cineseek/search q="$MOOD_TAG" directorId="$DIRECTOR_ID"
expect_status 200
expect_eq '.items[0].movieId' "$A"
# 매칭 단어는 sparse 토큰이 덮은 구간이라 "반딧불"까지만 나올 수 있다
expect_true '.items[0].matchedTerms | any(contains("반딧불"))' "LLM 분위기 태그가 색인에 반영됐다"

get /cineseek/search q="고장 난 우주 정거장에 홀로 남은 우주비행사" limit=50
expect_status 200
expect_true "[.items[].movieId] | index($A) != null" "필터 없이도 검색된다"

get /cineseek/search q="우주비행사" directorId="$DIRECTOR_ID" excludeGenreId=18
expect_status 200
expect_eq '.items | length' 0

get /cineseek/search q=" "
expect_error 400 INVALID_REQUEST
expect_eq '.errors[0].field' q

get /cineseek/search q="우주" limit=51
expect_error 400 INVALID_REQUEST
expect_eq '.errors[0].field' limit

step "GET /cineseek/movies/{id}/similar — 유사 영화"
get "/cineseek/movies/$A/similar" limit=20
expect_status 200
expect_true "[.items[].movieId] | index($A) == null" "자기 자신은 빠진다"
expect_true "[.items[].movieId] as \$ids | (\$ids | index($B)) != null
  and ((\$ids | index($C)) == null or (\$ids | index($B)) < (\$ids | index($C)))" "우주 이야기 B가 요리 이야기 C보다 앞선다"

get "/cineseek/movies/$UNKNOWN_ID/similar"
expect_status 200
expect_eq '.items | length' 0

get "/cineseek/movies/$A/similar" limit=21
expect_error 400 INVALID_REQUEST
expect_eq '.errors[0].field' limit

step "GET /cineseek/recommendations — 취향 추천"
get /cineseek/recommendations likedId="$A" dislikedId="$C" limit=50
expect_status 200
expect_true "[.items[].movieId] | index($B) != null" "찜한 A와 비슷한 B가 추천된다"
expect_true "[.items[].movieId] | (index($A) == null and index($C) == null)" "찜·관심없음 영화는 빠진다"

get /cineseek/recommendations likedId="$A" watchedId="$B" limit=50
expect_status 200
expect_true "[.items[].movieId] | index($B) == null" "봤어요 영화는 빠진다"

get /cineseek/recommendations likedId="$UNKNOWN_ID"
expect_status 200
expect_eq '.items | length' 0

get /cineseek/recommendations
expect_error 400 INVALID_REQUEST
expect_eq '.errors[0].field' likedId

get /cineseek/recommendations likedId="$A" limit=0
expect_error 400 INVALID_REQUEST
expect_eq '.errors[0].field' limit
