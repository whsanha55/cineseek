package com.whsanha55.cineseek.search.vo

/** Qdrant payload — 필터 대상 사본 (pipeline.build_payload 이식). 표시 필드는 PG에서 조립하므로 ID만 담는다 */
data class MoviePayload(
    val title: String?, // Qdrant 대시보드 디버깅용
    val releaseYear: Int?,
    val rating: Double?,
    val genreIds: List<Long>,
    val directorIds: List<Long>, // 전체
    val castIds: List<Long>, // 전체 — PG movie_cast와 같은 출연 관계를 반영한다
    /** 러닝타임(분) */
    val runtime: Int? = null,
    /** 숨은 명작 걸러내기용 투표 수 */
    val voteCount: Int? = null,
)
