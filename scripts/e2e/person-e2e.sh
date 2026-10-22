#!/usr/bin/env bash
# 인물 API E2E — 자동완성, 인물 조회, 필모그래피
set -euo pipefail
source "$(dirname "$0")/lib.sh"

KEY=$(unique_key)
PERSON_ID=$((KEY + 1))
DIRECTOR_ID=$((KEY + 2))
TMDB_ID=$((KEY + 3))
ACTOR="E2E배우$KEY"
DIRECTOR="E2E감독$KEY"
TITLE="사막의 우체부 $KEY"
OVERVIEW="모래바람이 부는 사막 마을에 한 달에 한 번 편지를 배달하는 우체부가 낙타와 함께 길을 나선다."
UNKNOWN_ID=999999999999

step "데이터 준비 — TMDB 스텁 등록 후 인물 필모그래피 수집 작업 등록 (personId=$PERSON_ID)"
stub_tmdb "/person/$PERSON_ID" "$(jq -n --argjson id "$PERSON_ID" --arg name "$ACTOR" \
  '{id: $id, name: $name, profile_path: "/e2e-profile.jpg"}')"
stub_tmdb "/person/$PERSON_ID/movie_credits" "$(jq -n --argjson id "$TMDB_ID" --arg title "$TITLE" \
  '{cast: [{id: $id, title: $title, character: "우체부", order: 0, release_date: "2024-05-01"}]}')"
stub_movie "$TMDB_ID" "$TITLE" "$OVERVIEW" "$DIRECTOR_ID" "$DIRECTOR" "$PERSON_ID" "$ACTOR" "우체부"
# 필모그래피 수집이 DB에 없는 출연작을 영화 상세 수집 작업으로 등록한다 — 영화는 따로 넣지 않는다
enqueue_task PERSON_FILMO "person:$PERSON_ID" 100

step "수집 완료 대기 — 필모그래피에 출연작이 나타날 때까지"
wait_for "필모그래피 수집 완료" 120 '.items | length == 1' "/cineseek/people/$PERSON_ID/filmography"
MOVIE_ID=$(json '.items[0].movieId')

step "GET /cineseek/people/{id}/filmography — 배우 필모그래피"
get "/cineseek/people/$PERSON_ID/filmography" sort=rating
expect_status 200
expect_eq .person.personId "$PERSON_ID"
expect_eq .person.name "$ACTOR"
expect_eq .person.profilePath /e2e-profile.jpg
expect_eq .person.totalWorks 1
expect_eq '.items[0].title' "$TITLE"
expect_eq '.items[0].character' "우체부"
expect_eq .collecting false

get "/cineseek/people/$PERSON_ID/filmography" excludeMovieId="$MOVIE_ID"
expect_status 200
expect_eq '.items | length' 0

get "/cineseek/people/$UNKNOWN_ID/filmography"
expect_error 404 PERSON_NOT_FOUND

get "/cineseek/people/$PERSON_ID/filmography" sort=popular
expect_error 400 INVALID_REQUEST
expect_eq '.errors[0].field' sort

step "GET /cineseek/people — 감독·배우 자동완성"
get /cineseek/people q="$ACTOR" role=cast
expect_status 200
expect_eq '.items | length' 1
expect_eq '.items[0].personId' "$PERSON_ID"
expect_eq '.items[0].role' cast
expect_eq '.items[0].knownFor[0]' "$TITLE"

get /cineseek/people q="$DIRECTOR"
expect_status 200
expect_eq '.items[0].personId' "$DIRECTOR_ID"
expect_eq '.items[0].role' director

get /cineseek/people q=" "
expect_error 400 INVALID_REQUEST
expect_eq '.errors[0].field' q

get /cineseek/people q="$ACTOR" role=writer
expect_error 400 INVALID_REQUEST
expect_eq '.errors[0].field' role

step "GET /cineseek/people/{id} — 인물 이름 조회"
get "/cineseek/people/$PERSON_ID" role=cast
expect_status 200
expect_eq .name "$ACTOR"
expect_eq '.knownFor[0]' "$TITLE"

get "/cineseek/people/$DIRECTOR_ID"
expect_status 200
expect_eq .name "$DIRECTOR"
expect_eq .role director

get "/cineseek/people/$PERSON_ID" role=director
expect_error 404 PERSON_NOT_FOUND

get "/cineseek/people/$PERSON_ID" role=writer
expect_error 400 INVALID_REQUEST
