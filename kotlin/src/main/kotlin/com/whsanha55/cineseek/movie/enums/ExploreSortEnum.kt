package com.whsanha55.cineseek.movie.enums

/** 탐색 정렬 — 기준을 라벨 그대로 매핑한다 ("인기순" 같은 모호한 이름 금지) */
enum class ExploreSortEnum(val param: String) {
    RATING("rating"),
    RELEASE("release"),
    VOTE_COUNT("vote_count"),
    ;

    companion object {
        fun fromParam(param: String) = entries.first { it.param == param }
    }
}
