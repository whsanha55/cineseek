package com.whsanha55.cineseek.search.dto

import com.whsanha55.cineseek.search.vo.SearchResult
import io.swagger.v3.oas.annotations.media.Schema

data class SearchResultResponse(
    @field:Schema(description = "제목", example = "쇼생크 탈출")
    val title: String?,

    @field:Schema(description = "개봉 연도", example = "1994")
    val releaseYear: Long?,

    @field:Schema(description = "TMDB 평점 (0~10)", example = "8.7")
    val rating: Double?,

    @field:Schema(description = "RRF 점수", example = "0.9")
    val score: Float,

    @field:Schema(description = "장르", example = "[\"범죄\", \"드라마\"]")
    val genres: List<String>,

    @field:Schema(description = "감독 (상위 3명)", example = "[\"프랭크 다라본트\"]")
    val directors: List<String>,
) {
    companion object {
        fun from(result: SearchResult) = SearchResultResponse(
            title = result.title,
            releaseYear = result.releaseYear,
            rating = result.rating,
            score = result.score,
            genres = result.genres,
            directors = result.directors,
        )
    }
}
