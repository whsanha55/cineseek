package com.whsanha55.cineseek.movie.dto

import com.whsanha55.cineseek.movie.enums.ExploreSortEnum
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern

/** GET /cineseek/movies 쿼리 파라미터 — 탐색 목록 */
data class MovieExploreRequest(
    @field:Schema(description = "장르 id", example = "28")
    val genreId: Long? = null,

    @field:Schema(description = "정렬 (rating | release | vote_count)", example = "rating")
    @field:Pattern(regexp = "rating|release|vote_count")
    val sort: String = "rating",

    @field:Schema(description = "페이지 (0부터)", example = "0")
    @field:Min(0)
    val page: Int = 0,

    @field:Schema(description = "페이지 크기 (1~50)", example = "20")
    @field:Min(1)
    @field:Max(MAX_LIMIT)
    val limit: Int = DEFAULT_LIMIT,

    @field:Schema(description = "감독 person id", example = "525")
    val directorId: Long? = null,

    @field:Schema(description = "배우 person id", example = "3895")
    val castId: Long? = null,
) {
    fun sortEnum() = ExploreSortEnum.fromParam(sort)

    companion object {
        const val DEFAULT_LIMIT = 20
        const val MAX_LIMIT = 50L
    }
}

/** GET /cineseek/people 쿼리 파라미터 — 감독·배우 자동완성 */
data class PeopleRequest(
    @field:Schema(description = "이름 접두어", example = "봉")
    @field:NotBlank
    val q: String,

    @field:Schema(description = "역할 (director | cast)", example = "director")
    @field:Pattern(regexp = ROLE_PATTERN)
    val role: String = "director",
) {
    companion object {
        const val ROLE_PATTERN = "director|cast"
    }
}

/** GET /cineseek/people/{id} 쿼리 파라미터 */
data class PersonRequest(
    @field:Schema(description = "역할 (director | cast)", example = "director")
    @field:Pattern(regexp = PeopleRequest.ROLE_PATTERN)
    val role: String = "director",
)
