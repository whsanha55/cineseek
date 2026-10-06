package com.whsanha55.cineseek.movie.service

/**
 * 사용자 조회에 따른 배우 필모그래피 우선 갱신 요청 — movie 도메인이 선언하고 collect 도메인이 구현한다.
 * 의존 방향을 collect → movie 한쪽으로 유지하기 위한 포트(의존 반전).
 */
interface FilmographyRefreshRequester {

    /** 해당 배우의 필모그래피 갱신 작업을 우선순위로 요청한다 — 멱등(이미 대기 중이면 무시) */
    fun requestRefresh(personId: Long)

    /** 해당 배우의 수집 작업이 대기/실행 중인지 — API 응답의 collecting 표시용 */
    fun hasPendingWork(personId: Long): Boolean
}
