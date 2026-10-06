package com.whsanha55.cineseek.movie.service

import com.whsanha55.cineseek.global.config.JpaConfig
import com.whsanha55.cineseek.movie.repository.MovieCastRepository
import com.whsanha55.cineseek.movie.repository.MovieDirectorRepository
import com.whsanha55.cineseek.movie.repository.MovieRepository
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

/** MovieUpsertService — pipeline.upsert_pg 이식 검증 (신규/재 upsert, 자식 교체) */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(MovieUpsertService::class, JpaConfig::class, MovieUpsertServiceTest.FixedClockConfig::class)
@Testcontainers
class MovieUpsertServiceTest {

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:17")

        val NOW: Instant = Instant.parse("2026-01-01T00:00:00Z")
    }

    @TestConfiguration
    class FixedClockConfig {
        @Bean
        fun clock(): Clock = Clock.fixed(NOW, ZoneOffset.UTC)
    }

    @Autowired lateinit var service: MovieUpsertService

    @Autowired lateinit var movieRepository: MovieRepository

    @Autowired lateinit var movieDirectorRepository: MovieDirectorRepository

    @Autowired lateinit var movieCastRepository: MovieCastRepository

    private fun tmdbMovie(
        title: String = "다크 나이트",
        cast: List<TmdbCastMember> = listOf(TmdbCastMember(3895, "크리스찬 베일", "브루스 웨인", 0)),
    ) = TmdbMovie(
        tmdbId = 155L,
        title = title,
        originalTitle = "The Dark Knight",
        overview = "배트맨이 조커를 막는 이야기",
        releaseDate = "2008-07-16",
        runtime = 152,
        voteAverage = BigDecimal("8.5"),
        voteCount = 30000,
        posterPath = "/p.jpg",
        backdropPath = "/b.jpg",
        originalLanguage = "en",
        genres = listOf(TmdbGenre(80, "범죄"), TmdbGenre(28, "액션")),
        directors = listOf(TmdbPerson(525, "크리스토퍼 놀란")),
        cast = cast,
    )

    @Test
    fun `신규 영화 upsert — 메타·장르·감독·출연진 저장`() {
        // when
        val movieId = service.upsert(tmdbMovie())

        // then
        val found = movieRepository.findByTmdbId(155L)!!
        assertThat(found.title).isEqualTo("다크 나이트")
        assertThat(found.releaseYear).isEqualTo(2008)
        assertThat(found.overviewUpdatedAt).isEqualTo(NOW)
        assertThat(found.createdAt).isEqualTo(NOW)
        assertThat(found.genres.map { it.genreId }).containsExactlyInAnyOrder(80L, 28L)
        assertThat(movieDirectorRepository.findAllByMovieId(movieId).map { it.name }).containsExactly("크리스토퍼 놀란")
        assertThat(movieCastRepository.findAllByMovieId(movieId).map { it.name }).containsExactly("크리스찬 베일")
    }

    @Test
    fun `재 upsert — 같은 movie_id에 갱신, 자식 교체, 출연진은 전체 저장`() {
        // given
        val manyCast = (1..12).map { TmdbCastMember(it.toLong(), "배우$it", "역할$it", it - 1) }
        val first = service.upsert(tmdbMovie())

        // when
        val second = service.upsert(tmdbMovie(title = "다크 나이트 디럭스", cast = manyCast))

        // then
        assertThat(second).isEqualTo(first) // 같은 movie_id — idempotent
        assertThat(movieRepository.findByTmdbId(155L)!!.title).isEqualTo("다크 나이트 디럭스")
        assertThat(movieCastRepository.findAllByMovieId(second)).hasSize(12) // 11번째 이후도 저장
        assertThat(movieDirectorRepository.count()).isEqualTo(1L) // 지우고 다시 삽입 — 1명 유지
    }

    @Test
    fun `동일 인물 다중 배역 — 첫 배역만 저장해 (movie_id, person_id) 위반을 막는다`() {
        // given — 같은 personId가 두 배역으로 내려온다
        val duplicated = listOf(
            TmdbCastMember(3895L, "크리스찬 베일", "브루스 웨인", 0),
            TmdbCastMember(3895L, "크리스찬 베일", "배트맨 목소리", 5),
            TmdbCastMember(3896L, "마이클 케인", "알프레드", 1),
        )

        // when
        val movieId = service.upsert(tmdbMovie(cast = duplicated))

        // then
        val cast = movieCastRepository.findAllByMovieId(movieId)
        assertThat(cast.map { it.personId }).containsExactly(3895L, 3896L) // 첫 배역(주연)만
        assertThat(cast.first { it.personId == 3895L }.character).isEqualTo("브루스 웨인")
    }

    @Test
    fun `mood 태그 저장 후 재 upsert — TMDB 갱신이 mood 필드를 건드리지 않는다`() {
        // given
        service.upsert(tmdbMovie())
        movieRepository.findByTmdbId(155L)!!.applyMood("다크,긴장,히어로", "치열한 대결", "gpt-4o-mini/abc12345", NOW)

        // when
        service.upsert(tmdbMovie(title = "다크 나이트 디럭스"))

        // then
        val found = movieRepository.findByTmdbId(155L)!!
        assertThat(found.title).isEqualTo("다크 나이트 디럭스") // TMDB 필드는 갱신
        assertThat(found.moodTags).isEqualTo("다크,긴장,히어로")
        assertThat(found.moodDesc).isEqualTo("치열한 대결")
        assertThat(found.moodModel).isEqualTo("gpt-4o-mini/abc12345")
        assertThat(found.moodTaggedAt).isEqualTo(NOW)
    }
}
