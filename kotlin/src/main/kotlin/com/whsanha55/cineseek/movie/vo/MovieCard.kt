package com.whsanha55.cineseek.movie.vo

/** 카드 공통 장르 조각 */
data class GenreItem(val genreId: Long, val name: String)

/** 검색·유사·탐색이 공유하는 카드 — 표시 필드는 항상 PG(SoT)에서 만든다 */
data class MovieCard(
    val movieId: Long,
    val title: String,
    val originalTitle: String?,
    val releaseYear: Int?,
    val rating: Double?,
    val voteCount: Int?,
    val posterPath: String?,
    val genres: List<GenreItem>,
)
