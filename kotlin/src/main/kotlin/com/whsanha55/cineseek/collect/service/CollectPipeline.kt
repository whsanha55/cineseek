package com.whsanha55.cineseek.collect.service

import com.whsanha55.cineseek.collect.config.CollectProperties
import com.whsanha55.cineseek.collect.entity.CollectStateEntity
import com.whsanha55.cineseek.collect.entity.CollectTaskEntity
import com.whsanha55.cineseek.collect.enums.CollectTaskTypeEnum.GLOBAL_DISCOVER
import com.whsanha55.cineseek.collect.enums.CollectTaskTypeEnum.KR_DISCOVER
import com.whsanha55.cineseek.collect.enums.CollectTaskTypeEnum.MOVIE_DETAIL
import com.whsanha55.cineseek.collect.enums.CollectTaskTypeEnum.PERSON_FILMO
import com.whsanha55.cineseek.collect.repository.CollectStateRepository
import com.whsanha55.cineseek.external.tmdb.client.TmdbClient
import com.whsanha55.cineseek.external.tmdb.config.TmdbProperties
import com.whsanha55.cineseek.global.exception.ExternalApiException
import com.whsanha55.cineseek.movie.entity.MovieCastEntity
import com.whsanha55.cineseek.movie.entity.MovieCastId
import com.whsanha55.cineseek.movie.entity.PersonEntity
import com.whsanha55.cineseek.movie.enums.PersonFilmoStateEnum
import com.whsanha55.cineseek.movie.repository.MovieCastRepository
import com.whsanha55.cineseek.movie.repository.MovieRepository
import com.whsanha55.cineseek.movie.repository.PersonRepository
import com.whsanha55.cineseek.movie.service.MovieMoodTagger
import com.whsanha55.cineseek.movie.service.MovieUpsertService
import com.whsanha55.cineseek.movie.vo.TmdbPersonInfo
import com.whsanha55.cineseek.search.service.IndexedMovieAssembler
import com.whsanha55.cineseek.search.service.MovieIndexer
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.readValue
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore

private val log = KotlinLogging.logger {}

/** 재시도해도 소용없는 작업 실패 — 404, payloadKey 파싱 실패 등. 핸들러가 던지면 fail이 영구 확정한다 */
private class PermanentTaskFailure(message: String) : RuntimeException(message)

/**
 * 수집 파이프라인 실행기 — 큐에서 클레임한 작업을 타입별로 실행한다.
 * 외부 API 호출은 이 레벨(트랜잭션 밖)에서 하고, 저장은 각 Service의 트랜잭션에 맡긴다.
 * MOVIE_DETAIL은 가상 스레드 + Semaphore(concurrency)로 병렬 처리하고,
 * 묶음이 끝나면 이번 틱에서 저장·갱신된 영화만 분위기 태깅 + 증분 색인한다(입력 해시로 재임베딩 스킵)
 */
