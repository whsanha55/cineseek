package com.whsanha55.cineseek.movie.vo

/** 탐색 목록 + 페이지 메타 — total 없이 hasNext로 "더 보기"를 판단한다 */
data class MoviePage(val items: List<MovieCard>, val limit: Int, val offset: Int, val hasNext: Boolean)
