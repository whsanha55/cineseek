package com.whsanha55.cineseek.search.vo

import com.whsanha55.cineseek.movie.vo.MovieCard

/** Qdrant 검색 결과 — (movie_id, score)만. 표시 필드는 PG에서 조립한다 */
data class SearchHit(val movieId: Long, val score: Float)

/** 검색 페이지 — total은 미제공, hasNext로 "더 보기"를 판단한다 */
data class SearchPage(val hits: List<SearchHit>, val limit: Int, val offset: Int, val hasNext: Boolean)

/** 검색·유사 카드 — score는 원본 점수 (등급은 프론트가 계산) */
data class ScoredMovieCard(val card: MovieCard, val score: Double)

/** 카드로 조립된 검색 결과 + 페이지 메타 */
data class SearchItems(val items: List<ScoredMovieCard>, val limit: Int, val offset: Int, val hasNext: Boolean)
