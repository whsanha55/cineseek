package com.whsanha55.cineseek.search.job

import com.whsanha55.cineseek.external.tmdb.client.TmdbClient
import com.whsanha55.cineseek.movie.service.MovieMoodTagger
import com.whsanha55.cineseek.movie.service.MovieUpsertService
import com.whsanha55.cineseek.movie.vo.TmdbMovie
import com.whsanha55.cineseek.search.service.IndexedMovieAssembler
import com.whsanha55.cineseek.search.service.MovieIndexer
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.util.concurrent.Callable
import java.util.concurrent.Executors
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
    private val upsertService: MovieUpsertService,
    private val assembler: IndexedMovieAssembler,
    private val moodTagger: MovieMoodTagger,
    private val indexer: MovieIndexer,
) : ApplicationRunner {

    override fun run(args: ApplicationArguments) {
        val ids = tmdbClient.fetchTmdbIds()
        log.info { "TMDB 후보 수집. ids=${ids.size}" }

        val movies = fetchDetailsParallel(ids)
        log.info { "상세 수집 완료. overview 있는 영화=${movies.size}" }

        val stored = movies.mapNotNull { upsertSafe(it) }
        log.info { "PG 적재 완료. stored=${stored.size}" }

        val tagged = moodTagger.tagAll()
        log.info { "분위기 태그. tagged=$tagged" }

        val indexed = assembler.assembleAll()
        val embedded = indexer.index(indexed)
        log.info { "재색인 완료. PG movie=${stored.size}, 색인 대상=${indexed.size}, 임베딩=$embedded" }
        exitProcess(0) // 배치 성격 — 출력 후 종료
    }

    /**
     * detail + credits 병렬 수집 — 단건 실패는 건너뛴다 (python fetch_detail_safe).
     * 동시 요청은 스레드 수로 제한한다 — 무제한이면 TMDB rate limit(429)에 대부분 막힌다.
     */
    private fun fetchDetailsParallel(ids: List<Long>): List<TmdbMovie> =
        Executors.newFixedThreadPool(FETCH_CONCURRENCY).use { executor ->
            executor.invokeAll(ids.map { id -> Callable { runCatching { tmdbClient.fetchDetail(id) } } })
        }.mapNotNull { future ->
            future.get()
                .onFailure { log.warn(it) { "수집 스킵" } }
                .getOrNull()
        }

    /** 한 건이 실패해도 나머지는 계속 (영화 한 건당 트랜잭션) */
    private fun upsertSafe(m: TmdbMovie): Long? = runCatching { upsertService.upsert(m) }
        .onFailure { log.warn(it) { "upsert 스킵. tmdbId=${m.tmdbId}" } }
        .getOrNull()

    companion object {
        private const val FETCH_CONCURRENCY = 4 // 영화 1건 = 요청 2개(detail, credits)
    }
}
