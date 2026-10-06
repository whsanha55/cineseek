package com.whsanha55.cineseek.search.service

import com.whsanha55.cineseek.movie.entity.MovieEntity
import com.whsanha55.cineseek.movie.repository.MovieCastRepository
import com.whsanha55.cineseek.movie.repository.MovieDirectorRepository
import com.whsanha55.cineseek.movie.repository.MovieRepository
import com.whsanha55.cineseek.search.vo.EmbeddingText
import com.whsanha55.cineseek.search.vo.IndexedMovie
import com.whsanha55.cineseek.search.vo.MoviePayload
import org.springframework.stereotype.Component

/**
 * PG(SoT)에서 색인 단위(IndexedMovie) 조립 — ReindexJob의 조립 로직을 추출한 것.
 * overview가 없는 영화는 임베딩이 불가해 스킵하고, castIds/directorIds는 전체 목록을
 * 담는다(PG와 Qdrant 배우 필터가 같은 출연 관계를 반영하도록).
 * 장르명은 nameKo 우선
 */
@Component
class IndexedMovieAssembler(
    private val movieRepository: MovieRepository,
    private val movieDirectorRepository: MovieDirectorRepository,
    private val movieCastRepository: MovieCastRepository,
) {

    /** 증분 색인 — 이번 틱에서 저장·갱신된 영화만 [movieIds]로 조립한다 */
    fun assemble(movieIds: List<Long>): List<IndexedMovie> = if (movieIds.isEmpty()) {
        emptyList()
    } else {
        movieRepository.findAllById(movieIds).mapNotNull(::toIndexedMovie)
    }

    /** 전체 재색인(ReindexJob)용 — PG의 모든 영화를 조립한다 */
    fun assembleAll(): List<IndexedMovie> = movieRepository.findAll().mapNotNull(::toIndexedMovie)

    private fun toIndexedMovie(movie: MovieEntity): IndexedMovie? {
        val overview = movie.overview ?: return null // 임베딩 불가 → 스킵
        val movieId = requireNotNull(movie.movieId) { "조회한 영화에 movieId가 없다. tmdbId=${movie.tmdbId}" }
        val directors = movieDirectorRepository.findAllByMovieId(movieId)
        val cast = movieCastRepository.findAllByMovieId(movieId)
        return IndexedMovie(
            movieId = movieId,
            embeddingInput = EmbeddingText.assemble(
                title = movie.title,
                originalTitle = movie.originalTitle,
                genreNames = movie.genres.map { it.nameKo ?: it.name },
                moodTags = movie.moodTags?.split(", ")?.filter { it.isNotBlank() } ?: emptyList(),
                moodDesc = movie.moodDesc,
                overview = overview,
            ),
            payload = MoviePayload(
                title = movie.title,
                releaseYear = movie.releaseYear,
                rating = movie.voteAverage?.toDouble(),
                genreIds = movie.genres.map { it.genreId },
                directorIds = directors.map { it.personId },
                castIds = cast.sortedBy { it.castOrder }.map { it.personId },
                runtime = movie.runtime,
                voteCount = movie.voteCount,
            ),
        )
    }
}
