package com.whsanha55.cineseek.global.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor

/** SchedulingConfig — cineseek.job 유무에 따라 @Scheduled 처리기가 등록되는지 검증한다 */
class SchedulingConfigTest {

    private val contextRunner = ApplicationContextRunner().withUserConfiguration(SchedulingConfig::class.java)

    @Test
    fun `job이 비어 있으면 스케줄링이 켜진다`() {
        // given
        val runner = contextRunner.withPropertyValues("cineseek.job=")

        // when, then
        runner.run { context ->
            assertThat(context.getBeansOfType(ScheduledAnnotationBeanPostProcessor::class.java)).hasSize(1)
        }
    }

    @Test
    fun `job 설정이 없으면 스케줄링이 켜진다`() {
        // when, then
        contextRunner.run { context ->
            assertThat(context.getBeansOfType(ScheduledAnnotationBeanPostProcessor::class.java)).hasSize(1)
        }
    }

    @Test
    fun `job이 설정되면 스케줄링이 꺼진다`() {
        // given
        val runner = contextRunner.withPropertyValues("cineseek.job=reindex")

        // when, then
        runner.run { context ->
            assertThat(context.getBeansOfType(ScheduledAnnotationBeanPostProcessor::class.java)).isEmpty()
        }
    }
}
