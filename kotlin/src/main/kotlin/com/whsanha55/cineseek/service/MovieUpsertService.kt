package com.whsanha55.cineseek.service

import com.whsanha55.cineseek.client.TmdbMovie
import com.whsanha55.cineseek.domain.Movie
import com.whsanha55.cineseek.domain.MovieCast
import com.whsanha55.cineseek.domain.MovieDirector
import com.whsanha55.cineseek.repository.GenreRepository
import com.whsanha55.cineseek.repository.MovieCastRepository
import com.whsanha55.cineseek.repository.MovieDirectorRepository
import com.whsanha55.cineseek.repository.MovieRepository
import java.time.Instant
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * pipeline.upsert_pg 이식 — JPA upsert. 영화 한 건당 트랜잭션 하나.
 * 자식(감독·출연진)은 cascade 없이 명시적으로 지우고 다시 삽입한다.
 * 갱신 필드는 python ON CONFLICT DO UPDATE와 같은 집합 (동일성 비교 기준)
 */
@Service
class MovieUpsertService(
	private val movieRepository: MovieRepository,
	private val genreRepository: GenreRepository,
	private val movieDirectorRepository: MovieDirectorRepository,
	private val movieCastRepository: MovieCastRepository,
) {

	@Transactional
	fun upsert(m: TmdbMovie): Long {
		val saved = movieRepository.findByTmdbId(m.tmdbId)
			?.apply { updateFrom(m) }
			?: movieRepository.save(m.toEntity())

		// 장르 — 마스터는 ON CONFLICT DO NOTHING과 동일하게 이름 갱신 없음
		saved.genres.clear()
		m.genres.forEach { g ->
			val genre = genreRepository.findById(g.genreId)
				.orElseGet { genreRepository.save(com.whsanha55.cineseek.domain.Genre(g.genreId, g.name)) }
			saved.genres += genre
		}
		movieRepository.save(saved)

		// 자식 — 기존 것을 지우고 다시 채운다 (명시적 삭제, 출연진은 상위 10명)
		movieDirectorRepository.deleteAllByMovieId(saved.movieId)
		movieCastRepository.deleteAllByMovieId(saved.movieId)
		movieDirectorRepository.saveAll(m.directors.map { MovieDirector(saved.movieId, it.personId, it.name) })
		movieCastRepository.saveAll(
			m.cast.take(CAST_LIMIT).map { MovieCast(saved.movieId, it.personId, it.name, it.character, it.castOrder) },
		)
		return saved.movieId
	}

	private fun TmdbMovie.toEntity() = Movie(
		tmdbId = tmdbId,
		title = title,
		originalTitle = originalTitle,
		overview = overview,
		releaseDate = releaseDate?.takeIf { it.isNotBlank() }?.let(java.time.LocalDate::parse),
		releaseYear = releaseDate?.take(4)?.toIntOrNull(),
		runtime = runtime,
		voteAverage = voteAverage,
		voteCount = voteCount,
		posterPath = posterPath,
		backdropPath = backdropPath,
		originalLanguage = originalLanguage,
		overviewUpdatedAt = Instant.now(),
	)

	/** python DO UPDATE SET 절과 같은 필드만 갱신 */
	private fun Movie.updateFrom(m: TmdbMovie) {
		title = m.title
		overview = m.overview
		releaseDate = m.releaseDate?.takeIf { it.isNotBlank() }?.let(java.time.LocalDate::parse)
		releaseYear = m.releaseDate?.take(4)?.toIntOrNull()
		runtime = m.runtime
		voteAverage = m.voteAverage
		voteCount = m.voteCount
		posterPath = m.posterPath
		overviewUpdatedAt = Instant.now()
	}

	companion object {
		private const val CAST_LIMIT = 10 // python: cast[:10]
	}
}
