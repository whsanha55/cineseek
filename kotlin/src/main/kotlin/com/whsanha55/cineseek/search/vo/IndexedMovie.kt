package com.whsanha55.cineseek.search.vo

/** 색인 단위 입력 — PG(SoT)에서 만든다 */
data class IndexedMovie(val movieId: Long, val overview: String, val payload: MoviePayload)
