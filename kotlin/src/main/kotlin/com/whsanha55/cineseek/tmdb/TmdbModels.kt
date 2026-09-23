package com.whsanha55.cineseek.tmdb

import java.math.BigDecimal

/** TMDB detail(ko-KR) + credits — upsert에 필요한 것만 담은 도메인 모델 */
data class TmdbMovie(
	val tmdbId: Long,
	val title: String,
	val originalTitle: String?,
	val overview: String,
	val releaseDate: String?, // "2008-07-16" — 없으면 null
	val runtime: Int?,
	val voteAverage: BigDecimal?,
	val voteCount: Int?,
	val posterPath: String?,
	val backdropPath: String?,
	val originalLanguage: String?,
	val genres: List<TmdbGenre>,
	val directors: List<TmdbPerson>,
	val cast: List<TmdbCastMember>,
)

data class TmdbGenre(
	val genreId: Long,
	val name: String, // ko-KR 응답이라 한국어 — python이 name 컬럼에 그대로 넣던 값과 동일
)

data class TmdbPerson(
	val personId: Long,
	val name: String,
)

data class TmdbCastMember(
	val personId: Long,
	val name: String,
	val character: String?,
	val castOrder: Int?,
)
