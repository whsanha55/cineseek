package com.whsanha55.cineseek.search.dto

import com.whsanha55.cineseek.search.vo.SearchFilter
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank

/** GET /cineseek/search 쿼리 파라미터 */
data class SearchRequest(
    @field:Schema(description = "검색어 (줄거리 묘사)", example = "감옥에서 탈출하는 이야기")
    @field:NotBlank
    val q: String,

    @field:Schema(description = "장르 id (반복 파라미터, OR)", example = "[80]")
    val genreId: List<Long> = emptyList(),

    @field:Schema(description = "개봉 연도 하한", example = "2000")
    val yearMin: Int? = null,

    @field:Schema(description = "개봉 연도 상한", example = "2020")
    val yearMax: Int? = null,

    @field:Schema(description = "평점 하한 (0~10)", example = "7.5")
    val ratingMin: Double? = null,

    @field:Schema(description = "러닝타임 하한 (분)", example = "90")
    @field:Min(0)
    @field:Max(MAX_RUNTIME)
    val runtimeMin: Int? = null,

    @field:Schema(description = "러닝타임 상한 (분)", example = "150")
    @field:Min(0)
    @field:Max(MAX_RUNTIME)
    val runtimeMax: Int? = null,

    @field:Schema(description = "투표 수 하한 — 숨은 명작 탐색용", example = "50")
    @field:Min(0)
    @field:Max(MAX_VOTE_COUNT)
    val voteCountMin: Int? = null,

    @field:Schema(description = "투표 수 상한 — 숨은 명작 탐색용", example = "500")
    @field:Min(0)
    @field:Max(MAX_VOTE_COUNT)
    val voteCountMax: Int? = null,

    @field:Schema(description = "감독 person id", example = "525")
    val directorId: Long? = null,

    @field:Schema(description = "배우 person id", example = "3895")
    val castId: Long? = null,

    @field:Schema(description = "결과 수 (1~50)", example = "20")
    @field:Min(1)
    @field:Max(MAX_LIMIT)
    val limit: Int = DEFAULT_LIMIT,

    @field:Schema(description = "건너뛸 결과 수", example = "0")
    @field:Min(0)
    val offset: Int = 0,
) {
    fun toFilter() = SearchFilter(
        genreIds = genreId,
        yearMin = yearMin,
        yearMax = yearMax,
        ratingMin = ratingMin,
        runtimeMin = runtimeMin,
        runtimeMax = runtimeMax,
        voteCountMin = voteCountMin,
        voteCountMax = voteCountMax,
        directorId = directorId,
        castId = castId,
        limit = limit,
        offset = offset,
    )

    companion object {
        const val DEFAULT_LIMIT = 20
        const val MAX_LIMIT = 50L
        const val MAX_RUNTIME = 1000L
        const val MAX_VOTE_COUNT = 1_000_000L
    }
}

/** GET /cineseek/movies/{id}/similar 쿼리 파라미터 */
data class SimilarRequest(
    @field:Schema(description = "결과 수 (1~20)", example = "8")
    @field:Min(1)
    @field:Max(MAX_LIMIT)
    val limit: Int = DEFAULT_LIMIT,
) {
    companion object {
        const val DEFAULT_LIMIT = 8
        const val MAX_LIMIT = 20L
    }
}
