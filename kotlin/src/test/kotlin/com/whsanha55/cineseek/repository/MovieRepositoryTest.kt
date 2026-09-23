package com.whsanha55.cineseek.repository

import com.whsanha55.cineseek.domain.Genre
import com.whsanha55.cineseek.domain.Movie
import com.whsanha55.cineseek.domain.MovieCast
import com.whsanha55.cineseek.domain.MovieDirector
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * 엔티티-스키마 일치(ddl-auto: validate) + 저장/조회/명시적 삭제 검증.
 * Flyway가 컨테이너 PG에 V1 스키마를 만들고, Hibernate validate가 엔티티와 대조한다
 */
@DataJpaTest
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
		val genre = genreRepository.save(Genre(genreId = 80, name = "Crime", nameKo = "범죄"))

		val saved = movieRepository.save(
			Movie(
				tmdbId = 155L,
				title = "다크 나이트",
				overview = "배트맨이 조커를 막는 이야기",
				releaseDate = LocalDate.of(2008, 7, 16),
				releaseYear = 2008,
				voteAverage = BigDecimal("8.5"),
			).apply { genres += genre },
		)
		movieDirectorRepository.save(MovieDirector(saved.movieId, 525L, "크리스토퍼 놀란"))
		movieCastRepository.save(
			MovieCast(saved.movieId, 3895L, "크리스찬 베일", character = "브루스 웨인", castOrder = 0),
		)

		val found = assertNotNull(movieRepository.findByTmdbId(155L))

		assertEquals("다크 나이트", found.title)
		assertEquals(setOf(genre), found.genres)
		assertEquals(2008, found.releaseYear)

		val directors = movieDirectorRepository.findAllByMovieId(found.movieId)
		assertEquals(listOf("크리스토퍼 놀란"), directors.map { it.name })

		val cast = movieCastRepository.findAllByMovieId(found.movieId).single()
		assertEquals("브루스 웨인", cast.character)
		assertEquals(0, cast.castOrder)
	}

	@Test
	fun `deleteAllByMovieId가 자식을 명시적으로 삭제한다`() {
		val movie = movieRepository.save(Movie(tmdbId = 155L, title = "다크 나이트"))
		movieDirectorRepository.save(MovieDirector(movie.movieId, 525L, "크리스토퍼 놀란"))
		movieCastRepository.save(MovieCast(movie.movieId, 3895L, "크리스찬 베일"))

		movieDirectorRepository.deleteAllByMovieId(movie.movieId)
		movieCastRepository.deleteAllByMovieId(movie.movieId)

		assertEquals(0L, movieDirectorRepository.count())
		assertEquals(0L, movieCastRepository.count())
	}
}
