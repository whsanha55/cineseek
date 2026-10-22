package com.whsanha55.cineseek.movie.service

import com.whsanha55.cineseek.movie.repository.MovieRepository
import org.springframework.stereotype.Service

/** movieId↔tmdbId 조회 — eval 라벨(재색인에도 변하지 않는 tmdbId)과 TMDB 실시간 조회(시청처)에 쓴다 */
@Service
class MovieTmdbIdService(private val movieRepository: MovieRepository) {

    /** movieId → tmdbId. PG에 없는 movieId는 빠진다 */
    fun tmdbIds(movieIds: List<Long>): Map<Long, Long> = if (movieIds.isEmpty()) {
        emptyMap()
    } else {
        movieRepository.findAllById(movieIds).associate { requireNotNull(it.movieId) to it.tmdbId }
    }

    /** PG에 존재하는 tmdbId만 남긴다 */
    fun existingTmdbIds(tmdbIds: Collection<Long>): Set<Long> = if (tmdbIds.isEmpty()) {
        emptySet()
    } else {
        movieRepository.findAllByTmdbIdIn(tmdbIds).map { it.tmdbId }.toSet()
    }
}
