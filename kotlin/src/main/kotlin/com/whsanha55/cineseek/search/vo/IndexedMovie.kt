package com.whsanha55.cineseek.search.vo

/** 색인 단위 입력 — PG(SoT)에서 만든다. embeddingInput은 EmbeddingText.assemble 결과 전체다 */
data class IndexedMovie(val movieId: Long, val embeddingInput: String, val payload: MoviePayload)
