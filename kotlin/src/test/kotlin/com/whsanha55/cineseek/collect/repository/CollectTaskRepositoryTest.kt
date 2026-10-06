package com.whsanha55.cineseek.collect.repository

import com.whsanha55.cineseek.collect.entity.CollectTaskEntity
import com.whsanha55.cineseek.collect.enums.CollectTaskStatusEnum.DONE
import com.whsanha55.cineseek.collect.enums.CollectTaskStatusEnum.PENDING
import com.whsanha55.cineseek.collect.enums.CollectTaskStatusEnum.RUNNING
import com.whsanha55.cineseek.collect.enums.CollectTaskTypeEnum
import com.whsanha55.cineseek.global.config.ClockConfig
import com.whsanha55.cineseek.global.config.JpaConfig
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.PageRequest
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant

/**
 * collect_task 큐 동작 검증 — 클레임 3단계(후보 조회 → 조건부 UPDATE → 재조회),
 * 만료 lease 회수, requeue, UNIQUE 멱등키. Flyway가 V3 스키마를 만들고
 * ddl-auto: validate가 엔티티와 대조한다
 */
@DataJpaTest
@Import(JpaConfig::class, ClockConfig::class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class CollectTaskRepositoryTest {

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:17")

        val NOW: Instant = Instant.parse("2026-01-01T00:00:00Z")
    }

    @Autowired lateinit var collectTaskRepository: CollectTaskRepository

    private fun saveTask(
        payloadKey: String,
        taskType: CollectTaskTypeEnum = CollectTaskTypeEnum.KR_DISCOVER,
        priority: Int = 0,
        mutate: (CollectTaskEntity) -> Unit = {},
    ): CollectTaskEntity = collectTaskRepository.save(
        CollectTaskEntity(taskType = taskType, payloadKey = payloadKey, priority = priority).apply(mutate),
    )

    @Test
    fun `task_type와 payload_key가 같은 조합은 저장이 거부된다`() {
        // given
        saveTask("kr:origin:2023")

        // when & then
        assertThatThrownBy {
            collectTaskRepository.saveAndFlush(
                CollectTaskEntity(taskType = CollectTaskTypeEnum.KR_DISCOVER, payloadKey = "kr:origin:2023"),
            )
        }.isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `클레임은 도래한 PENDING만 우선순위 순으로 잡고 lease를 새긴다`() {
        // given
        val low = saveTask("kr:origin:2023-01-01..2023-12-31")
        val high = saveTask("global:2024", taskType = CollectTaskTypeEnum.GLOBAL_DISCOVER, priority = 5)
        val notDue = saveTask("movie:tmdb:123", taskType = CollectTaskTypeEnum.MOVIE_DETAIL) {
            it.scheduleRetry(NOW.plusSeconds(3600))
        }
        val done = saveTask("person:456", taskType = CollectTaskTypeEnum.PERSON_FILMO) { it.markDone() }

        // when
        val ids = collectTaskRepository.findClaimableIds(PENDING, NOW, PageRequest.ofSize(10))
        val claimedCount = collectTaskRepository.claimTasks(ids, PENDING, RUNNING, NOW, "worker-1")

        // then — priority DESC, taskId ASC 정렬로 high가 먼저
        assertThat(ids).containsExactly(high.taskId, low.taskId)
        assertThat(claimedCount).isEqualTo(2)
        val running = collectTaskRepository.findAllByTaskIdInAndStatus(ids, RUNNING)
        assertThat(running).hasSize(2)
        running.forEach { task ->
            assertThat(task.status).isEqualTo(RUNNING)
            assertThat(task.lockedAt).isEqualTo(NOW)
            assertThat(task.lockedBy).isEqualTo("worker-1")
        }

        // 재시도 예약된 작업은 아직 잡히지 않는다
        val reloadedNotDue = collectTaskRepository.findById(notDue.taskId!!).orElseThrow()
        assertThat(reloadedNotDue.status).isEqualTo(PENDING)
        assertThat(reloadedNotDue.nextRetryAt).isEqualTo(NOW.plusSeconds(3600))
        assertThat(reloadedNotDue.attempts).isEqualTo(1)
        assertThat(reloadedNotDue.lockedAt).isNull()

        // 완료된 작업도 건드리지 않는다
        assertThat(collectTaskRepository.findById(done.taskId!!).orElseThrow().status).isEqualTo(DONE)
    }

    @Test
    fun `만료된 lease는 PENDING으로 회수된다`() {
        // given — 2시간 전에 잡은 lease는 만료, 1분 전 lease는 유효
        val expired = saveTask("expired") { it.lease(NOW.minusSeconds(7200), "worker-1") }
        val active = saveTask("active") { it.lease(NOW.minusSeconds(60), "worker-2") }

        // when
        val released = collectTaskRepository.releaseExpiredLeases(PENDING, RUNNING, NOW.minusSeconds(3600))

        // then
        assertThat(released).isEqualTo(1)
        val reloadedExpired = collectTaskRepository.findById(expired.taskId!!).orElseThrow()
        assertThat(reloadedExpired.status).isEqualTo(PENDING)
        assertThat(reloadedExpired.lockedAt).isNull()
        val reloadedActive = collectTaskRepository.findById(active.taskId!!).orElseThrow()
        assertThat(reloadedActive.status).isEqualTo(RUNNING)
        assertThat(reloadedActive.lockedAt).isEqualTo(NOW.minusSeconds(60))
    }

    @Test
    fun `requeue는 DONE 작업을 초기화된 PENDING으로 되돌린다`() {
        // given — attempts와 checkpoint가 남은 상태에서 완료
        val task = saveTask("kr:origin:2023") {
            it.scheduleRetry(NOW)
            it.saveCheckpoint("""{"page":12}""")
            it.markDone()
        }
        assertThat(collectTaskRepository.existsByStatusIn(listOf(PENDING, RUNNING))).isFalse()

        // when
        val requeued = collectTaskRepository.requeueDone(
            CollectTaskTypeEnum.KR_DISCOVER,
            "kr:origin:2023",
            PENDING,
            DONE,
        )

        // then
        assertThat(requeued).isEqualTo(1)
        val reloaded = collectTaskRepository.findById(task.taskId!!).orElseThrow()
        assertThat(reloaded.status).isEqualTo(PENDING)
        assertThat(reloaded.attempts).isZero()
        assertThat(reloaded.checkpoint).isNull()
        assertThat(collectTaskRepository.existsByStatusIn(listOf(PENDING, RUNNING))).isTrue()

        // 이미 PENDING이면 두 번 requeue하지 않는다
        assertThat(
            collectTaskRepository.requeueDone(CollectTaskTypeEnum.KR_DISCOVER, "kr:origin:2023", PENDING, DONE),
        ).isZero()
    }

    @Test
    fun `멱등키 조회와 type별 상태 집계`() {
        // given
        saveTask("kr:origin:2023")
        saveTask("kr:origin:2024")
        saveTask("movie:tmdb:123", taskType = CollectTaskTypeEnum.MOVIE_DETAIL) { it.markDone() }

        // when & then
        val found = collectTaskRepository.findByTaskTypeAndPayloadKey(
            CollectTaskTypeEnum.KR_DISCOVER,
            "kr:origin:2023",
        )!!
        assertThat(found.priority).isZero()
        assertThat(
            collectTaskRepository.findByTaskTypeAndPayloadKey(CollectTaskTypeEnum.KR_DISCOVER, "kr:nothing"),
        ).isNull()
        assertThat(
            collectTaskRepository.countByTaskTypeAndStatus(CollectTaskTypeEnum.KR_DISCOVER, PENDING),
        ).isEqualTo(2)
        assertThat(
            collectTaskRepository.countByTaskTypeAndStatus(CollectTaskTypeEnum.MOVIE_DETAIL, DONE),
        ).isEqualTo(1)
    }
}
