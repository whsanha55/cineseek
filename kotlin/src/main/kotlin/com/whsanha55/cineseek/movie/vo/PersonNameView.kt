package com.whsanha55.cineseek.movie.vo

/** 사람 자동완성용 조회 프로젝션 — movie_director / movie_cast 검색이 공유한다 */
interface PersonNameView {
    val personId: Long
    val name: String
}