@Service
class CollectPipeline(
    private val tmdbClient: TmdbClient,
    private val tmdbProperties: TmdbProperties,
    private val collectTaskService: CollectTaskService,
    private val collectStateRepository: CollectStateRepository,
    private val upsertService: MovieUpsertService,
    private val movieRepository: MovieRepository,
    private val movieCastRepository: MovieCastRepository,
    private val personRepository: PersonRepository,
    private val moodTagger: MovieMoodTagger,
    private val indexer: MovieIndexer,
    private val assembler: IndexedMovieAssembler,
    private val properties: CollectProperties,
    private val objectMapper: ObjectMapper,
    private val clock: Clock,
) {

    /** 작업 하나의 실행 결과 — 묶음 집계용 */
    private enum class Outcome {
        SUCCEEDED,
        RETRYABLE,
        PERMANENT,
        RELEASED,
    }

    /** 이번 틱에서 저장·갱신돼 색인 대상이 된 영화 — MOVIE_DETAIL(병렬)과 PERSON_FILMO(관계 보강)이 채운다 */
    private class TouchedMovies {
        val ids = ConcurrentLinkedQueue<Long>()

        fun add(movieId: Long) {
            ids += movieId
        }

        fun distinct(): List<Long> = ids.distinct()
    }

    /** 만료 lease 회수 후 batchSize만큼 클레임해 실행한다. maxRuntime에 도달하면 남은 작업은 체크포인트와 함께 반납한다 */
    fun runBatch(): BatchResult {
        collectTaskService.recoverExpiredLeases()
        val deadline = Instant.now(clock).plus(properties.maxRuntime)
        val tasks = collectTaskService.claim(properties.batchSize)
        val touched = TouchedMovies()
        val outcomes = mutableListOf<Outcome>()
        var storedMovies = 0

        tasks.filter { it.taskType != MOVIE_DETAIL }.forEach { task ->
            if (Instant.now(clock) >= deadline) {
                collectTaskService.release(task) // 체크포인트는 그대로 두고 반납
                outcomes += Outcome.RELEASED
                return@forEach
            }
            outcomes += when (task.taskType) {
                GLOBAL_DISCOVER -> handleGlobalDiscover(task)
                KR_DISCOVER -> handleKrDiscover(task)
                PERSON_FILMO -> handlePersonFilmo(task, touched)
                MOVIE_DETAIL -> throw IllegalStateException("여기 오면 안 온다") // 위에서 병렬 대상으로 걸러냈다
            }
        }

        // MOVIE_DETAIL — 가상 스레드 + Semaphore(concurrency). invokeAll은 제출 순서대로 결과를 돌려준다
        val details = tasks.filter { it.taskType == MOVIE_DETAIL }
        if (details.isNotEmpty()) {
            val detailOutcomes = runDetailsParallel(details, deadline, touched)
            outcomes += detailOutcomes
            storedMovies = detailOutcomes.count { it == Outcome.SUCCEEDED }
        }

        val counts = outcomes.groupingBy { it }.eachCount()
        val succeeded = counts.getOrDefault(Outcome.SUCCEEDED, 0)
        val failedRetryable = counts.getOrDefault(Outcome.RETRYABLE, 0)
        val failedPermanent = counts.getOrDefault(Outcome.PERMANENT, 0)
        val released = counts.getOrDefault(Outcome.RELEASED, 0)
        val touchedMovieIds = touched.distinct()
        if (touchedMovieIds.isNotEmpty()) {
            finishTick(touchedMovieIds)
        }
        log.info {
            "수집 틱 완료. 클레임=${tasks.size}, 성공=$succeeded, 재시도=$failedRetryable, " +
                "영구실패=$failedPermanent, 반납=$released, 색인대상=${touchedMovieIds.size}"
        }
        return BatchResult(
            processed = outcomes.size - released,
            succeeded = succeeded,
            failedRetryable = failedRetryable,
            failedPermanent = failedPermanent,
            storedMovies = storedMovies,
            touchedMovieIds = touchedMovieIds,
        )
    }

    /** bootstrap 초기 enqueue — collect_state.bootstrapped_at이 NULL일 때만 1회 실행한다 */
    fun enqueueBootstrapIfNotStarted(): Boolean {
        val state = collectStateRepository.findById(COLLECT_STATE_ID).orElseGet {
            collectStateRepository.save(CollectStateEntity())
        }
        if (state.bootstrappedAt != null) {
            return false
        }
        val today = LocalDate.now(clock.withZone(ZoneId.of(properties.zone)))

        // ① 전 세계 3기준
        TmdbClient.GLOBAL_DISCOVER_SOURCES.forEach { params ->
            collectTaskService.enqueue(GLOBAL_DISCOVER, "$GLOBAL_PREFIX${params[SORT_BY]}")
        }

        // ② 한국 — 연도 구간 전체(제작국가·원어 두 기준)
        KR_CRITERIA.forEach { criterion ->
            KrWindow.yearly(today, properties.kr.yearsBack, criterion).forEach { window ->
                collectTaskService.enqueue(KR_DISCOVER, window.payloadKey())
            }
        }

        // ③ 기존 영화 백필 — 전체 cast 저장 전환으로 누락된 출연진을 복구한다
        val existingTmdbIds = movieRepository.findAllTmdbIds()
        existingTmdbIds.forEach { enqueueMovieDetail(it, PRIORITY_NORMAL) }
        log.info { "bootstrap 기존 영화 백필 enqueue. count=${existingTmdbIds.size}" }

        // ④ 시드 인물 필모그래피
        properties.seedPersonIds.forEach { personId ->
            collectTaskService.enqueue(PERSON_FILMO, "$PERSON_PREFIX$personId")
        }

        state.markBootstrapped(Instant.now(clock))
        collectStateRepository.save(state)
        log.info { "bootstrap 초기 enqueue 완료" }
        return true
    }

    /**
     * MOVIE_DETAIL 병렬 실행 — 가상 스레드 + Semaphore(concurrency). permit을 잡은 뒤 deadline을
     * 검사해 대기 중이던 작업도 시간 초과면 반납한다. 결과는 제출 순서와 같다
     */
    private fun runDetailsParallel(
        details: List<CollectTaskEntity>,
        deadline: Instant,
        touched: TouchedMovies,
    ): List<Outcome> {
        val permits = Semaphore(properties.concurrency)
        val futures = Executors.newVirtualThreadPerTaskExecutor().use { executor ->
            executor.invokeAll(
                details.map { task ->
                    Callable {
                        permits.acquire()
                        try {
                            if (Instant.now(clock) >= deadline) {
                                collectTaskService.release(task)
                                Outcome.RELEASED
                            } else {
                                handleMovieDetail(task, touched)
                            }
                        } finally {
                            permits.release()
                        }
                    }
                },
            )
        }
        return futures.map { it.get() }
    }

    /** MOVIE_DETAIL — 상세 + credits 수집 후 upsert. 404(null)은 영구 실패 */
    private fun handleMovieDetail(task: CollectTaskEntity, touched: TouchedMovies): Outcome = runCatching {
        val tmdbId = task.payloadKey.removePrefix(MOVIE_PREFIX).toLongOrNull()
            ?: throw PermanentTaskFailure("MOVIE_DETAIL payloadKey 파싱 실패. payloadKey=${task.payloadKey}")
        val movie = tmdbClient.fetchDetail(tmdbId)
            ?: throw PermanentTaskFailure("TMDB 404 — 영화 없음. tmdbId=$tmdbId")
        touched.add(upsertService.upsert(movie))
        collectTaskService.complete(task)
        Outcome.SUCCEEDED
    }.getOrElse { fail(task, it) }

    /** KR_DISCOVER — 구간별 discover 순회. 페이지 상한 도달 시 구간을 반분할해 재수집 작업을 만든다 */
    private fun handleKrDiscover(task: CollectTaskEntity): Outcome = runCatching {
        val window = KrWindow.fromPayloadKey(task.payloadKey)
            ?: throw PermanentTaskFailure("KR_DISCOVER payloadKey 파싱 실패. payloadKey=${task.payloadKey}")
        val base = when (window.criterion) {
            CRITERION_ORIGIN -> TmdbClient.KR_DISCOVER_PARAMS
            CRITERION_LANG -> TmdbClient.KO_LANGUAGE_DISCOVER_PARAMS
            else -> throw PermanentTaskFailure("알 수 없는 한국 수집 기준. criterion=${window.criterion}")
        }
        val params = base + mapOf(
            "primary_release_date.gte" to window.from.toString(),
            "primary_release_date.lte" to window.to.toString(),
            SORT_BY to "primary_release_date.desc",
        )
        val startPage = Checkpoint.decode(objectMapper, task.checkpoint).page + 1
        val result = tmdbClient.fetchDiscoverIds(params, startPage, properties.kr.pageCap)
        result.tmdbIds.forEach { enqueueMovieDetail(it, PRIORITY_NORMAL) }
        result.failure?.let { return@runCatching fail(task, it, Checkpoint(result.lastPage).encode(objectMapper)) }

        val split = window.split(properties.kr.minWindowMonths)
        val capped = result.lastPage >= properties.kr.pageCap && result.totalPages > properties.kr.pageCap
        if (capped && split != null) {
            // 상한 안에 구간을 다 못 보았다 — 반으로 쪼개 각각 재수집하게 하고 이 작업은 끝낸다
            collectTaskService.enqueue(KR_DISCOVER, split.first.payloadKey())
            collectTaskService.enqueue(KR_DISCOVER, split.second.payloadKey())
            log.info { "KR 구간 분할 재수집. 원=${window.payloadKey()}, 상한=${properties.kr.pageCap}" }
        }
        collectTaskService.complete(task, Checkpoint(result.lastPage).encode(objectMapper))
        Outcome.SUCCEEDED
    }.getOrElse { fail(task, it) }

    /** GLOBAL_DISCOVER — 기존 3기준을 checkpoint부터 tmdb pages까지 순회한다 */
    private fun handleGlobalDiscover(task: CollectTaskEntity): Outcome = runCatching {
        val sortBy = task.payloadKey.removePrefix(GLOBAL_PREFIX)
        val params = TmdbClient.GLOBAL_DISCOVER_SOURCES.firstOrNull { it[SORT_BY] == sortBy }
            ?: throw PermanentTaskFailure("알 수 없는 전 세계 수집 기준. payloadKey=${task.payloadKey}")
        val startPage = Checkpoint.decode(objectMapper, task.checkpoint).page + 1
        val result = tmdbClient.fetchDiscoverIds(params, startPage, tmdbProperties.pages)
        result.tmdbIds.forEach { enqueueMovieDetail(it, PRIORITY_NORMAL) }
        result.failure?.let { return@runCatching fail(task, it, Checkpoint(result.lastPage).encode(objectMapper)) }
        collectTaskService.complete(task, Checkpoint(result.lastPage).encode(objectMapper))
        Outcome.SUCCEEDED
    }.getOrElse { fail(task, it) }

    /**
     * PERSON_FILMO — 인물 upsert 후 필모그래피로 movie_cast를 보강한다. DB에 없는 영화는
     * MOVIE_DETAIL(필모 확장 우선순위)로 등록한다. 배우 재귀 확장은 하지 않는다 —
     * 시드/우선 요청만 PERSON_FILMO를 만든다
     */
    private fun handlePersonFilmo(task: CollectTaskEntity, touched: TouchedMovies): Outcome = runCatching {
        val personId = task.payloadKey.removePrefix(PERSON_PREFIX).toLongOrNull()
            ?: throw PermanentTaskFailure("PERSON_FILMO payloadKey 파싱 실패. payloadKey=${task.payloadKey}")
        val info = tmdbClient.fetchPerson(personId)
            ?: throw PermanentTaskFailure("TMDB 404 — 인물 없음. personId=$personId")
        upsertPerson(info)

        val credits = tmdbClient.fetchPersonMovieCredits(personId).distinctBy { it.tmdbId }
        val existing = movieRepository.findAllByTmdbIdIn(credits.map { it.tmdbId }).associateBy { it.tmdbId }
        var stored = 0
        credits.forEach { credit ->
            val movie = existing[credit.tmdbId]
            if (movie == null) {
                enqueueMovieDetail(credit.tmdbId, PRIORITY_FILMO)
            } else {
                val movieId = requireNotNull(movie.movieId)
                if (!movieCastRepository.existsById(MovieCastId(movieId, personId))) {
                    movieCastRepository.save(
                        MovieCastEntity(
                            movieId = movieId,
                            personId = personId,
                            name = info.name,
                            character = credit.character,
                            castOrder = credit.order,
                        ),
                    )
                    touched.add(movieId) // 출연 관계가 늘었으니 payload(cast_ids)를 다시 내보낸다
                }
                stored++
            }
        }

        val person = personRepository.findById(personId).orElseThrow()
        val state = if (stored == credits.size) PersonFilmoStateEnum.COMPLETE else PersonFilmoStateEnum.COLLECTING
        person.applyFilmography(
            checkedAt = Instant.now(clock),
            externalCount = credits.size,
            storedCount = stored,
            state = state,
        )
        personRepository.save(person)
        collectTaskService.complete(task)
        Outcome.SUCCEEDED
    }.getOrElse { fail(task, it) }

    /** 틱 마무리 — 이번에 저장·갱신한 영화의 분위기 태깅(상한)과 증분 색인. PG는 이미 저장됐으므로 실패는 로그만 남긴다 */
    private fun finishTick(movieIds: List<Long>) {
        runCatching { moodTagger.tagAll(MOOD_TAG_LIMIT_PER_TICK) }
            .onFailure { log.error(it) { "분위기 태깅 실패 — 다음 틱/재색인에서 회복된다" } }
        runCatching { indexer.indexTouched(assembler.assemble(movieIds)) }
            .onFailure { log.error(it) { "증분 색인 실패 — PG는 저장됐고 재색인에서 회복된다" } }
    }

    private fun enqueueMovieDetail(tmdbId: Long, priority: Int) {
        collectTaskService.enqueue(MOVIE_DETAIL, "$MOVIE_PREFIX$tmdbId", priority)
    }

    private fun upsertPerson(info: TmdbPersonInfo) {
        personRepository.findById(info.personId).orElseGet { PersonEntity(info.personId, info.name) }
            .apply { updateProfile(info.name, info.profilePath) }
            .let(personRepository::save)
    }

    /**
     * 실패 처리 — 영구(404·파싱 오류)인지 분류해 기록한다. 429/5xx/타임아웃과 나머지는 재시도 예약.
     * [checkpointJson]은 discover 중간 실패의 진행분 — 재시도가 그다음 페이지부터 이어서 한다
     */
    private fun fail(task: CollectTaskEntity, e: Throwable, checkpointJson: String? = null): Outcome {
        val retryable = e !is PermanentTaskFailure && (e !is ExternalApiException || e.status != HTTP_NOT_FOUND)
        collectTaskService.fail(task, e.message ?: e.javaClass.simpleName, retryable, checkpointJson)
        return if (retryable) Outcome.RETRYABLE else Outcome.PERMANENT
    }

    /** discover 진행 체크포인트 — 마지막으로 소비한 페이지를 Map JSON({"page":N})으로 저장한다 */
    private data class Checkpoint(val page: Int = 0) {
        fun encode(mapper: ObjectMapper): String = mapper.writeValueAsString(mapOf(PAGE to page))

        companion object {
            private const val PAGE = "page"

            fun decode(mapper: ObjectMapper, json: String?): Checkpoint = Checkpoint(
                json?.let { mapper.readValue<Map<String, Int>>(it)[PAGE] } ?: 0,
            )
        }
    }

    companion object {
        const val PRIORITY_NORMAL = 0 // 일반(discover가 만든 상세 수집)
        const val PRIORITY_FILMO = 10 // 필모 확장 — PERSON_FILMO가 만든 상세 수집
        const val PRIORITY_USER_CLICK = 100 // 사용자 클릭 즉시 수집(T5 API가 사용)

        private const val COLLECT_STATE_ID = 1

        /** MOVIE_DETAIL payloadKey 접두 — 스케줄러·필모 갱신 포트 구현이 같은 형식을 쓴다 */
        const val MOVIE_PREFIX = "movie:tmdb:"

        /** PERSON_FILMO payloadKey 접두 — 스케줄러·필모 갱신 포트 구현이 같은 형식을 쓴다 */
        const val PERSON_PREFIX = "person:"
        private const val GLOBAL_PREFIX = "global:"
        private const val SORT_BY = "sort_by"
        private const val CRITERION_ORIGIN = "origin"
        private const val CRITERION_LANG = "lang"

        /** 한국 수집 기준 목록 — 일일 증분 재등록에서도 쓴다 */
        val KR_CRITERIA = listOf(CRITERION_ORIGIN, CRITERION_LANG)
        private const val HTTP_NOT_FOUND = 404
        private const val MOOD_TAG_LIMIT_PER_TICK = 50 // 틱당 분위기 태깅 상한 — LLM 호출 폭주 방지
    }
}

/** 한 틱의 수집 결과 요약 — collect_state.last_summary 기록용(T5) */
data class BatchResult(
    val processed: Int,
    val succeeded: Int,
    val failedRetryable: Int,
    val failedPermanent: Int,
    val storedMovies: Int,
    val touchedMovieIds: List<Long>,
) {
    /** 로그·last_summary용 요약 문자열 (키=값) */
    fun summary(): String =
        "처리=$processed, 성공=$succeeded, 재시도실패=$failedRetryable, 영구실패=$failedPermanent, 저장영화=$storedMovies"
}
