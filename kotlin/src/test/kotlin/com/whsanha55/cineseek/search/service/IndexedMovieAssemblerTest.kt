package com.whsanha55.cineseek.search.service

import com.whsanha55.cineseek.global.config.JpaConfig
import com.whsanha55.cineseek.movie.entity.MovieEntity
import com.whsanha55.cineseek.movie.repository.MovieCastRepository
import com.whsanha55.cineseek.movie.repository.MovieRepository
import com.whsanha55.cineseek.movie.service.MovieUpsertService
import com.whsanha55.cineseek.movie.vo.TmdbCastMember
import com.whsanha55.cineseek.movie.vo.TmdbGenre
import com.whsanha55.cineseek.movie.vo.TmdbMovie
import com.whsanha55.cineseek.movie.vo.TmdbPerson
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** IndexedMovieAssembler — payload의 cast/director가 PG 전체 목록과 일치하는지, overview 없는 영화 스킵 검증 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(
    IndexedMovieAssembler::class,
    MovieUpsertService::class,
    JpaConfig::class,
    IndexedMovieAssemblerTest.FixedClockConfig::class,
)
@Testcontainers
class IndexedMovieAssemblerTest {

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:17")
    }

    @TestConfiguration
    class FixedClockConfig {
        @Bean
        fun clock(): Clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC)
    }

    @Autowired lateinit var assembler: IndexedMovieAssembler

    @Autowired lateinit var upsertService: MovieUpsertService

    @Autowired lateinit var movieRepository: MovieRepository

    @Autowired lateinit var movieCastRepository: MovieCastRepository

    private fun tmdbMovie(tmdbId: Long) = TmdbMovie(
        tmdbId = tmdbId,
        title = "영화$tmdbId",
        originalTitle = null,
        overview = "줄거리$tmdbId",
        releaseDate = "2008-07-16",
        runtime = 152,
        voteAverage = BigDecimal("8.5"),
        voteCount = 30000,
        posterPath = "/p.jpg",
        backdropPath = "/b.jpg",
        originalLanguage = "ko",
        originCountry = "KR",
        genres = listOf(TmdbGenre(80, "범죄")),
        directors = (1..4).map { TmdbPerson(it.toLong(), "감독$it") },
        cast = (1..6).map { TmdbCastMember(1000L + it, "배우$it", "역할$it", 6 - it) }, // castOrder 역순
    )

    @Test
    fun `assembleAll — payload의 castIds·directorIds가 PG 전체 목록과 일치한다`() {
        // given — 예전 상한(take 5/take 3)보다 많은 자식
        val movieId = upsertService.upsert(tmdbMovie(155L))

        // when
        val indexed = assembler.assembleAll()

        // then
        val movie = indexed.single()
        assertThat(movie.movieId).isEqualTo(movieId)
        assertThat(movie.payload.castIds).containsExactly(1006L, 1005L, 1004L, 1003L, 1002L, 1001L) // castOrder asc 전체
        assertThat(movie.payload.castIds)
            .isEqualTo(movieCastRepository.findAllByMovieId(movieId).sortedBy { it.castOrder }.map { it.personId })
        assertThat(movie.payload.directorIds).containsExactly(1L, 2L, 3L, 4L)
        assertThat(movie.embeddingInput).contains("줄거리")
    }

    @Test
    fun `assemble — 지정한 movieId만 조립하고 overview 없는 영화는 스킵한다`() {
        // given
        val withOverview = upsertService.upsert(tmdbMovie(155L))
        val withoutOverview = requireNotNull(
            movieRepository.save(MovieEntity(tmdbId = 156L, title = "줄거리 없음", overview = null)).movieId,
        )

        // when
        val indexed = assembler.assemble(listOf(withOverview, withoutOverview))

        // then
        assertThat(indexed.map { it.movieId }).containsExactly(withOverview)
    }
}
