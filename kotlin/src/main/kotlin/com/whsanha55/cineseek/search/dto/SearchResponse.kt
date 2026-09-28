package com.whsanha55.cineseek.search.dto

import com.whsanha55.cineseek.search.vo.SearchResult

data class SearchResponse(
    val query: String,
    val filter: SearchFilter,
    val count: Int,
    val results: List<SearchResultResponse>,
) {
    data class SearchFilter(val genre: String?, val yearMin: Int?)

    companion object {
        fun from(request: SearchRequest, results: List<SearchResult>) = SearchResponse(
            query = request.q,
            filter = SearchFilter(genre = request.genre, yearMin = request.yearMin),
            count = results.size,
            results = results.map(SearchResultResponse::from),
        )
    }
}
