package com.whsanha55.cineseek.movie.vo

/** 사람 요약 — 자동완성·칩 라벨·상세 감독 */
data class PersonItem(val personId: Long, val name: String, val knownFor: List<String>)

data class CastMember(val personId: Long, val name: String, val character: String?)

data class MovieDetail(
    val card: MovieCard,
    val runtime: Int?,
    val overview: String?,
    val directors: List<PersonItem>,
    val cast: List<CastMember>,
)
