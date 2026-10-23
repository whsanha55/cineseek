package com.whsanha55.cineseek.collect.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled

/** UserRequestScheduler — 사용자 요청 처리 호출, 중복 실행 가드, 스케줄·활성 조건을 MockK 목으로 검증한다 */
class UserRequestSchedulerTest {

    private val pipeline = mockk<CollectPipeline>(relaxed = true)

    private val scheduler = UserRequestScheduler(pipeline)

    @Test
    fun `틱마다 사용자 요청을 처리한다`() {
        // when
        scheduler.tick()

        // then
        verify(exactly = 1) { pipeline.runUserRequests() }
    }

    @Test
    fun `이전 틱이 끝나지 않았으면 두 번째 틱은 건너뛴다`() {
        // given
        every { pipeline.runUserRequests() } answers {
            scheduler.tick() // 첫 틱 실행 중 재진입
            0
        }

        // when
        scheduler.tick()

        // then
        verify(exactly = 1) { pipeline.runUserRequests() }
    }

    @Test
    fun `스케줄 애노테이션 — user-request-interval 플레이스홀더를 참조한다`() {
        val tick = UserRequestScheduler::class.java.getMethod("tick").getAnnotation(Scheduled::class.java)
        assertThat(tick.fixedDelayString).isEqualTo("\${cineseek.collect.user-request-interval}")
    }

    @Test
    fun `활성 조건 — 정기 틱과 같이 collect enabled=true일 때만 빈으로 등록된다`() {
        val conditional =
            requireNotNull(UserRequestScheduler::class.java.getAnnotation(ConditionalOnProperty::class.java))
        assertThat(conditional.prefix).isEqualTo("cineseek.collect")
        assertThat(conditional.name).containsExactly("enabled")
        assertThat(conditional.havingValue).isEqualTo("true")
    }
}
