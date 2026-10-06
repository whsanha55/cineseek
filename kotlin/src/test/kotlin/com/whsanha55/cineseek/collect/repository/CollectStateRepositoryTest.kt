package com.whsanha55.cineseek.collect.repository

import com.whsanha55.cineseek.collect.enums.CollectModeEnum
import com.whsanha55.cineseek.global.config.ClockConfig
import com.whsanha55.cineseek.global.config.JpaConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Import
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant

/** collect_state 단일 행 검증 — V3 마이그레이션이 BOOTSTRAP 행을 미리 넣는다 (1회 가드) */
@DataJpaTest
@Import(JpaConfig::class, ClockConfig::class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class CollectStateRepositoryTest {

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:17")
    }

    @Autowired lateinit var collectStateRepository: CollectStateRepository

    @Test
    fun `마이그레이션이 단일 행을 BOOTSTRAP 모드로 미리 넣는다`() {
        // when
        val state = collectStateRepository.findById(1).orElseThrow()

        // then
        assertThat(state.mode).isEqualTo(CollectModeEnum.BOOTSTRAP)
        assertThat(state.bootstrappedAt).isNull()
        assertThat(collectStateRepository.count()).isEqualTo(1)
    }

    @Test
    fun `transitionTo가 모드와 전환 시각을 갱신한다`() {
        // given
        val state = collectStateRepository.findById(1).orElseThrow()
        val changedAt = Instant.parse("2026-02-01T00:00:00Z")

        // when
        state.transitionTo(CollectModeEnum.DAILY, changedAt)
        collectStateRepository.saveAndFlush(state)

        // then
        val reloaded = collectStateRepository.findById(1).orElseThrow()
        assertThat(reloaded.mode).isEqualTo(CollectModeEnum.DAILY)
        assertThat(reloaded.modeChangedAt).isEqualTo(changedAt)
    }
}
