package com.whsanha55.cineseek.collect.service

import com.whsanha55.cineseek.collect.config.CollectProperties
import com.whsanha55.cineseek.collect.entity.CollectStateEntity
import com.whsanha55.cineseek.collect.enums.CollectModeEnum
import com.whsanha55.cineseek.collect.enums.CollectTaskStatusEnum.DONE
import com.whsanha55.cineseek.collect.enums.CollectTaskStatusEnum.PENDING
import com.whsanha55.cineseek.collect.enums.CollectTaskTypeEnum.MOVIE_DETAIL
import com.whsanha55.cineseek.collect.repository.CollectStateRepository
import com.whsanha55.cineseek.collect.repository.CollectTaskRepository
import com.whsanha55.cineseek.external.tmdb.client.TmdbClient
import com.whsanha55.cineseek.global.config.ClockConfig
import com.whsanha55.cineseek.global.config.JpaConfig
import com.whsanha55.cineseek.movie.entity.MovieEntity
import com.whsanha55.cineseek.movie.repository.MovieRepository
import com.whsanha55.cineseek.movie.repository.PersonRepository
import com.whsanha55.cineseek.movie.vo.TmdbDiscoverResult
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Import
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * CollectScheduler 일일 틱의 최근 개봉작 상세 재등록 — 실제 PG의 개봉일 조회와 (task_type, payload_key) 멱등을
 * 함께 확인한다. 배치 실행·TMDB·통계는 목으로 대체한다 (흐름 자체는 CollectSchedulerTest가 담당)
 */
@DataJpaTest
@Import(JpaConfig::class, ClockConfig::class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class CollectSchedulerRecentDetailTest {

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:17")

        private val START: Instant = Instant.parse("2026-10-21T15:00:00Z") // KST 기준 2026-10-22 00:00
    }

    @Autowired lateinit var collectTaskRepository: CollectTaskRepository

    @Autowired lateinit var collectStateRepository: CollectStateRepository

    @Autowired lateinit var movieRepository: MovieRepository

    @Autowired lateinit var personRepository: PersonRepository

    private val pipeline = mockk<CollectPipeline>(relaxed = true)
    private val tmdbClient = mockk<TmdbClient>(relaxed = true)
    private val statsService = mockk<CollectStatsService>(relaxed = true)
    private lateinit var scheduler: CollectScheduler

    @BeforeEach
    fun setUp() {
        val clock = Clock.fixed(START, ZoneOffset.UTC)
        val properties = CollectProperties()
        scheduler = CollectScheduler(
            pipeline = pipeline,
            taskService = CollectTaskService(collectTaskRepository, properties, clock),
            collectStateRepository = collectStateRepository,
            personRepository = personRepository,
            movieRepository = movieRepository,
            tmdbClient = tmdbClient,
            statsService = statsService,
            properties = properties,
            clock = clock,
        )
        collectStateRepository.save(
            collectStateRepository.findById(CollectStateEntity.SINGLETON_ID).orElseGet { CollectStateEntity() }
                .apply { transitionTo(CollectModeEnum.DAILY, START) },
        )
        every { tmdbClient.fetchDiscoverIds(any(), any(), any()) } returns
            TmdbDiscoverResult(tmdbIds = emptyList(), lastPage = 1, totalPages = 1)
        every { pipeline.runBatch() } returns BatchResult(0, 0, 0, 0, 0, emptyList())

        // KST 오늘 2026-10-22 → 기본 90일 창의 시작은 2026-07-24
        movie(tmdbId = 1L, releaseDate = LocalDate.parse("2026-10-21")) // 어제 개봉
        movie(tmdbId = 2L, releaseDate = LocalDate.parse("2026-12-25")) // 개봉 예정
        movie(tmdbId = 3L, releaseDate = LocalDate.parse("2026-07-24")) // 창 경계(90일 전)
        movie(tmdbId = 4L, releaseDate = LocalDate.parse("2026-07-23")) // 창 밖(91일 전)
        movie(tmdbId = 5L, releaseDate = null) // 개봉일 미상
    }

    @Test
    fun `일일 틱 — 개봉일이 최근 90일 안이거나 개봉 예정인 영화만 상세 수집을 등록한다`() {
        // when
        scheduler.dailyTick()

        // then
        assertThat(movieDetailKeys()).containsExactlyInAnyOrder("movie:tmdb:1", "movie:tmdb:2", "movie:tmdb:3")
    }

    @Test
    fun `일일 틱 반복 — 대기 중이면 그대로 두고 완료된 작업은 같은 행을 되살린다 (중복 행 없음)`() {
        // given — 첫 틱이 만든 작업
        scheduler.dailyTick()
        val firstIds = movieDetailTasks().map { it.taskId }

        // when — 대기 중에 다시 틱
        scheduler.dailyTick()

        // then
        assertThat(movieDetailTasks().map { it.taskId }).containsExactlyInAnyOrderElementsOf(firstIds)

        // given — 수집이 끝나 DONE이 된 뒤
        movieDetailTasks().forEach { collectTaskRepository.save(it.apply { markDone() }) }
        assertThat(movieDetailTasks().map { it.status }).containsOnly(DONE)

        // when — 다음 날 틱
        scheduler.dailyTick()

        // then — 같은 행이 PENDING으로 돌아온다
        val requeued = movieDetailTasks()
        assertThat(requeued.map { it.taskId }).containsExactlyInAnyOrderElementsOf(firstIds)
        assertThat(requeued.map { it.status }).containsOnly(PENDING)
    }

    private fun movie(tmdbId: Long, releaseDate: LocalDate?) = movieRepository.save(
        MovieEntity(tmdbId = tmdbId, title = "영화$tmdbId", overview = "줄거리", releaseDate = releaseDate),
    )

    private fun movieDetailTasks() = collectTaskRepository.findAll().filter { it.taskType == MOVIE_DETAIL }

    private fun movieDetailKeys() = movieDetailTasks().map { it.payloadKey }
}
