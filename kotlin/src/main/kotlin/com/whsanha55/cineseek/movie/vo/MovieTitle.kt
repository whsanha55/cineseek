package com.whsanha55.cineseek.movie.vo

/** 제목 매칭용 경량 조회 결과 — 영화 id와 한국어 제목, 원제 */
data class MovieTitle(val movieId: Long, val title: String, val originalTitle: String?)
