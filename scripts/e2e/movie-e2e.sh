#!/usr/bin/env bash
# 영화 API E2E — 상세, 시청처, 탐색, 장르
set -euo pipefail
source "$(dirname "$0")/lib.sh"

KEY=$(unique_key)
TMDB_ID=$((KEY + 1))
DIRECTOR_ID=$((KEY + 2))
CAST_ID=$((KEY + 3))
TITLE="외딴섬 등대 $KEY"
OVERVIEW="외딴섬의 낡은 등대를 지키는 노인이 폭풍우 치는 밤마다 길 잃은 배에 불빛을 비추며 살아간다."
UNKNOWN_ID=999999999999

step "데이터 준비 — TMDB 스텁 등록 후 영화 상세 수집 작업 등록 (tmdbId=$TMDB_ID)"
stub_movie "$TMDB_ID" "$TITLE" "$OVERVIEW" "$DIRECTOR_ID" "E2E감독$KEY" "$CAST_ID" "E2E배우$KEY" "등대지기"
stub_tmdb "/movie/$TMDB_ID/watch/providers" '{
  "id": '"$TMDB_ID"',
  "results": {
    "KR": {
      "link": "https://www.themoviedb.org/movie/'"$TMDB_ID"'/watch?locale=KR",
      "flatrate": [{"provider_id": 8, "provider_name": "Netflix", "logo_path": "/netflix.jpg", "display_priority": 1}],
      "rent": [],
      "buy": [{"provider_id": 3, "provider_name": "Google Play Movies", "logo_path": "/play.jpg", "display_priority": 2}]
    }
  }
}'
enqueue_task MOVIE_DETAIL "movie:tmdb:$TMDB_ID" 0

step "수집 완료 대기 — 감독 필터 탐색에 나타날 때까지"
wait_for "영화 수집 완료" 120 '.items | length == 1' /cineseek/movies directorId="$DIRECTOR_ID" sort=release
MOVIE_ID=$(json '.items[0].movieId')

step "GET /cineseek/movies/{id} — 영화 상세"
get "/cineseek/movies/$MOVIE_ID"
expect_status 200
expect_eq .movieId "$MOVIE_ID"
expect_eq .title "$TITLE"
expect_eq .overview "$OVERVIEW"
expect_eq .releaseYear 2024
expect_eq '.genres[0].id' 18
expect_eq '.directors[0].personId' "$DIRECTOR_ID"
expect_eq '.cast[0].personId' "$CAST_ID"
expect_eq '.cast[0].character' "등대지기"

get "/cineseek/movies/$UNKNOWN_ID"
expect_error 404 MOVIE_NOT_FOUND

get "/cineseek/movies/abc"
expect_error 400 INVALID_REQUEST

step "GET /cineseek/movies/{id}/watch-providers — 시청처 (TMDB 스텁)"
get "/cineseek/movies/$MOVIE_ID/watch-providers"
expect_status 200
expect_eq '.flatrate[0].name' Netflix
expect_eq '.buy[0].providerId' 3
expect_eq '.rent | length' 0
expect_true '.link | startswith("https://www.themoviedb.org/")' "link가 TMDB 안내 페이지"

get "/cineseek/movies/$UNKNOWN_ID/watch-providers"
expect_error 404 MOVIE_NOT_FOUND

step "GET /cineseek/movies — 탐색"
get /cineseek/movies castId="$CAST_ID" sort=rating
expect_status 200
expect_eq '.items | length' 1
expect_eq '.items[0].movieId' "$MOVIE_ID"
expect_eq '.page.hasNext' false

get /cineseek/movies directorId="$DIRECTOR_ID" ratingMin=9
expect_status 200
expect_eq '.items | length' 0

get /cineseek/movies sort=popular
expect_error 400 INVALID_REQUEST
expect_eq '.errors[0].field' sort

get /cineseek/movies limit=0
expect_error 400 INVALID_REQUEST
expect_eq '.errors[0].field' limit

step "GET /cineseek/genres — 장르 목록"
get /cineseek/genres
expect_status 200
expect_true '.items | any(.id == 18 and .name == "드라마")' "수집한 영화의 장르(드라마 18)가 있다"
