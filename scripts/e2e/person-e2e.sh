#!/usr/bin/env bash
# 인물 API E2E — 자동완성, 인물 조회, 필모그래피(상세·번역 포함, 첫 방문 수집)
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
BIO_EN="E2E actor ${KEY} is a South Korean actor known for desert road movies."
BIO_KO="E2E 배우 ${KEY}는 사막 로드무비로 알려진 대한민국 배우다."
# 첫 방문 배우 — 영화 출연진(movie_cast)에만 있고 인물(person) 정보는 없다. 인물 페이지를 처음 열면 수집이 시작된다
FIRST_ID=$((KEY + 4))
FIRST_TMDB_ID=$((KEY + 5))
FIRST_ACTOR="E2E첫방문배우$KEY"
FIRST_TITLE="겨울 정원 $KEY"

step "데이터 준비 — TMDB 스텁 등록 후 인물 필모그래피 수집 작업 등록 (personId=$PERSON_ID)"
# 인물 상세 — 한국어 소개는 비어 있고 영어 소개·영어 이름만 있다 (번역과 slug 대상)
stub_tmdb "/person/$PERSON_ID" "$(jq -n --argjson id "$PERSON_ID" --arg name "$ACTOR" --arg key "$KEY" --arg bio "$BIO_EN" '{
  id: $id, name: $name, profile_path: "/e2e-profile.jpg",
  birthday: "1967-01-17", deathday: null, place_of_birth: "Gimhae, South Gyeongsang, South Korea",
  biography: "", homepage: "https://example.com/e2e",
  external_ids: {imdb_id: "nm0814280", instagram_id: null},
  translations: {translations: [{iso_639_1: "en", data: {name: ("E2E Actor " + $key), biography: $bio}}]}
}')"
stub_translation "$BIO_EN" "대한민국 경상남도 김해시" "$BIO_KO"
stub_tmdb "/person/$PERSON_ID/movie_credits" "$(jq -n --argjson id "$TMDB_ID" --arg title "$TITLE" \
  '{cast: [{id: $id, title: $title, character: "우체부", order: 0, release_date: "2024-05-01"}]}')"
stub_movie "$TMDB_ID" "$TITLE" "$OVERVIEW" "$DIRECTOR_ID" "$DIRECTOR" "$PERSON_ID" "$ACTOR" "우체부"
# 필모그래피 수집이 DB에 없는 출연작을 영화 상세 수집 작업으로 등록한다 — 영화는 따로 넣지 않는다
enqueue_task PERSON_FILMO "person:$PERSON_ID" 100

step "데이터 준비 — 첫 방문 배우가 출연한 영화만 수집 (personId=$FIRST_ID, 인물 정보 없음)"
stub_movie "$FIRST_TMDB_ID" "$FIRST_TITLE" "눈 덮인 정원을 가꾸는 노부부가 봄을 기다리며 지난 계절을 돌아본다." \
  "$DIRECTOR_ID" "$DIRECTOR" "$FIRST_ID" "$FIRST_ACTOR" "정원사"
# 인물 페이지 첫 방문이 등록한 필모그래피 수집이 쓴다. 한국어 소개가 있고 출생지가 없어 번역 대상이 아니다
stub_tmdb "/person/$FIRST_ID" "$(jq -n --argjson id "$FIRST_ID" --arg name "$FIRST_ACTOR" '{
  id: $id, name: $name, profile_path: "/e2e-first-profile.jpg", biography: "겨울 영화에 자주 나오는 배우다.",
  translations: {translations: []}
}')"
stub_tmdb "/person/$FIRST_ID/movie_credits" "$(jq -n --argjson id "$FIRST_TMDB_ID" --arg title "$FIRST_TITLE" \
  '{cast: [{id: $id, title: $title, character: "정원사", order: 0, release_date: "2024-05-01"}]}')"
enqueue_task MOVIE_DETAIL "movie:tmdb:$FIRST_TMDB_ID" 0

step "수집 완료 대기 — 필모그래피에 출연작이 나타나고 출생지·소개 번역이 끝날 때까지"
wait_for "필모그래피 수집 완료" 120 '.items | length == 1' "/cineseek/people/$PERSON_ID/filmography"
wait_for "출생지·소개 번역 완료" 60 '.person.biographyKo != null' "/cineseek/people/$PERSON_ID/filmography"
MOVIE_ID=$(json '.items[0].movieId')

step "GET /cineseek/people/{id}/filmography — 배우 필모그래피"
get "/cineseek/people/$PERSON_ID/filmography" sort=rating
expect_status 200
expect_eq .person.personId "$PERSON_ID"
expect_eq .person.name "$ACTOR"
expect_eq .person.profilePath /e2e-profile.jpg
expect_eq .person.totalWorks 1
expect_eq .person.slug "e2e-actor-$KEY"
expect_eq .person.birthday 1967-01-17
expect_eq .person.deathday null
expect_eq .person.placeOfBirthEn "Gimhae, South Gyeongsang, South Korea"
expect_eq .person.placeOfBirthKo "대한민국 경상남도 김해시"
expect_eq .person.biographyEn "$BIO_EN"
expect_eq .person.biographyKo "$BIO_KO"
expect_eq .person.biographyKoTranslated true
expect_eq .person.imdbId nm0814280
expect_eq .person.instagramId null
expect_eq .person.homepage https://example.com/e2e
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

step "GET /cineseek/people/{id}/filmography — 첫 방문 배우는 출연진 이름으로 바로 응답하고 수집을 시작한다"
wait_for "첫 방문 배우 출연 영화 수집 완료" 120 '.items | length == 1' /cineseek/movies castId="$FIRST_ID" sort=release
get "/cineseek/people/$FIRST_ID/filmography"
expect_status 200
expect_eq .person.name "$FIRST_ACTOR"
expect_eq .person.profilePath null
expect_eq .person.filmoState NONE
expect_eq '.items[0].title' "$FIRST_TITLE"
expect_eq .collecting true

wait_for "첫 방문 배우 필모그래피 수집 완료" 60 '.collecting == false' "/cineseek/people/$FIRST_ID/filmography"
expect_eq .person.profilePath /e2e-first-profile.jpg
expect_eq .person.filmoState COMPLETE
expect_eq .person.biographyKo "겨울 영화에 자주 나오는 배우다."
expect_eq .person.totalWorks 1

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
