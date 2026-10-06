package com.whsanha55.cineseek.collect.service

import com.whsanha55.cineseek.collect.config.CollectProperties
import com.whsanha55.cineseek.collect.entity.CollectStateEntity
import com.whsanha55.cineseek.collect.enums.CollectModeEnum
import com.whsanha55.cineseek.collect.enums.CollectTaskStatusEnum.FAILED
import com.whsanha55.cineseek.collect.enums.CollectTaskStatusEnum.PENDING
import com.whsanha55.cineseek.collect.enums.CollectTaskStatusEnum.RUNNING
import com.whsanha55.cineseek.collect.enums.CollectTaskTypeEnum
import com.whsanha55.cineseek.collect.enums.CollectTaskTypeEnum.KR_DISCOVER
import com.whsanha55.cineseek.collect.enums.CollectTaskTypeEnum.MOVIE_DETAIL
import com.whsanha55.cineseek.collect.enums.CollectTaskTypeEnum.PERSON_FILMO
import com.whsanha55.cineseek.collect.repository.CollectStateRepository
import com.whsanha55.cineseek.external.tmdb.client.TmdbClient
import com.whsanha55.cineseek.movie.repository.MovieRepository
import com.whsanha55.cineseek.movie.repository.PersonRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.data.domain.PageRequest
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicBoolean

private val log = KotlinLogging.logger {}

/**
 * 수집 스케줄러 — interval 틱(큐 소진)과 일일 증분 틱을 운영한다.
 * BOOTSTRAP 모드에서는 초기 enqueue(1회) 후 큐를 반복 소진해, PENDING/RUNNING이 비면 DAILY로 전환한다.
 * DAILY 모드에서는 매일 정시에 신작 창·만료 필모·줄거리 누락·재시도 소진 실패를 다시 큐에 올린다.
 * cineseek.collect.enabled=false(기본)면 빈 자체가 등록되지 않는다
 */
