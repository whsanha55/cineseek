package com.whsanha55.cineseek.collect.service

import com.whsanha55.cineseek.collect.enums.CollectTaskTypeEnum.PERSON_FILMO
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** FilmographyRefreshRequesterImpl — 포트 구현이 우선순위 enqueue와 CollectTaskService 위임을 하는지 검증 */
class FilmographyRefreshRequesterImplTest {

    private val collectTaskService = mockk<CollectTaskService>()

    private val requester = FilmographyRefreshRequesterImpl(collectTaskService)

    @Test
    fun `requestRefresh — 처음 보는 인물은 PRIORITY_USER_CLICK 우선순위로 멱등 등록한다`() {
        every { collectTaskService.enqueue(PERSON_FILMO, "person:5", CollectPipeline.PRIORITY_USER_CLICK) } returns true

        requester.requestRefresh(5)

        verify(exactly = 1) {
            collectTaskService.enqueue(PERSON_FILMO, "person:5", CollectPipeline.PRIORITY_USER_CLICK)
        }
        verify(exactly = 0) { collectTaskService.requeue(any(), any()) } // 등록됐으면 되살릴 필요 없다
    }

    @Test
    fun `requestRefresh — 이미 등록된 작업이면 requeue로 되살린다(대기·실행 중이면 무시된다)`() {
        every { collectTaskService.enqueue(any(), any(), any()) } returns false
        every { collectTaskService.requeue(PERSON_FILMO, "person:5") } returns true

        requester.requestRefresh(5)

        verify(exactly = 1) { collectTaskService.requeue(PERSON_FILMO, "person:5") }
    }

    @Test
    fun `hasPendingWork — person payloadKey로 CollectTaskService에 위임한다`() {
        every { collectTaskService.hasPendingWork(PERSON_FILMO, "person:5") } returns true
        every { collectTaskService.hasPendingWork(PERSON_FILMO, "person:6") } returns false

        assertThat(requester.hasPendingWork(5)).isTrue
        assertThat(requester.hasPendingWork(6)).isFalse
    }
}
