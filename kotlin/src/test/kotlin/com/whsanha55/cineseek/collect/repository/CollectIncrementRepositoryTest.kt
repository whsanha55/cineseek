package com.whsanha55.cineseek.collect.repository

import com.whsanha55.cineseek.collect.entity.CollectTaskEntity
import com.whsanha55.cineseek.collect.enums.CollectTaskStatusEnum.DONE
import com.whsanha55.cineseek.collect.enums.CollectTaskStatusEnum.FAILED
import com.whsanha55.cineseek.collect.enums.CollectTaskStatusEnum.PENDING
import com.whsanha55.cineseek.collect.enums.CollectTaskTypeEnum.KR_DISCOVER
import com.whsanha55.cineseek.collect.enums.CollectTaskTypeEnum.MOVIE_DETAIL
import com.whsanha55.cineseek.global.config.ClockConfig
import com.whsanha55.cineseek.global.config.JpaConfig
import com.whsanha55.cineseek.movie.entity.MovieEntity
import com.whsanha55.cineseek.movie.entity.PersonEntity
import com.whsanha55.cineseek.movie.enums.PersonFilmoStateEnum
import com.whsanha55.cineseek.movie.repository.MovieRepository
import com.whsanha55.cineseek.movie.repository.PersonRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageRequest
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant

/**
 * 일일 증분·통계가 의존하는 쿼리 검증 — 재시도 소진 실패 복귀, 만료 필모 조회, 줄거리 누락 조회,
 * 상태 집계, 한국 영화 연도별 수. CollectSchedulerTest는 목으로 대체하므로 여기서 SQL 자체를 확인한다
 */
@DataJpaTest
@Import(JpaConfig::class, ClockConfig::class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class CollectIncrementRepositoryTest {

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:17")

        private const val RETRY_MAX = 8 // CollectProperties 기본값과 같은 상한으로 맞춘다
    }

    @Autowired lateinit var collectTaskRepository: CollectTaskRepository

    @Autowired lateinit var movieRepository: MovieRepository

    @Autowired lateinit var personRepository: PersonRepository

    @Test
    fun `resetRetryExhausted — 재시도를 소진한 FAILED만 초기화해 PENDING으로 되돌린다`() {
        // given — attempts를 상한(8)까지 소진한 실패와 즉시 영구 확정된 실패, 완료 작업
        val exhausted = collectTaskRepository.save(
            CollectTaskEntity(taskType = MOVIE_DETAIL, payloadKey = "movie:tmdb:1").apply {
                repeat(RETRY_MAX) { scheduleRetry(Instant.EPOCH) }
                saveCheckpoint("""{"page":3}""")
                markFailed("재시도 소진")
            },
        )
        val permanent = collectTaskRepository.save(
            CollectTaskEntity(taskType = MOVIE_DETAIL, payloadKey = "movie:tmdb:2").apply { markFailed("404") },
        )
        collectTaskRepository.save(
            CollectTaskEntity(taskType = KR_DISCOVER, payloadKey = "kr:origin:2024-01-01..2024-12-31")
                .apply { markDone() },
        )

        // when
        val reset = collectTaskRepository.resetRetryExhausted(PENDING, FAILED, RETRY_MAX)

        // then
        assertThat(reset).isEqualTo(1)
        val reloaded = collectTaskRepository.findById(exhausted.taskId!!).orElseThrow()
        assertThat(reloaded.status).isEqualTo(PENDING)
        assertThat(reloaded.attempts).isZero
        assertThat(reloaded.nextRetryAt).isNull()
        assertThat(reloaded.checkpoint).isNull()
        assertThat(reloaded.lastError).isEqualTo("재시도 소진") // 오류 기록은 남긴다
        assertThat(collectTaskRepository.findById(permanent.taskId!!).orElseThrow().status).isEqualTo(FAILED)
    }

    @Test
    fun `줄거리 누락 조회 — overview가 NULL인 영화만 오래된 순으로 상한만큼`() {
        movieRepository.save(MovieEntity(tmdbId = 1L, title = "줄거리 없음 1"))
        movieRepository.save(MovieEntity(tmdbId = 2L, title = "줄거리 없음 2"))
        movieRepository.save(MovieEntity(tmdbId = 3L, title = "줄거리 있음", overview = "내용"))

        assertThat(movieRepository.countByOverviewIsNull()).isEqualTo(2)
        assertThat(movieRepository.findTmdbIdsByOverviewIsNull(PageRequest.ofSize(1))).containsExactly(1L)
        assertThat(movieRepository.findTmdbIdsByOverviewIsNull(PageRequest.ofSize(10))).containsExactly(1L, 2L)
    }

    @Test
    fun `한국 영화 연도별 수 — 제작국가 KR만, 연도 없음은 제외하고 내림차순`() {
        movieRepository.save(MovieEntity(tmdbId = 10L, title = "한국 2025", originCountry = "KR", releaseYear = 2025))
        movieRepository.save(MovieEntity(tmdbId = 11L, title = "한국 2024", originCountry = "KR", releaseYear = 2024))
        movieRepository.save(MovieEntity(tmdbId = 12L, title = "미국", originCountry = "US", releaseYear = 2024))
        movieRepository.save(MovieEntity(tmdbId = 13L, title = "국가 없음", releaseYear = 2023))
        movieRepository.save(MovieEntity(tmdbId = 14L, title = "한국 연도미상", originCountry = "KR"))

        val counts = movieRepository.countKrMoviesByYear().associate { tuple ->
            (tuple.get("yr") as Number).toInt() to (tuple.get("total") as Number).toLong()
        }

        assertThat(counts.keys).containsExactly(2025, 2024)
        assertThat(counts[2025]).isEqualTo(1)
        assertThat(counts[2024]).isEqualTo(1)
    }

    @Test
    fun `만료 필모 조회 — threshold 이전에 확인한 인물만 (한 번도 확인하지 않은 NULL은 제외)`() {
        val threshold = Instant.parse("2026-05-01T00:00:00Z")
        personRepository.save(
            PersonEntity(personId = 1L, name = "만료").apply {
                applyFilmography(Instant.parse("2026-04-01T00:00:00Z"), 10, 10, PersonFilmoStateEnum.COMPLETE)
            },
        )
        personRepository.save(
            PersonEntity(personId = 2L, name = "최근").apply {
                applyFilmography(Instant.parse("2026-06-01T00:00:00Z"), 10, 10, PersonFilmoStateEnum.COMPLETE)
            },
        )
        personRepository.save(PersonEntity(personId = 3L, name = "미확인"))

        assertThat(personRepository.findByFilmoCheckedAtBefore(threshold).map { it.personId }).containsExactly(1L)
    }

    @Test
    fun `countByStatus — 상태별 총 집계`() {
        collectTaskRepository.save(CollectTaskEntity(taskType = MOVIE_DETAIL, payloadKey = "movie:tmdb:1"))
        collectTaskRepository.save(
            CollectTaskEntity(taskType = MOVIE_DETAIL, payloadKey = "movie:tmdb:2").apply { markDone() },
        )

        assertThat(collectTaskRepository.countByStatus(PENDING)).isEqualTo(1)
        assertThat(collectTaskRepository.countByStatus(DONE)).isEqualTo(1)
        assertThat(collectTaskRepository.countByStatus(FAILED)).isZero
    }
}