@Component
@ConditionalOnProperty(prefix = "cineseek.collect", name = ["enabled"], havingValue = "true")
class CollectScheduler(
    private val pipeline: CollectPipeline,
    private val taskService: CollectTaskService,
    private val collectStateRepository: CollectStateRepository,
    private val personRepository: PersonRepository,
    private val movieRepository: MovieRepository,
    private val tmdbClient: TmdbClient,
    private val statsService: CollectStatsService,
    private val properties: CollectProperties,
    private val clock: Clock,
) {

    /** 틱 중복 실행 가드 — 스케줄러 스레드 풀이 넓어져도 이 프로세스에서는 틱이 한 번에 하나만 돈다 */
    private val ticking = AtomicBoolean(false)

    /** 수집 틱 — 만료 lease 회수 → bootstrap enqueue(BOOTSTRAP일 때만) → 배치 실행 → 잔여가 없으면 DAILY 전환 */
    @Scheduled(fixedDelayString = "\${cineseek.collect.interval}")
    fun tick() {
        runGuarded { runTick() }
    }

    /** 일일 증분 틱 — DAILY 모드일 때만 재등록 후 배치를 돌린다 */
    @Scheduled(cron = "\${cineseek.collect.daily-cron}", zone = "\${cineseek.collect.zone}")
    fun dailyTick() {
        runGuarded { runDailyTick() }
    }

    private inline fun runGuarded(block: () -> Unit) {
        if (!ticking.compareAndSet(false, true)) {
            log.warn { "이전 틱이 아직 실행 중 — 이번 틱을 건너뛴다" }
            return
        }
        try {
            block()
        } finally {
            ticking.set(false)
        }
    }

    private fun runTick() {
        // 단일 행 상태 — 없으면 만들어 넣는다(V3 마이그레이션이 미리 넣어 두지만 방어적으로)
        val state = collectStateRepository
            .findById(CollectStateEntity.SINGLETON_ID)
            .orElseGet { collectStateRepository.save(CollectStateEntity()) }
        taskService.recoverExpiredLeases()
        if (state.mode == CollectModeEnum.BOOTSTRAP) {
            pipeline.enqueueBootstrapIfNotStarted()
        }
        val result = pipeline.runBatch() // 내부에서 lease 회수가 한 번 더 실행되지만 직전에 비웠으니 무해하다
        if (finishTick(state, result)) {
            log.info { "bootstrap 완료 — 잔여 작업이 없어 일일 증분(DAILY) 모드로 전환한다" }
        }
        logStats(result)
    }

    private fun runDailyTick() {
        // 단일 행 상태 — 없으면 만들어 넣는다(V3 마이그레이션이 미리 넣어 두지만 방어적으로)
        val state = collectStateRepository
            .findById(CollectStateEntity.SINGLETON_ID)
            .orElseGet { collectStateRepository.save(CollectStateEntity()) }
        if (state.mode != CollectModeEnum.DAILY) {
            log.info { "아직 ${state.mode} 모드 — 일일 증분을 건너뛴다" }
            return
        }
        val freshMovies = requeueFreshMovies()
        val expiredFilmos = requeueExpiredFilmographies()
        val missingOverviews = requeueMissingOverviews()
        val resetFailed = taskService.resetRetryExhausted()
        val result = pipeline.runBatch()
        finishTick(state, result)
        log.info {
            "일일 증분 틱 완료. 신작등록=$freshMovies, 만료필모=$expiredFilmos, " +
                "줄거리누락=$missingOverviews, 실패복귀=$resetFailed, ${result.summary()}"
        }
        logStats(result)
    }

    /** 틱 마무리 — last_run_at/last_summary 기록. BOOTSTRAP에서 잔여 작업이 없으면 DAILY로 전환하고 true를 돌려준다 */
    private fun finishTick(state: CollectStateEntity, result: BatchResult): Boolean {
        val now = Instant.now(clock)
        val transitioned = state.mode == CollectModeEnum.BOOTSTRAP && !taskService.hasRemainingWork()
        if (transitioned) {
            state.transitionTo(CollectModeEnum.DAILY, now)
        }
        state.recordTick(now, result.summary())
        collectStateRepository.save(state)
        return transitioned
    }

    /**
     * 신작 등록 — 최근 2년 한국 연도 구간(기준별)을 되살려 다시 수집하게 하고,
     * 전 세계 인기 첫 페이지의 영화를 상세 수집 큐에 멱등 등록한다. 새로 등록된 작업 수를 돌려준다
     */
    private fun requeueFreshMovies(): Int {
        val today = LocalDate.now(clock.withZone(ZoneId.of(properties.zone)))
        val krWindows = CollectPipeline.KR_CRITERIA.sumOf { criterion ->
            KrWindow.yearly(today, RECENT_YEARS_BACK, criterion).count { window ->
                requeueOrCreate(KR_DISCOVER, window.payloadKey())
            }
        }
        val globalFirstPage = runCatching {
            TmdbClient.GLOBAL_DISCOVER_SOURCES.sumOf { params ->
                tmdbClient.fetchDiscoverIds(params, startPage = 1, endPage = 1).tmdbIds.count { tmdbId ->
                    val payloadKey = "${CollectPipeline.MOVIE_PREFIX}$tmdbId"
                    taskService.enqueue(MOVIE_DETAIL, payloadKey, CollectPipeline.PRIORITY_NORMAL)
                }
            }
        }.onFailure { log.error(it) { "전 세계 인기 첫 페이지 수집 실패 — 나머지 일일 증분은 계속한다" } }
            .getOrDefault(0)
        return krWindows + globalFirstPage
    }

    /** 필모그래피 만료 인물 — filmoTtlDays 넘게 확인하지 않은 person을 다시 수집한다 */
    private fun requeueExpiredFilmographies(): Int {
        val threshold = Instant.now(clock).minus(Duration.ofDays(properties.filmoTtlDays.toLong()))
        return personRepository.findByFilmoCheckedAtBefore(threshold).count { person ->
            requeueOrCreate(PERSON_FILMO, "${CollectPipeline.PERSON_PREFIX}${person.personId}")
        }
    }

    /** 줄거리 누락 영화 — 오래된 순 최대 200건을 상세 재수집해 overview를 다시 채운다 */
    private fun requeueMissingOverviews(): Int =
        movieRepository.findTmdbIdsByOverviewIsNull(PageRequest.ofSize(MISSING_OVERVIEW_LIMIT))
            .count { tmdbId -> requeueOrCreate(MOVIE_DETAIL, "${CollectPipeline.MOVIE_PREFIX}$tmdbId") }

    /** DONE 작업은 되살리고(PENDING), 행이 없으면 새로 등록한다. 대기/실행 중이면 아무것도 하지 않는다 */
    private fun requeueOrCreate(type: CollectTaskTypeEnum, payloadKey: String): Boolean =
        taskService.requeue(type, payloadKey) || taskService.enqueue(type, payloadKey, CollectPipeline.PRIORITY_NORMAL)

    /** 틱 통계 — 큐·영화·인물·색인 현황을 키=값으로 남긴다. 집계 실패가 틱을 죽이지 않게 감싼다 */
    private fun logStats(result: BatchResult) {
        val snapshot = runCatching { statsService.snapshot() }
            .onFailure { log.warn(it) { "통계 집계 실패" } }
            .getOrNull() ?: return
        log.info {
            "수집 통계. ${result.summary()}, 대기=${snapshot.count(PENDING)}, 실행중=${snapshot.count(RUNNING)}, " +
                "실패=${snapshot.count(FAILED)}, 줄거리누락=${snapshot.missingOverviewCount}, " +
                "PG영화=${snapshot.pgMovieCount}, 색인미완료=${snapshot.unindexedCount ?: "n/a"}, " +
                "한국영화(연도:수)=${snapshot.krMoviesByYear.entries.joinToString(",") { "${it.key}:${it.value}" }}, " +
                "seed필모=${snapshot.seedStoredTotal}/${snapshot.seedExternalTotal}"
        }
    }

    companion object {
        private const val RECENT_YEARS_BACK = 2 // 일일 증분이 되살리는 신작 창(연도 수)
        private const val MISSING_OVERVIEW_LIMIT = 200 // 일일 틱당 줄거리 누락 재수집 상한
    }
}
