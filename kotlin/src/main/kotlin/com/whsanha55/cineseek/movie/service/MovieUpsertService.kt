package com.whsanha55.cineseek.movie.service

import com.whsanha55.cineseek.movie.entity.GenreEntity
import com.whsanha55.cineseek.movie.entity.MovieCastEntity
import com.whsanha55.cineseek.movie.entity.MovieDirectorEntity
import com.whsanha55.cineseek.movie.entity.MovieEntity
import com.whsanha55.cineseek.movie.repository.GenreRepository
import com.whsanha55.cineseek.movie.repository.MovieCastRepository
import com.whsanha55.cineseek.movie.repository.MovieDirectorRepository
import com.whsanha55.cineseek.movie.repository.MovieRepository
import com.whsanha55.cineseek.movie.vo.TmdbMovie
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant

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
    private val clock: Clock,
) {

    @Transactional
    fun upsert(m: TmdbMovie): Long {
        val now = Instant.now(clock)
        val saved = movieRepository.findByTmdbId(m.tmdbId)
            ?.apply { updateFrom(m, now) }
            ?: movieRepository.save(m.toEntity(now))
        val movieId = requireNotNull(saved.movieId) { "저장 후 movieId가 없다. tmdbId=${m.tmdbId}" }

        // 장르 — 마스터는 ON CONFLICT DO NOTHING과 동일하게 이름 갱신 없음
        saved.replaceGenres(
            m.genres.map { g ->
                genreRepository.findById(g.genreId)
                    .orElseGet { genreRepository.save(GenreEntity(genreId = g.genreId, name = g.name)) }
            },
        )
        movieRepository.save(saved)

        // 자식 — 기존 것을 지우고 다시 채운다 (명시적 삭제, 출연진은 상위 10명)
        movieDirectorRepository.deleteAllByMovieId(movieId)
        movieCastRepository.deleteAllByMovieId(movieId)
        movieDirectorRepository.saveAll(
            m.directors.map { MovieDirectorEntity(movieId = movieId, personId = it.personId, name = it.name) },
        )
        movieCastRepository.saveAll(
            m.cast.take(CAST_LIMIT).map {
                MovieCastEntity(
                    movieId = movieId,
                    personId = it.personId,
                    name = it.name,
                    character = it.character,
                    castOrder = it.castOrder,
                )
            },
        )
        return movieId
    }

    private fun TmdbMovie.toEntity(now: Instant) = MovieEntity(
        tmdbId = tmdbId,
        title = title,
        originalTitle = originalTitle,
        overview = overview,
        releaseDate = releaseLocalDate(),
        releaseYear = releaseYear(),
        runtime = runtime,
        voteAverage = voteAverage,
        voteCount = voteCount,
        posterPath = posterPath,
        backdropPath = backdropPath,
        originalLanguage = originalLanguage,
        overviewUpdatedAt = now,
    )

    companion object {
        private const val CAST_LIMIT = 10 // python: cast[:10]
    }
}
