package com.whsanha55.cineseek.movie.vo

/**
 * 분위기 태그 후보 어휘 — 태깅 프롬프트(MovieMoodTagger)와 검색의 쿼리→태그 매칭(MoodTagMatcher)이 함께 쓴다.
 * 순서·표기를 바꾸면 프롬프트 해시(태깅 버전)가 바뀌어 전체 영화가 다시 태깅된다
 */
object MoodVocabulary {
    val CANDIDATE_TAGS: List<String> = listOf(
        "잔잔한", "서정적", "긴장감 있는", "유쾌한", "어두운",
        "감동적인", "소름 돋는", "몽환적인", "힐링", "처절한",
        "잔혹한", "로맨틱", "냉소적", "희망적", "애절한",
        "코미디", "미스터리", "누아르", "판타지", "아늑한",
        "슬픈", "뜨거운", "차가운", "아련한", "절망적인",
    )
}
