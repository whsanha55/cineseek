package com.whsanha55.cineseek.service

import com.whsanha55.cineseek.repository.MovieCastRepository
import com.whsanha55.cineseek.repository.MovieDirectorRepository
import com.whsanha55.cineseek.repository.MovieRepository
import com.whsanha55.cineseek.client.TmdbCastMember
import com.whsanha55.cineseek.client.TmdbGenre
import com.whsanha55.cineseek.client.TmdbMovie
import com.whsanha55.cineseek.client.TmdbPerson
import java.math.BigDecimal
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Import
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

/** MovieUpsertService — pipeline.upsert_pg 이식 검증 (신규/재 upsert, 자식 교체) */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(MovieUpsertService::class)
@Testcontainers
class MovieUpsertServiceTest {

	companion object {
		@Container
		@ServiceConnection
		@JvmStatic
		val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:17")
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
		val movieId = service.upsert(tmdbMovie())

		val found = movieRepository.findByTmdbId(155L)!!
		assertEquals("다크 나이트", found.title)
		assertEquals(2008, found.releaseYear)
		assertEquals(setOf(80L, 28L), found.genres.map { it.genreId }.toSet())
		assertEquals(listOf("크리스토퍼 놀란"), movieDirectorRepository.findAllByMovieId(movieId).map { it.name })
		assertEquals(listOf("크리스찬 베일"), movieCastRepository.findAllByMovieId(movieId).map { it.name })
	}

	@Test
	fun `재 upsert — 같은 movie_id에 갱신, 자식 교체, 출연진은 상위 10명만`() {
		val manyCast = (1..12).map { TmdbCastMember(it.toLong(), "배우$it", "역할$it", it - 1) }
		val first = service.upsert(tmdbMovie())
		val second = service.upsert(tmdbMovie(title = "다크 나이트 디럭스", cast = manyCast))

		assertEquals(first, second) // 같은 movie_id — idempotent
		assertEquals("다크 나이트 디럭스", movieRepository.findByTmdbId(155L)!!.title)
		assertEquals(10, movieCastRepository.findAllByMovieId(second).size) // 상위 10 제한
		assertEquals(1L, movieDirectorRepository.count()) // 지우고 다시 삽입 — 1명 유지
	}
}
