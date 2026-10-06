package com.whsanha55.cineseek.movie.repository

import com.whsanha55.cineseek.global.config.ClockConfig
import com.whsanha55.cineseek.global.config.JpaConfig
import com.whsanha55.cineseek.movie.entity.GenreEntity
import com.whsanha55.cineseek.movie.entity.MovieCastEntity
import com.whsanha55.cineseek.movie.entity.MovieDirectorEntity
import com.whsanha55.cineseek.movie.entity.MovieEntity
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
import java.math.BigDecimal
import java.time.LocalDate

/**
 * 엔티티-스키마 일치(ddl-auto: validate) + 저장/조회/명시적 삭제 검증.
 * Flyway가 컨테이너 PG에 V1 스키마를 만들고, Hibernate validate가 엔티티와 대조한다
 */
@DataJpaTest
@Import(JpaConfig::class, ClockConfig::class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class MovieRepositoryTest {

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:17")
    }

    @Autowired lateinit var movieRepository: MovieRepository

    @Autowired lateinit var genreRepository: GenreRepository

    @Autowired lateinit var movieDirectorRepository: MovieDirectorRepository

    @Autowired lateinit var movieCastRepository: MovieCastRepository

    @Test
    fun `영화 저장 후 tmdbId로 조회 — 장르 조인과 자식 라운드트립`() {
        // given
        val genre = genreRepository.save(GenreEntity(genreId = 80, name = "범죄"))
        val saved = movieRepository.save(
            MovieEntity(
                tmdbId = 155L,
                title = "다크 나이트",
                overview = "배트맨이 조커를 막는 이야기",
                releaseDate = LocalDate.of(2008, 7, 16),
                releaseYear = 2008,
                voteAverage = BigDecimal("8.5"),
            ).apply { replaceGenres(listOf(genre)) },
        )
        val movieId = saved.movieId!!
        movieDirectorRepository.save(MovieDirectorEntity(movieId = movieId, personId = 525L, name = "크리스토퍼 놀란"))
        movieCastRepository.save(
            MovieCastEntity(
                movieId = movieId,
                personId = 3895L,
                name = "크리스찬 베일",
                character = "브루스 웨인",
                castOrder = 0,
            ),
        )

        // when
        val found = movieRepository.findByTmdbId(155L)!!
        val directors = movieDirectorRepository.findAllByMovieId(movieId)
        val cast = movieCastRepository.findAllByMovieId(movieId).single()

        // then
        assertThat(found.title).isEqualTo("다크 나이트")
        assertThat(found.genres).containsExactly(genre)
        assertThat(found.releaseYear).isEqualTo(2008)
        assertThat(found.createdAt).isNotNull()
        assertThat(directors.map { it.name }).containsExactly("크리스토퍼 놀란")
        assertThat(cast.character).isEqualTo("브루스 웨인")
        assertThat(cast.castOrder).isEqualTo(0)
    }

    @Test
    fun `deleteAllByMovieId가 자식을 명시적으로 삭제한다`() {
        // given
        val movieId = movieRepository.save(MovieEntity(tmdbId = 155L, title = "다크 나이트")).movieId!!
        movieDirectorRepository.save(MovieDirectorEntity(movieId = movieId, personId = 525L, name = "크리스토퍼 놀란"))
        movieCastRepository.save(MovieCastEntity(movieId = movieId, personId = 3895L, name = "크리스찬 베일"))

        // when
        movieDirectorRepository.deleteAllByMovieId(movieId)
        movieCastRepository.deleteAllByMovieId(movieId)

        // then
        assertThat(movieDirectorRepository.count()).isZero()
        assertThat(movieCastRepository.count()).isZero()
    }
}
