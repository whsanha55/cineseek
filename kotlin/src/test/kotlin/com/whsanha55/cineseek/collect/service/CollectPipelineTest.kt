package com.whsanha55.cineseek.collect.service

import com.ninjasquad.springmockk.MockkBean
import com.whsanha55.cineseek.collect.config.CollectProperties
import com.whsanha55.cineseek.collect.enums.CollectTaskStatusEnum.DONE
import com.whsanha55.cineseek.collect.enums.CollectTaskStatusEnum.FAILED
import com.whsanha55.cineseek.collect.enums.CollectTaskStatusEnum.PENDING
import com.whsanha55.cineseek.collect.enums.CollectTaskTypeEnum
import com.whsanha55.cineseek.collect.enums.CollectTaskTypeEnum.GLOBAL_DISCOVER
import com.whsanha55.cineseek.collect.enums.CollectTaskTypeEnum.KR_DISCOVER
import com.whsanha55.cineseek.collect.enums.CollectTaskTypeEnum.MOVIE_DETAIL
import com.whsanha55.cineseek.collect.enums.CollectTaskTypeEnum.PERSON_FILMO
import com.whsanha55.cineseek.collect.repository.CollectTaskRepository
import com.whsanha55.cineseek.external.tmdb.client.TmdbClient
import com.whsanha55.cineseek.external.tmdb.config.TmdbProperties
import com.whsanha55.cineseek.global.config.JpaConfig
import com.whsanha55.cineseek.global.exception.ExternalApiException
import com.whsanha55.cineseek.movie.entity.MovieCastEntity
import com.whsanha55.cineseek.movie.enums.PersonFilmoStateEnum
import com.whsanha55.cineseek.movie.repository.MovieCastRepository
import com.whsanha55.cineseek.movie.repository.MovieRepository
import com.whsanha55.cineseek.movie.repository.PersonRepository
import com.whsanha55.cineseek.movie.service.MovieMoodTagger
import com.whsanha55.cineseek.movie.service.MovieUpsertService
import com.whsanha55.cineseek.movie.vo.TmdbCastMember
import com.whsanha55.cineseek.movie.vo.TmdbDiscoverResult
import com.whsanha55.cineseek.movie.vo.TmdbGenre
import com.whsanha55.cineseek.movie.vo.TmdbMovie
import com.whsanha55.cineseek.movie.vo.TmdbPerson
import com.whsanha55.cineseek.movie.vo.TmdbPersonCredit
import com.whsanha55.cineseek.movie.vo.TmdbPersonInfo
import com.whsanha55.cineseek.search.service.IndexedMovieAssembler
import com.whsanha55.cineseek.search.service.MovieIndexer
import com.whsanha55.cineseek.search.vo.IndexedMovie
import io.mockk.Called
import io.mockk.every
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.math.BigDecimal
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * CollectPipeline — 태스크 유형별 실행·증분 색인 검증. PG는 Testcontainers,
 * TMDB·LLM·Qdrant 경계는 MockK으로 대체한다(외부 계약 자체는 각 클라이언트 테스트가 담당).
 * MOVIE_DETAIL 핸들러는 가상 스레드에서 자기 트랜잭션으로 커밋하므로 테스트 트랜잭션 롤백을
 * 끄고(@Transactional NOT_SUPPORTED) 매 테스트前に 데이터를 지운다
 */
@DataJpaTest(
    properties = [
        "cineseek.collect.kr.page-cap=3",
        "cineseek.collect.kr.min-window-months=1",
        "cineseek.collect.kr.years-back=1",
        "cineseek.collect.seed-person-ids=100",
        "cineseek.tmdb.pages=5",
    ],
)
@Import(
    CollectPipeline::class,
    CollectTaskService::class,
    MovieUpsertService::class,
    IndexedMovieAssembler::class,
    JpaConfig::class,
    CollectPipelineTest.Config::class,
)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers
class CollectPipelineTest {

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:17")

