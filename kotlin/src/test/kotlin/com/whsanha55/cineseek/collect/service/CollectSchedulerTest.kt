package com.whsanha55.cineseek.collect.service

import com.whsanha55.cineseek.collect.config.CollectProperties
import com.whsanha55.cineseek.collect.entity.CollectStateEntity
import com.whsanha55.cineseek.collect.enums.CollectModeEnum
import com.whsanha55.cineseek.collect.enums.CollectTaskTypeEnum.KR_DISCOVER
import com.whsanha55.cineseek.collect.enums.CollectTaskTypeEnum.MOVIE_DETAIL
import com.whsanha55.cineseek.collect.enums.CollectTaskTypeEnum.PERSON_FILMO
import com.whsanha55.cineseek.collect.repository.CollectStateRepository
import com.whsanha55.cineseek.external.tmdb.client.TmdbClient
import com.whsanha55.cineseek.movie.entity.PersonEntity
import com.whsanha55.cineseek.movie.repository.MovieRepository
import com.whsanha55.cineseek.movie.repository.PersonRepository
import com.whsanha55.cineseek.movie.vo.TmdbDiscoverResult
import io.mockk.Called
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional

/**
 * CollectScheduler — 틱 흐름(회수→enqueue→배치→전환), 일일 증분 재등록 4종, 동시 실행 가드를
 * MockK 목으로 검증한다. 실제 스케줄 기동은 검증하지 않는다 — 애노테이션 placeholder만 확인한다
 */
class CollectSchedulerTest {

    private val pipeline = mockk<CollectPipeline>(relaxed = true)
    private val taskService = mockk<CollectTaskService>(relaxed = true)
    private val collectStateRepository = mockk<CollectStateRepository>(relaxed = true)
    private val personRepository = mockk<PersonRepository>(relaxed = true)
    private val movieRepository = mockk<MovieRepository>(relaxed = true)
    private val tmdbClient = mockk<TmdbClient>(relaxed = true)
    private val statsService = mockk<CollectStatsService>(relaxed = true)

    private val start: Instant = Instant.parse("2026-06-15T15:00:00Z") // KST 기준 2026-06-16 00:00

    private val scheduler = CollectScheduler(
        pipeline = pipeline,
        taskService = taskService,
        collectStateRepository = collectStateRepository,
        personRepository = personRepository,
        movieRepository = movieRepository,
        tmdbClient = tmdbClient,
        statsService = statsService,
        properties = CollectProperties(),
        clock = Clock.fixed(start, ZoneOffset.UTC),
    )

    @BeforeEach
    fun setUp() {
        stubState(CollectStateEntity())
        every { statsService.snapshot() } returns emptySnapshot()
    }

    @Test
    fun `bootstrap 틱 — 회수·enqueue·배치 순으로 실행하고 잔여가 없으면 DAILY로 전환해 last_summary를 남긴다`() {
        val state = CollectStateEntity()
        stubState(state)
        every { pipeline.runBatch() } returns batchResult()
        every { taskService.hasRemainingWork() } returns false

        scheduler.tick()

        verifyOrder {
            taskService.recoverExpiredLeases()
            pipeline.enqueueBootstrapIfNotStarted()
            pipeline.runBatch()
        }
        assertThat(state.mode).isEqualTo(CollectModeEnum.DAILY)
        assertThat(state.lastRunAt).isEqualTo(start)
        assertThat(state.lastSummary).contains("성공=8")
        verify(exactly = 1) { collectStateRepository.save(state) }
    }

    @Test
    fun `잔여 작업이 남으면 BOOTSTRAP를 유지한다 — FAILED는 hasRemainingWork 잔여로 치지 않는다`() {
        val state = CollectStateEntity()
        stubState(state)
        every { pipeline.runBatch() } returns batchResult()
        every { taskService.hasRemainingWork() } returns true

        scheduler.tick()

        assertThat(state.mode).isEqualTo(CollectModeEnum.BOOTSTRAP)
        assertThat(state.lastRunAt).isEqualTo(start) // 전환과 무관하게 틱 기록은 남는다
    }

    @Test
    fun `DAILY 모드 틱 — bootstrap enqueue 없이 배치만 돈다`() {
        stubState(CollectStateEntity().apply { transitionTo(CollectModeEnum.DAILY, start) })
        every { pipeline.runBatch() } returns batchResult()

        scheduler.tick()

        verify(exactly = 0) { pipeline.enqueueBootstrapIfNotStarted() }
        verify(exactly = 1) { pipeline.runBatch() }
    }

