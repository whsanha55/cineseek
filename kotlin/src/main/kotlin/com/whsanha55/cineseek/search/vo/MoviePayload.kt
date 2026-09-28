package com.whsanha55.cineseek.search.vo

/** Qdrant payload — 필터 대상 사본 (pipeline.build_payload 이식) */
data class MoviePayload(
    val title: String?,
    val releaseYear: Int?,
    val rating: Double?,
    val genres: List<String>,
    val directors: List<String>, // 상위 3명
    val cast: List<String>, // 상위 5명
)
