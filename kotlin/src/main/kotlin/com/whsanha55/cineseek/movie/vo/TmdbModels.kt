package com.whsanha55.cineseek.movie.vo

import java.math.BigDecimal
import java.time.LocalDate

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
    val originCountry: String? = null, // production_countries[0] ISO 코드 (예: "KR") — 없으면 null
    val genres: List<TmdbGenre>,
    val directors: List<TmdbPerson>,
    val cast: List<TmdbCastMember>,
) {
    fun releaseLocalDate(): LocalDate? = releaseDate?.takeIf { it.isNotBlank() }?.let(LocalDate::parse)

    fun releaseYear(): Int? = releaseDate?.take(YEAR_LENGTH)?.toIntOrNull()

    companion object {
        private const val YEAR_LENGTH = 4
    }
}

data class TmdbGenre(
    val genreId: Long,
    val name: String, // ko-KR 응답이라 한국어 — python이 name 컬럼에 그대로 넣던 값과 동일
)

data class TmdbPerson(val personId: Long, val name: String)

data class TmdbCastMember(val personId: Long, val name: String, val character: String?, val castOrder: Int?)

/** TMDB discover 순회 결과 — 체크포인트 재개에 필요한 페이지 정보까지 담는다 */
data class TmdbDiscoverResult(
    val tmdbIds: List<Long>,
    val lastPage: Int, // 성공적으로 소비한 마지막 페이지 — 다음 실행의 startPage 체크포인트
    val totalPages: Int, // TMDB가 알려준 total_pages (응답에 없으면 0)
)

/** TMDB person 상세(ko-KR) */
data class TmdbPersonInfo(val personId: Long, val name: String, val profilePath: String?)

/** TMDB person movie_credits의 cast 항목 — 인물 필모그래피 */
data class TmdbPersonCredit(
    val tmdbId: Long,
    val title: String?,
    val character: String?,
    val order: Int?,
    val releaseDate: String?, // "2008-07-16" — 없으면 null
)