        val START: Instant = Instant.parse("2026-01-01T00:00:00Z")
    }

    @TestConfiguration
    @EnableConfigurationProperties(CollectProperties::class, TmdbProperties::class)
    class Config {
        @Bean fun clock(): MutableClock = MutableClock(START)

        @Bean fun objectMapper(): ObjectMapper = jacksonObjectMapper()
    }

    /** 시각을 임의로 옮길 수 있는 Clock — maxRuntime 경계를 밟아본다 */
    class MutableClock(private val start: Instant) : Clock() {
        private var current = start

        fun reset() {
            current = start
        }

        fun advance(duration: Duration) {
            current = current.plus(duration)
        }

        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock = this

        override fun instant(): Instant = current
    }

    @MockkBean(relaxed = true)
    lateinit var tmdbClient: TmdbClient

    @MockkBean(relaxed = true)
    lateinit var moodTagger: MovieMoodTagger

    @MockkBean(relaxed = true)
    lateinit var indexer: MovieIndexer

    @Autowired lateinit var pipeline: CollectPipeline

    @Autowired lateinit var collectTaskService: CollectTaskService

    @Autowired lateinit var collectTaskRepository: CollectTaskRepository

    @Autowired lateinit var upsertService: MovieUpsertService

    @Autowired lateinit var movieRepository: MovieRepository

    @Autowired lateinit var movieCastRepository: MovieCastRepository

    @Autowired lateinit var personRepository: PersonRepository

    @Autowired lateinit var clock: MutableClock

    @BeforeEach
    fun cleanUp() {
        clock.reset() // 컨텍스트가 캐시되므로 모든 테스트가 같은 기준 시각에서 시작하게 한다
        collectTaskRepository.deleteAll()
        movieRepository.deleteAll() // 자식(movie_cast 등)은 DB의 ON DELETE CASCADE가 지운다
        personRepository.deleteAll()
    }

    private fun tmdbMovie(tmdbId: Long) = TmdbMovie(
        tmdbId = tmdbId,
        title = "영화$tmdbId",
        originalTitle = null,
        overview = "줄거리$tmdbId",
        releaseDate = "2008-07-16",
        runtime = 152,
        voteAverage = BigDecimal("8.5"),
        voteCount = 30000,
        posterPath = "/p.jpg",
        backdropPath = "/b.jpg",
        originalLanguage = "ko",
        originCountry = "KR",
        genres = listOf(TmdbGenre(80, "범죄")),
        directors = listOf(TmdbPerson(1L, "감독")),
        cast = listOf(TmdbCastMember(2L, "배우", "역할", 0)),
    )

    private fun task(type: CollectTaskTypeEnum, payloadKey: String) =
        collectTaskRepository.findByTaskTypeAndPayloadKey(type, payloadKey)

    @Test
    fun `KR_DISCOVER — 체크포인트 다음 페이지부터 재개하고 상한 미도달 시 분할 없이 DONE`() {
        // given — 2023년 구간, 이전 틱에서 1페이지까지 소비
        collectTaskService.enqueue(KR_DISCOVER, "kr:origin:2023-01-01..2023-12-31")
        val claimed = collectTaskService.claim(1).single()
        claimed.saveCheckpoint("""{"page":1}""")
        collectTaskRepository.save(claimed)
        collectTaskService.release(claimed)

        val params = slot<Map<String, String>>()
        val startPage = slot<Int>()
        val endPage = slot<Int>()
        every {
            tmdbClient.fetchDiscoverIds(capture(params), capture(startPage), capture(endPage))
        } returns TmdbDiscoverResult(tmdbIds = listOf(10L, 11L), lastPage = 2, totalPages = 2)

        // when
        val result = pipeline.runBatch()

        // then — 이전 페이지는 다시 호출하지 않는다(호출 1건, startPage=2)
        assertThat(startPage.captured).isEqualTo(2)
        assertThat(endPage.captured).isEqualTo(3) // kr.page-cap
        assertThat(params.captured).containsAllEntriesOf(
            mapOf(
                "with_origin_country" to "KR",
                "primary_release_date.gte" to "2023-01-01",
                "primary_release_date.lte" to "2023-12-31",
                "sort_by" to "primary_release_date.desc",
            ),
        )
        assertThat(result.succeeded).isEqualTo(1)
        assertThat(task(KR_DISCOVER, "kr:origin:2023-01-01..2023-12-31")!!.status).isEqualTo(DONE)
        // 발견한 id는 상세 수집으로 — 분할 작업은 생기지 않는다
        assertThat(task(MOVIE_DETAIL, "movie:tmdb:10")!!.status).isEqualTo(PENDING)
        assertThat(task(MOVIE_DETAIL, "movie:tmdb:11")!!.status).isEqualTo(PENDING)
        assertThat(collectTaskRepository.count()).isEqualTo(3L) // KR 1 + MOVIE_DETAIL 2
    }

    @Test
    fun `KR_DISCOVER — 중간 페이지 실패는 진행분을 enqueue하고 체크포인트를 남겨 재시도를 예약한다`() {
        // given — 2페이지까지 읽고 3페이지에서 503
        collectTaskService.enqueue(KR_DISCOVER, "kr:origin:2022-01-01..2022-12-31")
        every { tmdbClient.fetchDiscoverIds(any(), any(), any()) } returns TmdbDiscoverResult(
            tmdbIds = listOf(20L),
            lastPage = 2,
            totalPages = 10,
            failure = ExternalApiException("tmdb", RuntimeException("503"), 503),
        )

        // when
        val result = pipeline.runBatch()

        // then
        assertThat(result.failedRetryable).isEqualTo(1)
        val krTask = task(KR_DISCOVER, "kr:origin:2022-01-01..2022-12-31")!!
        assertThat(krTask.status).isEqualTo(PENDING)
        assertThat(krTask.checkpoint).isEqualTo("""{"page":2}""")
        assertThat(task(MOVIE_DETAIL, "movie:tmdb:20")!!.status).isEqualTo(PENDING)
    }

    @Test
    fun `KR_DISCOVER — pageCap 도달 시 구간을 반분할해 두 작업을 enqueue한다`() {
        // given
        collectTaskService.enqueue(KR_DISCOVER, "kr:lang:2020-01-01..2020-12-31")
        every {
            tmdbClient.fetchDiscoverIds(any(), any(), any())
        } returns TmdbDiscoverResult(tmdbIds = listOf(1L), lastPage = 3, totalPages = 100) // 상한(3) 도달, 더 있음

        // when
        pipeline.runBatch()

        // then — 원 구간은 DONE, 반분할된 두 구간이 PENDING
        assertThat(task(KR_DISCOVER, "kr:lang:2020-01-01..2020-12-31")!!.status).isEqualTo(DONE)
        assertThat(task(KR_DISCOVER, "kr:lang:2020-01-01..2020-06-30")!!.status).isEqualTo(PENDING)
        assertThat(task(KR_DISCOVER, "kr:lang:2020-07-01..2020-12-31")!!.status).isEqualTo(PENDING)
    }

    @Test
    fun `GLOBAL_DISCOVER — 체크포인트부터 tmdb pages까지 조회해 상세 수집을 만든다`() {
        // given
        collectTaskService.enqueue(GLOBAL_DISCOVER, "global:vote_count.desc")
        val params = slot<Map<String, String>>()
        val startPage = slot<Int>()
        val endPage = slot<Int>()
        every {
            tmdbClient.fetchDiscoverIds(capture(params), capture(startPage), capture(endPage))
        } returns TmdbDiscoverResult(tmdbIds = listOf(7L), lastPage = 5, totalPages = 5)

        // when
        pipeline.runBatch()

        // then
        assertThat(params.captured).containsAllEntriesOf(mapOf("sort_by" to "vote_count.desc"))
        assertThat(startPage.captured).isEqualTo(1)
        assertThat(endPage.captured).isEqualTo(5) // tmdb.pages
        assertThat(task(GLOBAL_DISCOVER, "global:vote_count.desc")!!.status).isEqualTo(DONE)
        assertThat(task(MOVIE_DETAIL, "movie:tmdb:7")).isNotNull
    }

    @Test
    fun `MOVIE_DETAIL — 404(null)는 영구 실패하고 색인을 돌리지 않는다`() {
        // given
        collectTaskService.enqueue(MOVIE_DETAIL, "movie:tmdb:999")
        every { tmdbClient.fetchDetail(999L) } returns null

        // when
        val result = pipeline.runBatch()

        // then
        assertThat(result.failedPermanent).isEqualTo(1)
        assertThat(result.storedMovies).isZero()
        val failed = task(MOVIE_DETAIL, "movie:tmdb:999")!!
        assertThat(failed.status).isEqualTo(FAILED)
        assertThat(failed.attempts).isZero() // 재시도 예약 없음
        verify { indexer wasNot Called }
    }

    @Test
    fun `처리할 작업이 없는 틱에도 분위기 태깅(상한)을 돌리고 색인은 돌리지 않는다`() {
        // given — 큐가 비어 있다

        // when
        val result = pipeline.runBatch()

        // then
        assertThat(result.processed).isZero()
        verify { moodTagger.tagAll(50) }
        verify { indexer wasNot Called }
    }

    @Test
    fun `MOVIE_DETAIL — 429는 재시도를 예약한다`() {
        // given
        collectTaskService.enqueue(MOVIE_DETAIL, "movie:tmdb:998")
        every { tmdbClient.fetchDetail(998L) } throws ExternalApiException("tmdb", status = 429)

        // when
        val result = pipeline.runBatch()

        // then
        assertThat(result.failedRetryable).isEqualTo(1)
        val scheduled = task(MOVIE_DETAIL, "movie:tmdb:998")!!
        assertThat(scheduled.status).isEqualTo(PENDING)
        assertThat(scheduled.attempts).isEqualTo(1)
        assertThat(scheduled.nextRetryAt).isNotNull()
    }

    @Test
    fun `MOVIE_DETAIL — 성공 시 upsert하고 묶음 색인·분위기 태깅(상한)을 돌린다`() {
        // given
        collectTaskService.enqueue(MOVIE_DETAIL, "movie:tmdb:155")
        every { tmdbClient.fetchDetail(155L) } returns tmdbMovie(155L)

        // when
        val result = pipeline.runBatch()

        // then
        assertThat(result.succeeded).isEqualTo(1)
        assertThat(result.storedMovies).isEqualTo(1)
        val movieId = requireNotNull(movieRepository.findByTmdbId(155L)?.movieId)
        assertThat(result.touchedMovieIds).containsExactly(movieId)
        verify { moodTagger.tagAll(50) } // 틱당 상한
        val indexed = slot<List<IndexedMovie>>()
        verify { indexer.indexTouched(capture(indexed)) }
        assertThat(indexed.captured.map { it.movieId }).containsExactly(movieId)
        assertThat(indexed.captured.single().payload.castIds).containsExactly(2L) // 전체 cast가 payload에
    }

    @Test
    fun `PERSON_FILMO — 없는 영화는 필모 확장 우선순위 상세 수집을 만들고 관계를 보강한다`() {
        // given — 155는 저장돼 있고 5555는 없다
        val movieId = upsertService.upsert(tmdbMovie(155L))
        collectTaskService.enqueue(PERSON_FILMO, "person:777")
        every { tmdbClient.fetchPerson(777L) } returns TmdbPersonInfo(777L, "배우777", "/p.jpg")
        every { tmdbClient.fetchPersonMovieCredits(777L) } returns listOf(
            TmdbPersonCredit(tmdbId = 155L, title = "영화155", character = "조연", order = 3, releaseDate = null),
            TmdbPersonCredit(tmdbId = 5555L, title = "미수집 영화", character = "주연", order = 0, releaseDate = null),
        )

        // when
        val result = pipeline.runBatch()

        // then — movie_cast 보강(캐릭터·castOrder) + 없는 영화는 우선순위 10으로 enqueue
        assertThat(result.succeeded).isEqualTo(1)
        val cast = movieCastRepository.findAllByMovieId(movieId).single { it.personId == 777L }
        assertThat(cast.character).isEqualTo("조연")
        assertThat(cast.castOrder).isEqualTo(3)
        val detail = task(MOVIE_DETAIL, "movie:tmdb:5555")!!
        assertThat(detail.status).isEqualTo(PENDING)
        assertThat(detail.priority).isEqualTo(10)

        // 필모 상태 — 외부 2건 중 저장 1건 → COLLECTING
        val person = personRepository.findById(777L).orElseThrow()
        assertThat(person.filmoState).isEqualTo(PersonFilmoStateEnum.COLLECTING)
        assertThat(person.filmoExternalCount).isEqualTo(2)
        assertThat(person.filmoStoredCount).isEqualTo(1)
        assertThat(person.filmoCheckedAt).isEqualTo(START)
        assertThat(person.name).isEqualTo("배우777")
    }

    @Test
    fun `PERSON_FILMO — 이미 있는 출연 관계는 유지하고 전부 저장돼 있으면 COMPLETE`() {
        // given — 155 저장 + 이미 (155, 777) 관계가 있다
        val movieId = upsertService.upsert(tmdbMovie(155L))
        movieCastRepository.save(
            MovieCastEntity(
                movieId = movieId,
                personId = 777L,
                name = "배우777",
                character = "원래 역할",
                castOrder = 0,
            ),
        )
        collectTaskService.enqueue(PERSON_FILMO, "person:777")
        every { tmdbClient.fetchPerson(777L) } returns TmdbPersonInfo(777L, "배우777", null)
        every { tmdbClient.fetchPersonMovieCredits(777L) } returns listOf(
            TmdbPersonCredit(tmdbId = 155L, title = "영화155", character = "다른 배역", order = 9, releaseDate = null),
        )

        // when
        pipeline.runBatch()

        // then — 관계는 그대로, 인물은 COMPLETE
        val cast = movieCastRepository.findAllByMovieId(movieId).single { it.personId == 777L }
        assertThat(cast.character).isEqualTo("원래 역할")
        assertThat(cast.castOrder).isEqualTo(0)
        val person = personRepository.findById(777L).orElseThrow()
        assertThat(person.filmoState).isEqualTo(PersonFilmoStateEnum.COMPLETE)
        assertThat(person.filmoExternalCount).isEqualTo(1)
        assertThat(person.filmoStoredCount).isEqualTo(1)
    }

    @Test
    fun `PERSON_FILMO — person 404는 영구 실패`() {
        // given
        collectTaskService.enqueue(PERSON_FILMO, "person:888")
        every { tmdbClient.fetchPerson(888L) } returns null

        // when
        val result = pipeline.runBatch()

        // then
        assertThat(result.failedPermanent).isEqualTo(1)
        assertThat(task(PERSON_FILMO, "person:888")!!.status).isEqualTo(FAILED)
    }

    @Test
    fun `PERSON_FILMO — 배우를 재귀 확장하지 않는다`() {
        // given — 필모가 미수집 영화(5555)를 만든다
        upsertService.upsert(tmdbMovie(155L))
        collectTaskService.enqueue(PERSON_FILMO, "person:777")
        every { tmdbClient.fetchPerson(777L) } returns TmdbPersonInfo(777L, "배우777", null)
        every { tmdbClient.fetchPersonMovieCredits(777L) } returns listOf(
            TmdbPersonCredit(tmdbId = 5555L, title = "미수집 영화", character = "주연", order = 0, releaseDate = null),
        )
        pipeline.runBatch()

        // when — 다음 틱에서 5555 상세 수집(credits에 배우 personId=2가 들어 있다)
        every { tmdbClient.fetchDetail(5555L) } returns tmdbMovie(5555L)
        pipeline.runBatch()

        // then — PERSON_FILMO는 원본 1개(777)뿐. 5555의 cast로 새 인물 작업이 생기지 않는다
        assertThat(movieRepository.findByTmdbId(5555L)).isNotNull
        assertThat(task(PERSON_FILMO, "person:2")).isNull()
        assertThat(collectTaskRepository.count()).isEqualTo(2L) // PERSON_FILMO 777 + MOVIE_DETAIL 5555, 모두 DONE
    }

    @Test
    fun `maxRuntime 도달 — 남은 작업을 체크포인트와 함께 반납한다`() {
        // given — 첫 작업 처리 중 시간이 흘러 deadline(maxRuntime 25m)을 넘긴다
        collectTaskService.enqueue(KR_DISCOVER, "kr:origin:2023-01-01..2023-12-31")
        collectTaskService.enqueue(KR_DISCOVER, "kr:origin:2022-01-01..2022-12-31")
        val preClaimed = collectTaskService.claim(2)
        val second = preClaimed.single { it.payloadKey == "kr:origin:2022-01-01..2022-12-31" }
        second.saveCheckpoint("""{"page":2}""")
        collectTaskRepository.save(second)
        preClaimed.forEach { collectTaskService.release(it) }

        every { tmdbClient.fetchDiscoverIds(any(), any(), any()) } answers {
            clock.advance(Duration.ofMinutes(30))
            TmdbDiscoverResult(tmdbIds = emptyList(), lastPage = 1, totalPages = 1)
        }

        // when
        val result = pipeline.runBatch()

        // then — 첫 작업은 성공, 두 번째는 체크포인트를 유지한 채 PENDING으로 반납
        assertThat(result.succeeded).isEqualTo(1)
        val released = task(KR_DISCOVER, "kr:origin:2022-01-01..2022-12-31")!!
        assertThat(released.status).isEqualTo(PENDING)
        assertThat(released.checkpoint).isEqualTo("""{"page":2}""")
    }

    @Test
    fun `bootstrap — 초기 enqueue는 1회만 실행된다`() {
        // given — 기존 영화 1편
        upsertService.upsert(tmdbMovie(155L))

        // when
        val first = pipeline.enqueueBootstrapIfNotStarted()
        val second = pipeline.enqueueBootstrapIfNotStarted()

        // then
        assertThat(first).isTrue()
        assertThat(second).isFalse()
        // ① 전 세계 3기준
        listOf("global:vote_count.desc", "global:vote_average.desc", "global:popularity.desc").forEach { key ->
            assertThat(task(GLOBAL_DISCOVER, key)).isNotNull
        }
        // ② 한국 연도 구간 — yearsBack=1, 오늘(2026-01-01) 기준 2025·2026 × 두 기준 = 4
        assertThat(task(KR_DISCOVER, "kr:origin:2026-01-01..2026-12-31")).isNotNull
        assertThat(task(KR_DISCOVER, "kr:lang:2025-01-01..2025-12-31")).isNotNull
        assertThat(collectTaskRepository.countByTaskTypeAndStatus(KR_DISCOVER, PENDING)).isEqualTo(4L)
        // ③ 기존 영화 백필
        assertThat(task(MOVIE_DETAIL, "movie:tmdb:155")).isNotNull
        // ④ 시드 인물
        assertThat(task(PERSON_FILMO, "person:100")).isNotNull
    }
}
