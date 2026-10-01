package com.whsanha55.cineseek.search.vo

/** Qdrant payload — 필터 대상 사본 (pipeline.build_payload 이식). 표시 필드는 PG에서 조립하므로 ID만 담는다 */
data class MoviePayload(
    val title: String?, // Qdrant 대시보드 디버깅용
    val releaseYear: Int?,
    val rating: Double?,
    val genreIds: List<Long>,
    val directorIds: List<Long>, // 상위 3명
    val castIds: List<Long>, // 상위 5명
)
