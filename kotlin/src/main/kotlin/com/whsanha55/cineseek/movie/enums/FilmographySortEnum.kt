package com.whsanha55.cineseek.movie.enums

/** 필모그래피 정렬 — 기본은 최신 개봉순. 탐색(explore)과 달리 투표 수 하한을 적용하지 않는다 */
enum class FilmographySortEnum(val param: String) {
    RELEASE("release"),
    RATING("rating"),
    ;

    companion object {
        fun fromParam(param: String) = entries.first { it.param == param }
    }
}
