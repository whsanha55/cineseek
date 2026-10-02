package com.whsanha55.cineseek.movie.service

import com.whsanha55.cineseek.global.config.ClockConfig
import com.whsanha55.cineseek.global.config.JpaConfig
import com.whsanha55.cineseek.movie.entity.MovieEntity
import com.whsanha55.cineseek.movie.repository.MovieRepository
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

/** eval의 movieId↔tmdbId 조회가 PG와 맞는지 — 실제 PG 컨테이너로 검증 */
@DataJpaTest
@Import(JpaConfig::class, ClockConfig::class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class MovieTmdbIdServiceTest {

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:17")
    }

    @Autowired lateinit var movieRepository: MovieRepository

    private lateinit var service: MovieTmdbIdService
    private var matrix = 0L
    private var darkKnight = 0L

    @BeforeEach
    fun setUp() {
        service = MovieTmdbIdService(movieRepository)
        matrix = save(603L, "매트릭스")
        darkKnight = save(155L, "다크 나이트")
    }

    @Test
    fun `tmdbIds — movieId를 tmdbId로 매핑하고 없는 id는 빠진다`() {
        // when
        val tmdbIds = service.tmdbIds(listOf(matrix, darkKnight, 999_999L))

        // then
        assertThat(tmdbIds).containsExactlyInAnyOrderEntriesOf(mapOf(matrix to 603L, darkKnight to 155L))
    }

    @Test
    fun `tmdbIds — 빈 목록이면 빈 맵`() {
        // when
        val tmdbIds = service.tmdbIds(emptyList())

        // then
        assertThat(tmdbIds).isEmpty()
    }

    @Test
    fun `existingTmdbIds — PG에 없는 tmdbId는 걸러낸다`() {
        // when
        val existing = service.existingTmdbIds(listOf(603L, 155L, 999_999L))

        // then
        assertThat(existing).containsExactlyInAnyOrder(603L, 155L)
    }

    @Test
    fun `existingTmdbIds — 빈 입력이면 빈 집합`() {
        // when
        val existing = service.existingTmdbIds(emptyList())

        // then
        assertThat(existing).isEmpty()
    }

    private fun save(tmdbId: Long, title: String): Long =
        requireNotNull(movieRepository.save(MovieEntity(tmdbId = tmdbId, title = title)).movieId)
}
