package com.whsanha55.cineseek.collect.service

import com.whsanha55.cineseek.collect.config.CollectProperties
import com.whsanha55.cineseek.collect.entity.CollectTaskEntity
import com.whsanha55.cineseek.collect.enums.CollectTaskStatusEnum.DONE
import com.whsanha55.cineseek.collect.enums.CollectTaskStatusEnum.FAILED
import com.whsanha55.cineseek.collect.enums.CollectTaskStatusEnum.PENDING
import com.whsanha55.cineseek.collect.enums.CollectTaskStatusEnum.RUNNING
import com.whsanha55.cineseek.collect.enums.CollectTaskTypeEnum
import com.whsanha55.cineseek.collect.repository.CollectTaskRepository
import com.whsanha55.cineseek.global.config.JpaConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.random.Random

/**
 * CollectTaskService — 큐 운영(멱등 enqueue, 클레임, 완료/실패, 백오프, requeue, 만료 회수) 검증.
 * CollectProperties는 @ConfigurationPropertiesScan이 application.yml 기본값으로 등록한 것을 쓰고
 * (yml 바인딩도 함께 검증), 경계 값을 짧게 만드는 항목만 오버라이드한다
 */
@DataJpaTest(
    properties = [
        "cineseek.collect.lease-timeout=1h", // 만료 회수 경계를 짧게
        "cineseek.collect.retry-max-attempts=2", // 영구 실패 전환을 빠르게
    ],
)
@Import(CollectTaskService::class, JpaConfig::class, CollectTaskServiceTest.Config::class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class CollectTaskServiceTest {

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:17")

        val START: Instant = Instant.parse("2026-01-01T00:00:00Z")
    }

    @TestConfiguration
    @EnableConfigurationProperties(CollectProperties::class)
    class Config {
        @Bean
        fun clock(): MutableClock = MutableClock(START)
    }

    /** 시각을 임의로 옮길 수 있는 Clock — nextRetryAt·leaseTimeout 경계를 밟아본다 */
    class MutableClock(start: Instant) : Clock() {
        private val origin = start
        private var current = start

        fun reset() {
            current = origin
        }

        fun advance(duration: Duration) {
            current = current.plus(duration)
        }

        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock = this

        override fun instant(): Instant = current
    }

    @Autowired lateinit var service: CollectTaskService

    @Autowired lateinit var collectTaskRepository: CollectTaskRepository

    @Autowired lateinit var clock: MutableClock

    @BeforeEach
    fun resetClock() {
        clock.reset() // 컨텍스트가 캐시되므로 모든 테스트가 같은 기준 시각에서 시작하게 한다
    }

    private fun reload(type: CollectTaskTypeEnum, payloadKey: String): CollectTaskEntity =
        collectTaskRepository.findByTaskTypeAndPayloadKey(type, payloadKey)!!

    @Test
    fun `backoffDelay — base에 2^attempts를 곱하고 base 미만의 지터를 더한다`() {
        val base = Duration.ofSeconds(60)

        repeat(20) { seed ->
            val random = Random(seed)
            assertThat(CollectTaskService.backoffDelay(base, 0, random)).isBetween(base, base.multipliedBy(2))
            assertThat(CollectTaskService.backoffDelay(base, 3, random)).isBetween(
                base.multipliedBy(8),
                base.multipliedBy(9),
            )
            assertThat(CollectTaskService.backoffDelay(base, 8, random)).isBetween(
                base.multipliedBy(256),
                base.multipliedBy(257),
            )
        }
    }

    @Test
    fun `멱등 enqueue — 같은 키의 두 번째 등록은 false`() {
        // when
        val first = service.enqueue(CollectTaskTypeEnum.KR_DISCOVER, "kr:origin:2023-01-01..2023-12-31")
        val second = service.enqueue(CollectTaskTypeEnum.KR_DISCOVER, "kr:origin:2023-01-01..2023-12-31")
        val other = service.enqueue(CollectTaskTypeEnum.KR_DISCOVER, "kr:lang:2023-01-01..2023-12-31")

        // then — payload_key가 다르면 별도 작업
        assertThat(first).isTrue()
        assertThat(second).isFalse()
        assertThat(other).isTrue()
        assertThat(service.hasRemainingWork()).isTrue()
    }

    @Test
    fun `claim은 작업을 RUNNING으로 잡고 완료하면 잔여 작업이 없다`() {
        // given
        service.enqueue(CollectTaskTypeEnum.MOVIE_DETAIL, "movie:tmdb:1", priority = 1)
        service.enqueue(CollectTaskTypeEnum.MOVIE_DETAIL, "movie:tmdb:2", priority = 5)

        // when
        val claimed = service.claim(10)

        // then
        assertThat(claimed.map { it.payloadKey }).containsExactlyInAnyOrder("movie:tmdb:1", "movie:tmdb:2")
        claimed.forEach {
            assertThat(it.status).isEqualTo(RUNNING)
            assertThat(it.lockedBy).isNotBlank()
        }
        assertThat(service.hasRemainingWork()).isTrue() // RUNNING도 잔여 작업

        // 완료 후에는 없다
        claimed.forEach { service.complete(it) }
        assertThat(service.hasRemainingWork()).isFalse()
    }

    @Test
    fun `complete — 체크포인트를 남기고 완료 처리`() {
        service.enqueue(CollectTaskTypeEnum.KR_DISCOVER, "kr:origin:2019")
        val claimed = service.claim(1).single()

        service.complete(claimed, """{"page":400,"split":true}""")

        val done = reload(CollectTaskTypeEnum.KR_DISCOVER, "kr:origin:2019")
        assertThat(done.status).isEqualTo(DONE)
        assertThat(done.checkpoint).isEqualTo("""{"page":400,"split":true}""")
        assertThat(done.lastError).isNull()
    }

    @Test
    fun `fail 재시도 예약 — nextRetryAt 이전에는 클레임 불가, 이후에는 가능`() {
        service.enqueue(CollectTaskTypeEnum.KR_DISCOVER, "kr:origin:2023")
        val claimed = service.claim(1).single()

        service.fail(claimed, "일시 오류", retryable = true)

        val scheduled = reload(CollectTaskTypeEnum.KR_DISCOVER, "kr:origin:2023")
        assertThat(scheduled.status).isEqualTo(PENDING)
        assertThat(scheduled.attempts).isEqualTo(1)
        assertThat(scheduled.lastError).isEqualTo("일시 오류")
        // backoff = 60s * 2^0 + [0, 60s) 지터 → [START+60s, START+120s)
        assertThat(scheduled.nextRetryAt).isBetween(START.plusSeconds(60), START.plusSeconds(120))

        // 예약 시각 이전 — 클레임 불가
        clock.advance(Duration.ofSeconds(59))
        assertThat(service.claim(10)).isEmpty()

        // 예약 시각 이후 — 다시 잡힌다
        clock.advance(Duration.ofSeconds(61))
        assertThat(service.claim(10)).hasSize(1)
    }

    @Test
    fun `attempts 상한 도달 — 재시도를 소진하면 FAILED로 영구 확정`() {
        // retryMaxAttempts=2 — 재시도 2회 뒤 영구 실패
        service.enqueue(CollectTaskTypeEnum.MOVIE_DETAIL, "movie:tmdb:9")
        val task = service.claim(1).single()
        service.fail(task, "재시도 대상 오류", retryable = true)
        clock.advance(Duration.ofMinutes(3)) // nextRetryAt(최대 2분) 이후
        service.fail(service.claim(1).single(), "재시도 대상 오류", retryable = true)
        assertThat(reload(CollectTaskTypeEnum.MOVIE_DETAIL, "movie:tmdb:9").attempts).isEqualTo(2)

        clock.advance(Duration.ofMinutes(5)) // attempts=2의 nextRetryAt(최대 4분) 이후
        val exhausted = service.claim(1).single()
        service.fail(exhausted, "영구 오류", retryable = true) // attempts=2 >= 상한 2

        val failed = reload(CollectTaskTypeEnum.MOVIE_DETAIL, "movie:tmdb:9")
        assertThat(failed.status).isEqualTo(FAILED)
        assertThat(failed.lastError).isEqualTo("영구 오류")

        // FAILED는 시간이 흘러도 다시 잡히지 않고, 잔여 작업에서도 제외된다
        clock.advance(Duration.ofHours(1))
        assertThat(service.claim(10)).isEmpty()
        assertThat(service.hasRemainingWork()).isFalse()
    }

    @Test
    fun `retryable하지 않은 실패는 재시도 없이 즉시 FAILED`() {
        service.enqueue(CollectTaskTypeEnum.MOVIE_DETAIL, "movie:tmdb:5")
        val claimed = service.claim(1).single()

        service.fail(claimed, "영구 오류", retryable = false)

        val failed = reload(CollectTaskTypeEnum.MOVIE_DETAIL, "movie:tmdb:5")
        assertThat(failed.status).isEqualTo(FAILED)
        assertThat(failed.attempts).isZero()
        assertThat(failed.nextRetryAt).isNull()
        assertThat(service.hasRemainingWork()).isFalse()
    }

    @Test
    fun `release — 체크포인트를 남기고 즉시 재클레임 가능한 PENDING으로 돌아간다`() {
        service.enqueue(CollectTaskTypeEnum.KR_DISCOVER, "kr:origin:2020")
        val claimed = service.claim(1).single()

        service.release(claimed, """{"page":12}""")

        val released = reload(CollectTaskTypeEnum.KR_DISCOVER, "kr:origin:2020")
        assertThat(released.status).isEqualTo(PENDING)
        assertThat(released.checkpoint).isEqualTo("""{"page":12}""")
        assertThat(released.attempts).isZero() // 재시도가 아니다
        assertThat(released.nextRetryAt).isNull()
        assertThat(released.lockedBy).isNull()

        // 시각 경과 없이도 바로 다시 잡힌다
        assertThat(service.claim(1)).hasSize(1)
    }

    @Test
    fun `만료 lease 회수 — leaseTimeout 넘은 RUNNING만 PENDING으로 되돌아가 재클레임된다`() {
        // leaseTimeout=1h
        service.enqueue(CollectTaskTypeEnum.PERSON_FILMO, "person:1")
        service.enqueue(CollectTaskTypeEnum.PERSON_FILMO, "person:2")
        assertThat(service.claim(10)).hasSize(2)

        clock.advance(Duration.ofMinutes(30))
        assertThat(service.recoverExpiredLeases()).isZero() // 아직 임계 미만

        clock.advance(Duration.ofMinutes(31)) // 클레임 시점에서 61분 경과 — 만료
        assertThat(service.recoverExpiredLeases()).isEqualTo(2)
        assertThat(service.claim(10)).hasSize(2) // 다시 잡힌다
    }

    @Test
    fun `requeue — DONE을 PENDING으로 되돌려 다음 틱에 재수집한다`() {
        service.enqueue(CollectTaskTypeEnum.KR_DISCOVER, "kr:origin:2023")
        service.complete(service.claim(1).single())
        assertThat(service.hasRemainingWork()).isFalse()

        // when
        assertThat(service.requeue(CollectTaskTypeEnum.KR_DISCOVER, "kr:origin:2023")).isTrue()

        // then — attempts/checkpoint 초기화된 PENDING으로 되돌아가 다시 클레임된다
        val requeued = service.claim(1).single()
        assertThat(requeued.payloadKey).isEqualTo("kr:origin:2023")
        assertThat(requeued.status).isEqualTo(RUNNING)
        assertThat(requeued.checkpoint).isNull()

        // DONE이 없는 키는 false
        assertThat(service.requeue(CollectTaskTypeEnum.KR_DISCOVER, "kr:origin:2022")).isFalse()
    }

    @Test
    fun `hasPendingWork — PENDING이나 RUNNING일 때만 true`() {
        assertThat(service.hasPendingWork(CollectTaskTypeEnum.MOVIE_DETAIL, "movie:tmdb:7")).isFalse()

        service.enqueue(CollectTaskTypeEnum.MOVIE_DETAIL, "movie:tmdb:7")
        assertThat(service.hasPendingWork(CollectTaskTypeEnum.MOVIE_DETAIL, "movie:tmdb:7")).isTrue()

        val claimed = service.claim(1).single()
        assertThat(service.hasPendingWork(CollectTaskTypeEnum.MOVIE_DETAIL, "movie:tmdb:7")).isTrue() // RUNNING

        service.complete(claimed)
        assertThat(service.hasPendingWork(CollectTaskTypeEnum.MOVIE_DETAIL, "movie:tmdb:7")).isFalse()
    }
}
