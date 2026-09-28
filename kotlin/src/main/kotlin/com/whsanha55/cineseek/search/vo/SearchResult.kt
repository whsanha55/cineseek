package com.whsanha55.cineseek.search.vo

/** 검색 결과 — Qdrant payload 사본 기반 */
data class SearchResult(
    val title: String?,
    val releaseYear: Long?,
    val rating: Double?,
    val score: Float,
    val genres: List<String> = emptyList(),
    val directors: List<String> = emptyList(),
)