    @Test
    fun `일일 틱 — 신작 창·글로벌 첫 페이지·만료 필모·줄거리 누락·재시도 소진 실패를 재등록하고 배치를 돈다`() {
        stubState(CollectStateEntity().apply { transitionTo(CollectModeEnum.DAILY, start) })
        every { personRepository.findByFilmoCheckedAtBefore(any()) } returns
            listOf(PersonEntity(personId = 7L, name = "배우"))
        every { movieRepository.findTmdbIdsByOverviewIsNull(any()) } returns listOf(101L)
        every { tmdbClient.fetchDiscoverIds(any(), any(), any()) } returns
            TmdbDiscoverResult(
                tmdbIds = listOf(11L, 12L),
                lastPage = 1,
                totalPages = 1,
            )
        every { taskService.requeue(any(), any()) } returns false
        every { taskService.enqueue(any(), any(), any()) } returns true
        every { pipeline.runBatch() } returns batchResult()

        scheduler.dailyTick()

        // ① 최근 2년(KST 오늘=2026-06-16 → 2024~2026) 연도 구간 × 2기준
        listOf("origin", "lang").forEach { criterion ->
            listOf("2024", "2025", "2026").forEach { year ->
                val payloadKey = "kr:$criterion:$year-01-01..$year-12-31"
                verify { taskService.enqueue(KR_DISCOVER, payloadKey, CollectPipeline.PRIORITY_NORMAL) }
            }
        }
        // ① 글로벌 인기 첫 페이지 → 상세 수집 등록
        verify { taskService.enqueue(MOVIE_DETAIL, "movie:tmdb:11", CollectPipeline.PRIORITY_NORMAL) }
        verify { taskService.enqueue(MOVIE_DETAIL, "movie:tmdb:12", CollectPipeline.PRIORITY_NORMAL) }
        // ② 필모 만료 인물
        verify { taskService.enqueue(PERSON_FILMO, "person:7", CollectPipeline.PRIORITY_NORMAL) }
        // ③ 줄거리 누락 영화
        verify { taskService.enqueue(MOVIE_DETAIL, "movie:tmdb:101", CollectPipeline.PRIORITY_NORMAL) }
        // ④ 재시도 소진 실패 복귀
        verify(exactly = 1) { taskService.resetRetryExhausted() }
        // ⑤ 배치 실행
        verify(exactly = 1) { pipeline.runBatch() }
    }

    @Test
    fun `일일 틱 — 아직 BOOTSTRAP 모드이면 아무것도 하지 않고 건너뛴다`() {
        scheduler.dailyTick()

        verify { pipeline wasNot Called }
        verify { tmdbClient wasNot Called }
        verify { taskService wasNot Called }
    }

    @Test
    fun `동시 틱 가드 — 이전 틱이 끝나지 않았으면 두 번째 틱은 아무것도 하지 않는다`() {
        every { pipeline.runBatch() } answers {
            scheduler.tick() // 첫 틱 실행 중 재진입
            batchResult()
        }

        scheduler.tick()

        verify(exactly = 1) { pipeline.runBatch() }
    }

    @Test
    fun `스케줄 애노테이션 — interval 플레이스홀더와 daily cron·zone을 참조한다`() {
        val tick = CollectScheduler::class.java.getMethod("tick").getAnnotation(Scheduled::class.java)
        assertThat(tick.fixedDelayString).isEqualTo("\${cineseek.collect.interval}")

        val daily = CollectScheduler::class.java.getMethod("dailyTick").getAnnotation(Scheduled::class.java)
        assertThat(daily.cron).isEqualTo("\${cineseek.collect.daily-cron}")
        assertThat(daily.zone).isEqualTo("\${cineseek.collect.zone}")
    }

    @Test
    fun `활성 조건 — collect enabled=true일 때만 빈으로 등록된다`() {
        val conditional = requireNotNull(CollectScheduler::class.java.getAnnotation(ConditionalOnProperty::class.java))
        assertThat(conditional.prefix).isEqualTo("cineseek.collect")
        assertThat(conditional.name).containsExactly("enabled")
        assertThat(conditional.havingValue).isEqualTo("true")
    }

    private fun stubState(state: CollectStateEntity) {
        every { collectStateRepository.findById(CollectStateEntity.SINGLETON_ID) } returns Optional.of(state)
        every { collectStateRepository.save(any()) } returnsArgument 0
    }

    private fun emptySnapshot() = CollectSnapshot(
        statusCountsByType = emptyMap(),
        krMoviesByYear = emptyMap(),
        missingOverviewCount = 0,
        seedCoverage = emptyList(),
        pgMovieCount = 0,
        qdrantPointCount = null,
    )

    private fun batchResult() = BatchResult(
        processed = 10,
        succeeded = 8,
        failedRetryable = 1,
        failedPermanent = 1,
        storedMovies = 6,
        touchedMovieIds = emptyList(),
    )
}
