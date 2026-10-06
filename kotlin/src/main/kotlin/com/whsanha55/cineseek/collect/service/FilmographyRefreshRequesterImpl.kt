package com.whsanha55.cineseek.collect.service

import com.whsanha55.cineseek.collect.enums.CollectTaskTypeEnum.PERSON_FILMO
import com.whsanha55.cineseek.movie.service.FilmographyRefreshRequester
import org.springframework.stereotype.Component

/**
 * 필모그래피 갱신 포트(movie 도메인이 선언)의 구현 — 사용자 조회를 PERSON_FILMO 작업 등록으로 연결한다.
 * 처음 보는 인물이면 우선순위(PRIORITY_USER_CLICK)로 등록하고, 이미 끝낸 수집이면 되살려 다시 반영한다.
 * 대기(PENDING)/실행(RUNNING) 중이면 멱등하게 무시된다
 */
@Component
class FilmographyRefreshRequesterImpl(private val collectTaskService: CollectTaskService) :
    FilmographyRefreshRequester {

    override fun requestRefresh(personId: Long) {
        val payloadKey = "${CollectPipeline.PERSON_PREFIX}$personId"
        if (collectTaskService.enqueue(PERSON_FILMO, payloadKey, CollectPipeline.PRIORITY_USER_CLICK)) {
            return
        }
        collectTaskService.requeue(PERSON_FILMO, payloadKey) // 이미 있던 작업(DONE)은 되살린다
    }

    override fun hasPendingWork(personId: Long): Boolean =
        collectTaskService.hasPendingWork(PERSON_FILMO, "${CollectPipeline.PERSON_PREFIX}$personId")
}
