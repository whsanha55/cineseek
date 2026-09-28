package com.whsanha55.cineseek.search.dto

import com.whsanha55.cineseek.search.vo.SearchResult

data class SearchResultResponse(
    val title: String?,
    val releaseYear: Long?,
    val rating: Double?,
    val score: Float,
    val genres: List<String>,
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
