package com.whsanha55.cineseek.search.dto

import com.whsanha55.cineseek.search.vo.SearchResult
import io.swagger.v3.oas.annotations.media.Schema

data class SearchResponse(
    @field:Schema(description = "검색어", example = "감옥에서 탈출하는 이야기")
    val query: String,

    @field:Schema(description = "적용한 필터")
    val filter: SearchFilter,

    @field:Schema(description = "결과 수", example = "5")
    val count: Int,

    @field:Schema(description = "검색 결과 (RRF 점수 내림차순)")
    val results: List<SearchResultResponse>,
) {
    data class SearchFilter(
        @field:Schema(description = "장르 필터", example = "범죄")
        val genre: String?,

        @field:Schema(description = "개봉 연도 하한", example = "2000")
        val yearMin: Int?,
    )

    companion object {
        fun from(request: SearchRequest, results: List<SearchResult>) = SearchResponse(
            query = request.q,
            filter = SearchFilter(genre = request.genre, yearMin = request.yearMin),
            count = results.size,
            results = results.map(SearchResultResponse::from),
        )
    }
}
