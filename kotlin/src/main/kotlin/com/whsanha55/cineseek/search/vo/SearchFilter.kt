package com.whsanha55.cineseek.search.vo

/** 검색 조건 — URL 쿼리 파라미터와 1:1 */
data class SearchFilter(
    val genreIds: List<Long> = emptyList(), // OR
    val yearMin: Int? = null,
    val yearMax: Int? = null,
    val ratingMin: Double? = null,
    val runtimeMin: Int? = null,
    val runtimeMax: Int? = null,
    val voteCountMin: Int? = null,
    val voteCountMax: Int? = null,
    val directorId: Long? = null,
    val castId: Long? = null,
    val limit: Int = 20,
    val offset: Int = 0,
)
