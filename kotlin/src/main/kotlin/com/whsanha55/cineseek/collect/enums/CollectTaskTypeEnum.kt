package com.whsanha55.cineseek.collect.enums

/**
 * 수집 작업 종류 — payload_key와 함께 (task_type, payload_key) 멱등키를 만든다
 * GLOBAL_DISCOVER/KR_DISCOVER는 TMDB discover 페이징, MOVIE_DETAIL은 영화 상세,
 * PERSON_FILMO는 인물 필모그래피 수집
 */
enum class CollectTaskTypeEnum {
    GLOBAL_DISCOVER,
    KR_DISCOVER,
    MOVIE_DETAIL,
    PERSON_FILMO,
}
