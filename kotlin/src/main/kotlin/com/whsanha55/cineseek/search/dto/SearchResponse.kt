package com.whsanha55.cineseek.search.dto

import com.whsanha55.cineseek.search.vo.SearchResult

data class SearchFilter(val genre: String?, val yearMin: Int?)

data class SearchResponse(val query: String, val filter: SearchFilter, val count: Int, val results: List<SearchResult>)
