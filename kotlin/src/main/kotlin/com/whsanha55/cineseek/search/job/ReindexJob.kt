package com.whsanha55.cineseek.search.job

import com.whsanha55.cineseek.external.tmdb.client.TmdbClient
import com.whsanha55.cineseek.external.tmdb.config.TmdbProperties
import com.whsanha55.cineseek.movie.repository.MovieCastRepository
import com.whsanha55.cineseek.movie.repository.MovieDirectorRepository
import com.whsanha55.cineseek.movie.repository.MovieRepository
import com.whsanha55.cineseek.movie.service.MovieMoodTagger
import com.whsanha55.cineseek.movie.service.MovieUpsertService
import com.whsanha55.cineseek.movie.vo.TmdbMovie
import com.whsanha55.cineseek.search.service.MovieIndexer
import com.whsanha55.cineseek.search.vo.EmbeddingText
import com.whsanha55.cineseek.search.vo.IndexedMovie
import com.whsanha55.cineseek.search.vo.MoviePayload
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import kotlin.system.exitProcess

private val log = KotlinLogging.logger {}

/**
 * 재색인 배치 — 수집(TMDB) → PG(SoT) → 임베딩 → Qdrant (pipeline.main 이식).
 * 실행: docker compose run --rm api --cineseek.job=reindex
 */
@Component
@ConditionalOnProperty(prefix = "cineseek", name = ["job"], havingValue = "reindex")
class ReindexJob(
    private val tmdbClient: TmdbClient,
    private val tmdbProperties: TmdbProperties,
    private val upsertService: MovieUpsertService,
    private val movieRepository: MovieRepository,
    private val movieDirectorRepository: MovieDirectorRepository,
    private val movieCastRepository: MovieCastRepository,
    private val moodTagger: MovieMoodTagger,
    private val indexer: MovieIndexer,
) : ApplicationRunner {

    override fun run(args: ApplicationArguments) {
        val ids = tmdbClient.fetchTmdbIds(tmdbProperties.pages)
        log.info { "TMDB 후보 수집. ids=${ids.size}, pages=${tmdbProperties.pages}" }

        val movies = fetchDetailsParallel(ids)
        log.info { "상세 수집 완료(가상 스레드). overview 있는 영화=${movies.size}" }

        val stored = movies.mapNotNull { upsertSafe(it) }
        log.info { "PG 적재 완료. stored=${stored.size}" }

        val tagged = moodTagger.tagAll()
        log.info { "분위기 태그. tagged=$tagged" }

        val indexed = buildIndexedMovies()
        val embedded = indexer.index(indexed)
        log.info { "재색인 완료. PG movie=${stored.size}, 색인 대상=${indexed.size}, 임베딩=$embedded" }
        exitProcess(0) // 배치 성격 — 출력 후 종료
    }

    /**
     * 가상 스레드로 detail + credits 병렬 수집 — 단건 실패는 건너뛴다 (python fetch_detail_safe).
     * 동시 요청은 Semaphore로 제한한다 — 무제한이면 TMDB rate limit(429)에 대부분 막힌다
     */
    private fun fetchDetailsParallel(ids: List<Long>): List<TmdbMovie> {
        val permits = Semaphore(FETCH_CONCURRENCY)
        return Executors.newVirtualThreadPerTaskExecutor().use { executor ->
            executor.invokeAll(
                ids.map { id ->
                    Callable {
                        permits.acquire()
                        try {
                            runCatching { tmdbClient.fetchDetail(id) }
                        } finally {
                            permits.release()
                        }
                    }
                },
            )
        }.mapNotNull { future ->
            future.get()
                .onFailure { log.warn(it) { "수집 스킵" } }
                .getOrNull()
        }
    }

    /** 한 건이 실패해도 나머지는 계속 (영화 한 건당 트랜잭션) */
    private fun upsertSafe(m: TmdbMovie): Long? = runCatching { upsertService.upsert(m) }
        .onFailure { log.warn(it) { "upsert 스킵. tmdbId=${m.tmdbId}" } }
        .getOrNull()

    /** PG에서 임베딩 입력(EmbeddingText)과 payload를 조립 — SoT 기준. 장르명은 nameKo 우선 */
    private fun buildIndexedMovies(): List<IndexedMovie> = movieRepository.findAll().mapNotNull { movie ->
        val overview = movie.overview ?: return@mapNotNull null // 임베딩 불가 → 스킵
        val movieId = requireNotNull(movie.movieId) { "조회한 영화에 movieId가 없다. tmdbId=${movie.tmdbId}" }
        val directors = movieDirectorRepository.findAllByMovieId(movieId)
        val cast = movieCastRepository.findAllByMovieId(movieId)
        IndexedMovie(
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
                directorIds = directors.map { it.personId }.take(3),
                castIds = cast.sortedBy { it.castOrder }.map { it.personId }.take(5),
                runtime = movie.runtime,
                voteCount = movie.voteCount,
            ),
        )
    }

    companion object {
        private const val FETCH_CONCURRENCY = 4 // 영화 1건 = 요청 2개(detail, credits)
    }
}
