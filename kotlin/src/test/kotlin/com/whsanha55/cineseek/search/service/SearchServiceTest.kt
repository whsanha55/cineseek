package com.whsanha55.cineseek.search.service

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.whsanha55.cineseek.external.embedding.client.EmbeddingClient
import com.whsanha55.cineseek.external.embedding.config.EmbeddingProperties
import com.whsanha55.cineseek.external.qdrant.config.QdrantProperties
import com.whsanha55.cineseek.global.exception.ExternalApiException
import com.whsanha55.cineseek.movie.repository.MovieRepository
import com.whsanha55.cineseek.movie.vo.MoodVocabulary
import com.whsanha55.cineseek.movie.vo.MovieTitle
import com.whsanha55.cineseek.search.vo.Embedding
import com.whsanha55.cineseek.search.vo.SearchFilter
import io.mockk.every
import io.mockk.mockk
import io.qdrant.client.PointIdFactory
import io.qdrant.client.QdrantClient
import io.qdrant.client.QdrantGrpcClient
import io.qdrant.client.ValueFactory
import io.qdrant.client.VectorFactory
import io.qdrant.client.VectorsFactory
import io.qdrant.client.grpc.Collections
import io.qdrant.client.grpc.Points
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * SearchService 통합 테스트 — 실제 Qdrant 컨테이너(dense 4차원 + sparse) + WireMock 임베딩 스텁.
 * prefetch(dense·sparse·분위기 태그) + RRF + 인기도 재정렬 + payload ID 필터가 실제로 맞물리는지 확인한다
 */
class SearchServiceTest {

    private lateinit var qdrant: GenericContainer<*>
    private lateinit var embedStub: WireMockServer
    private lateinit var qdrantClient: QdrantClient
    private lateinit var service: SearchService
    private val movieRepository = mockk<MovieRepository> { every { findAllTitles() } returns emptyList() }
    private val fixedClock = Clock.fixed(TODAY.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC)

    @BeforeEach
    fun setUp() {
        qdrant = GenericContainer(DockerImageName.parse("qdrant/qdrant:latest"))
            .withExposedPorts(6334)
            .also { it.start() }
        embedStub = WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort()).also { it.start() }

        qdrantClient = QdrantClient(
            QdrantGrpcClient.newBuilder(qdrant.host, qdrant.getMappedPort(6334), false).build(),
        )
        val embeddingClient = EmbeddingClient(EmbeddingProperties(baseUrl = embedStub.baseUrl(), batchSize = 64))
        service = SearchService(
            qdrantClient,
            embeddingClient,
            QdrantProperties(grpcUrl = "unused", collection = "movies"),
            MoodTagMatcher(embeddingClient),
            TitleMatcher(movieRepository, fixedClock),
            fixedClock,
        )

        createCollection()
        upsertFixtures()
        stubEmbed()
        stubTagEmbed()
    }

    @AfterEach
    fun tearDown() {
        qdrantClient.close()
        qdrant.stop()
        embedStub.stop()
    }

    @Test
    fun `RRF 하이브리드 — dense와 sparse 모두 상위인 영화1이 1위`() {
        // when
        val page = service.search("쿼리", SearchFilter(limit = 5))

        // then
        assertThat(page.hits.map { it.movieId }).containsExactly(1L, 2L, 3L)
        assertThat(page.hasNext).isFalse()
    }

    @Test
    fun `쿼리와 저장 sparse의 공통 토큰을 매칭 단어로 돌려준다 — dense로만 걸린 결과는 빈 목록`() {
        // given — 토큰 1은 영화1, 토큰 2는 영화2만 갖고 있다
        stubEmbed("""{"indices":[1,2],"values":[0.9,0.5],"tokens":["▁탈출","▁감옥"]}""")

        // when
        val page = service.search("탈출 감옥", SearchFilter(limit = 5))

        // then
        assertThat(page.hits.associate { it.movieId to it.matchedTerms }).containsExactlyInAnyOrderEntriesOf(
            mapOf(1L to listOf("탈출"), 2L to listOf("감옥"), 3L to emptyList()),
        )
    }

    @Test
    fun `임베딩만 넘기는 eval 경로는 매칭 단어를 계산하지 않는다`() {
        // given
        val embedding = Embedding(
            dense = listOf(0.9f, 0.1f, 0f, 0f),
            sparse = Embedding.Sparse(listOf(1L), listOf(0.9f), listOf("▁탈출")),
        )

        // when
        val page = service.search(embedding, SearchFilter(limit = 5))

        // then
        assertThat(page.hits.map { it.movieId }).containsExactly(1L, 2L, 3L)
        assertThat(page.hits).allSatisfy { assertThat(it.matchedTerms).isEmpty() }
    }

    @Test
    fun `genreId 다중은 OR로 적용된다`() {
        // when
        val single = service.search("쿼리", SearchFilter(genreIds = listOf(28L), limit = 5))
        val multi = service.search("쿼리", SearchFilter(genreIds = listOf(28L, 53L), limit = 5))

        // then
        assertThat(single.hits.map { it.movieId }).containsExactly(1L, 3L)
        assertThat(multi.hits.map { it.movieId }).containsExactly(1L, 2L, 3L)
    }

    @Test
    fun `excludeGenreId는 하나라도 겹치는 영화를 제외한다`() {
        // when
        val single = service.search("쿼리", SearchFilter(excludeGenreIds = listOf(53L), limit = 5))
        val multi = service.search("쿼리", SearchFilter(excludeGenreIds = listOf(28L, 53L), limit = 5))

        // then
        assertThat(single.hits.map { it.movieId }).containsExactly(1L)
        assertThat(multi.hits).isEmpty()
    }

    @Test
    fun `genreId와 excludeGenreId를 함께 주면 포함 장르 중 제외 장르를 뺀다`() {
        // when
        val filter = SearchFilter(genreIds = listOf(28L), excludeGenreIds = listOf(53L), limit = 5)
        val page = service.search("쿼리", filter)

        // then
        assertThat(page.hits.map { it.movieId }).containsExactly(1L)
    }

    @Test
    fun `yearMin yearMax ratingMin 필터가 payload에 적용된다`() {
        // when
        val byYearMax = service.search("쿼리", SearchFilter(yearMax = 2000, limit = 5))
        val byYearMin = service.search("쿼리", SearchFilter(yearMin = 2000, limit = 5))
        val byRating = service.search("쿼리", SearchFilter(ratingMin = 7.5, limit = 5))

        // then
        assertThat(byYearMax.hits.map { it.movieId }).containsExactly(1L, 2L)
        assertThat(byYearMin.hits.map { it.movieId }).containsExactly(1L, 3L)
        assertThat(byRating.hits.map { it.movieId }).containsExactly(1L, 3L)
    }

    @Test
    fun `runtimeMin runtimeMax voteCountMin voteCountMax 필터가 payload에 적용된다`() {
        // when
        val byRuntimeMin = service.search("쿼리", SearchFilter(runtimeMin = 120, limit = 5))
        val byRuntimeMax = service.search("쿼리", SearchFilter(runtimeMax = 120, limit = 5))
        val byVoteCountMin = service.search("쿼리", SearchFilter(voteCountMin = 100, limit = 5))
        val byVoteCountMax = service.search("쿼리", SearchFilter(voteCountMax = 500, limit = 5))

        // then
        assertThat(byRuntimeMin.hits.map { it.movieId }).containsExactly(1L, 3L)
        assertThat(byRuntimeMax.hits.map { it.movieId }).containsExactly(2L, 3L)
        assertThat(byVoteCountMin.hits.map { it.movieId }).containsExactly(1L, 2L)
        assertThat(byVoteCountMax.hits.map { it.movieId }).containsExactly(2L, 3L)
    }

    @Test
    fun `ratingMin과 voteCountMin voteCountMax 조합이 소수 평론가형 영화만 반환한다`() {
        // when
        val page = service.search("쿼리", SearchFilter(ratingMin = 7.5, voteCountMin = 50, voteCountMax = 500, limit = 5))

        // then
        assertThat(page.hits.map { it.movieId }).containsExactly(3L)
    }

    @Test
    fun `투표 수 조건을 주지 않으면 기본 하한 10 미만인 영화는 빠진다`() {
        // given
        upsertObscure()

        // when
        val page = service.search("쿼리", SearchFilter(limit = 5))

        // then
        assertThat(page.hits.map { it.movieId }).containsExactly(1L, 2L, 3L)
    }

    @Test
    fun `투표 수 상한만 주면 기본 하한 없이 무명작도 검색된다`() {
        // given
        upsertObscure()

        // when
        val page = service.search("쿼리", SearchFilter(voteCountMax = 100, limit = 5))

        // then
        assertThat(page.hits.map { it.movieId }).containsExactly(6L, 3L)
    }

    @Test
    fun `벡터 순위가 바로 아래인 영화도 투표 수가 훨씬 많으면 인기도 보정으로 앞선다`() {
        // given — 영화7이 쿼리 방향에 더 가깝지만 투표 10, 영화8은 조금 멀지만 투표 50000.
        // sparse는 아무도 갖지 않은 토큰이고, 장르 99 필터로 두 영화만 비교한다
        qdrantClient.upsertAsync(
            "movies",
            listOf(
                point(7L, listOf(0f, 0f, 0f, 1f), listOf(7), 2015, 7.0, listOf(99L), emptyList(), emptyList(), 100, 10),
                point(
                    8L,
                    listOf(0f, 0f, 0.3f, 1f),
                    listOf(8),
                    2015,
                    7.0,
                    listOf(99L),
                    emptyList(),
                    emptyList(),
                    100,
                    50000,
                ),
            ),
        ).get()
        stubEmbed(sparse = """{"indices":[99],"values":[0.9]}""", dense = "[0.0,0.0,0.0,1.0]")

        // when
        val page = service.search("쿼리", SearchFilter(genreIds = listOf(99L), limit = 5))

        // then
        assertThat(page.hits.map { it.movieId }).containsExactly(8L, 7L)
        assertThat(page.hits[0].score).isGreaterThan(page.hits[1].score)
    }

    @Test
    fun `RRF k=10 — 한 목록 1위보다 두 목록 모두 2위인 영화가 앞선다`() {
        // given — 장르 98 영화 5편, 투표 수는 같다. sparse 순위는 쿼리 토큰을 많이 가진 순서다.
        // dense 순위: 11(1위) 12(2위) 13(3위) 15(4위) 14(5위) / sparse 순위: 14(1위) 12(2위) 13(3위) 11(4위)
        // k=2면 11이 1/2 + 1/5 = 0.70으로 12(1/3 + 1/3 = 0.67)를 앞서고, k=10이면 12가 2/11 = 0.182로 11(1/10 + 1/13 = 0.177)을 앞선다
        val movie = { id: Long, dense: List<Float>, tokens: List<Int> ->
            point(id, dense, tokens, 2015, 7.0, listOf(98L), emptyList(), emptyList(), 100, 100)
        }
        qdrantClient.upsertAsync(
            "movies",
            listOf(
                movie(11L, listOf(0f, 0f, 0f, 1f), listOf(91)),
                movie(12L, listOf(0f, 0f, 0.2f, 1f), listOf(91, 92, 93)),
                movie(13L, listOf(0f, 0f, 0.5f, 1f), listOf(91, 92)),
                movie(14L, listOf(0f, 0f, 1f, 0.1f), listOf(91, 92, 93, 94)),
                movie(15L, listOf(0f, 0f, 1f, 0.5f), emptyList()),
            ),
        ).get()
        stubEmbed(sparse = """{"indices":[91,92,93,94],"values":[0.9,0.9,0.9,0.9]}""", dense = "[0.0,0.0,0.0,1.0]")

        // when
        val page = service.search("쿼리", SearchFilter(genreIds = listOf(98L), limit = 5))

        // then
        assertThat(page.hits.map { it.movieId }.take(2)).containsExactly(12L, 11L)
    }

    @Test
    fun `제목이 일치하는 영화는 벡터 순위와 투표 수가 낮아도 맨 앞에 온다`() {
        // given — 영화9는 쿼리 방향과 멀고 sparse 토큰도 없으며 투표 12. 쿼리가 영화9의 제목이다
        upsertTitleMatchTarget()
        every { movieRepository.findAllTitles() } returns listOf(MovieTitle(9, "실낙원", null))

        // when
        val page = service.search("실낙원", SearchFilter(limit = 5))

        // then
        assertThat(page.hits.first().movieId).isEqualTo(9L)
    }

    @Test
    fun `임베딩을 넘기는 eval 경로도 query가 있으면 제목 일치를 반영하고 매칭 단어는 계산하지 않는다`() {
        // given
        upsertTitleMatchTarget()
        every { movieRepository.findAllTitles() } returns listOf(MovieTitle(9, "실낙원", null))
        val embedding = Embedding(
            dense = listOf(0.9f, 0.1f, 0f, 0f),
            sparse = Embedding.Sparse(listOf(1L), listOf(0.9f), listOf("▁실")),
        )

        // when
        val page = service.search(embedding, SearchFilter(limit = 5), "실낙원", withTerms = false)

        // then
        assertThat(page.hits.first().movieId).isEqualTo(9L)
        assertThat(page.hits).allSatisfy { assertThat(it.matchedTerms).isEmpty() }
    }

    @Test
    fun `제목이 일치해도 사용자 필터에 걸리면 나오지 않는다`() {
        // given
        upsertTitleMatchTarget()
        every { movieRepository.findAllTitles() } returns listOf(MovieTitle(9, "실낙원", null))

        // when
        val page = service.search("실낙원", SearchFilter(genreIds = listOf(28L), limit = 5))

        // then
        assertThat(page.hits.map { it.movieId }).doesNotContain(9L)
    }

    @Test
    fun `directorId castId 필터가 payload에 적용된다`() {
        // when
        val byDirector = service.search("쿼리", SearchFilter(directorId = 100L, limit = 5))
        val byCast = service.search("쿼리", SearchFilter(castId = 202L, limit = 5))

        // then
        assertThat(byDirector.hits.map { it.movieId }).containsExactly(1L, 3L)
        assertThat(byCast.hits.map { it.movieId }).containsExactly(2L)
    }

    @Test
    fun `offset 페이지네이션과 hasNext가 맞물린다`() {
        // when
        val page1 = service.search("쿼리", SearchFilter(limit = 2))
        val page2 = service.search("쿼리", SearchFilter(limit = 2, offset = 2))

        // then
        assertThat(page1.hits.map { it.movieId }).containsExactly(1L, 2L)
        assertThat(page1.hasNext).isTrue()
        assertThat(page2.hits.map { it.movieId }).containsExactly(3L)
        assertThat(page2.hasNext).isFalse()
    }

    @Test
    fun `similar — 자기 자신을 제외하고 저장 벡터 기준으로 돌려준다`() {
        // when
        val hits = service.similar(movieId = 1L, limit = 3)

        // then — 영화1과 영화2·3의 코사인은 둘 다 0(동률)이라 구성만 확인한다
        assertThat(hits.map { it.movieId }).containsExactlyInAnyOrder(2L, 3L)
    }

    @Test
    fun `similar — 투표 수 10 미만 영화는 빠진다`() {
        // given — 무명작6은 영화1과 가장 가깝다
        upsertObscure()

        // when
        val hits = service.similar(movieId = 1L, limit = 3)

        // then
        assertThat(hits.map { it.movieId }).containsExactlyInAnyOrder(2L, 3L)
    }

    @Test
    fun `similar — 색인 없는 영화는 빈 목록`() {
        // when
        val hits = service.similar(movieId = 99L, limit = 3)

        // then
        assertThat(hits).isEmpty()
    }

    @Test
    fun `recommend — 찜과 가까운 영화를 돌려주고 찜·관심없음·봤어요 영화는 제외한다`() {
        // given — 영화4·5는 영화1(찜) 방향, 영화4는 봤어요
        upsertNearMovie1()

        // when
        val hits = service.recommend(
            likedIds = listOf(1L),
            dislikedIds = listOf(2L),
            watchedIds = listOf(4L),
            limit = 5,
        )

        // then
        assertThat(hits.map { it.movieId }).containsExactly(5L, 3L)
    }

    @Test
    fun `recommend — 색인되지 않은 id는 무시한다`() {
        // given
        upsertNearMovie1()

        // when
        val hits = service.recommend(
            likedIds = listOf(1L, 99L),
            dislikedIds = listOf(98L),
            watchedIds = emptyList(),
            limit = 1,
        )

        // then
        assertThat(hits.map { it.movieId }).containsExactly(4L)
    }

    @Test
    fun `recommend — 투표 수 10 미만 영화는 빠진다`() {
        // given — 무명작6은 찜한 영화1과 가장 가깝다
        upsertNearMovie1()
        upsertObscure()

        // when
        val hits = service.recommend(
            likedIds = listOf(1L),
            dislikedIds = emptyList(),
            watchedIds = emptyList(),
            limit = 2,
        )

        // then
        assertThat(hits.map { it.movieId }).containsExactly(4L, 5L)
    }

    @Test
    fun `recommend — 색인된 찜이 없으면 빈 목록`() {
        // when
        val hits = service.recommend(
            likedIds = listOf(99L),
            dislikedIds = listOf(1L),
            watchedIds = emptyList(),
            limit = 5,
        )

        // then
        assertThat(hits).isEmpty()
    }

    @Test
    fun `쿼리와 가까운 분위기 태그를 가진 영화는 dense 순위가 낮아도 세 번째 후보 목록으로 앞선다`() {
        // given — 기본 쿼리의 상위 태그는 잔잔한·서정적. 영화9만 잔잔한 태그를 갖고 dense는 영화7보다 멀다
        upsertMoodFixtures()

        // when
        val page = service.search("쿼리", SearchFilter(genreIds = listOf(99L), limit = 5))

        // then
        assertThat(page.hits.map { it.movieId }).containsExactly(9L, 7L)
    }

    @Test
    fun `분위기 태그 후보 목록에도 사용자 필터와 투표 수 하한이 적용된다`() {
        // given — 영화10은 투표 3(하한 미만), 영화11은 장르 98. 둘 다 잔잔한 태그를 갖고 쿼리 방향에 가깝다
        upsertMoodFixtures()
        qdrantClient.upsertAsync(
            "movies",
            listOf(
                point(
                    10L,
                    listOf(1f, 0.1f, 0f, 0f),
                    listOf(10),
                    2015,
                    7.0,
                    listOf(99L),
                    emptyList(),
                    emptyList(),
                    voteCount = 3,
                    moodTags = listOf("잔잔한"),
                ),
                point(
                    11L,
                    listOf(1f, 0.1f, 0f, 0f),
                    listOf(11),
                    2015,
                    7.0,
                    listOf(98L),
                    emptyList(),
                    emptyList(),
                    voteCount = 100,
                    moodTags = listOf("잔잔한"),
                ),
            ),
        ).get()

        // when
        val page = service.search("쿼리", SearchFilter(genreIds = listOf(99L), limit = 5))

        // then
        assertThat(page.hits.map { it.movieId }).containsExactly(9L, 7L)
    }

    @Test
    fun `분위기 태그 임베딩이 실패해도 dense와 sparse만으로 검색한다`() {
        // given
        upsertMoodFixtures()
        embedStub.stubFor(
            WireMock.post("/embed")
                .withRequestBody(WireMock.containing(MoodVocabulary.CANDIDATE_TAGS.first()))
                .atPriority(1)
                .willReturn(WireMock.serverError()),
        )

        // when
        val page = service.search("쿼리", SearchFilter(genreIds = listOf(99L), limit = 5))

        // then — 태그 후보 목록이 없으니 dense가 가까운 영화7이 앞선다
        assertThat(page.hits.map { it.movieId }).containsExactly(7L, 9L)
    }

    @Test
    fun `투표 수 조건을 주지 않으면 최근 1년 안에 개봉했거나 개봉 예정인 장편은 투표 수가 적어도 검색된다`() {
        // given
        upsertLowVoteReleases()

        // when
        val page = service.search("쿼리", SearchFilter(limit = 10))

        // then — 400일 전 개봉작과 최근 단편은 하한에 걸린다
        assertThat(page.hits.map { it.movieId }).containsExactlyInAnyOrder(1L, 2L, 3L, RECENT, UPCOMING)
    }

    @Test
    fun `투표 수 조건을 명시하면 최근 개봉작 예외 없이 명시값만 쓴다`() {
        // given
        upsertLowVoteReleases()

        // when
        val byVoteCountMax = service.search("쿼리", SearchFilter(voteCountMax = 100, limit = 10))
        val byVoteCountMin = service.search("쿼리", SearchFilter(voteCountMin = 10, limit = 10))

        // then
        assertThat(byVoteCountMax.hits.map { it.movieId })
            .containsExactlyInAnyOrder(3L, RECENT, OLD, RECENT_SHORT, UPCOMING)
        assertThat(byVoteCountMin.hits.map { it.movieId }).containsExactlyInAnyOrder(1L, 2L, 3L)
    }

    @Test
    fun `similar — 최근 1년 안에 개봉했거나 개봉 예정인 장편은 투표 수가 적어도 나온다`() {
        // given
        upsertLowVoteReleases()

        // when
        val hits = service.similar(movieId = 1L, limit = 10)

        // then
        assertThat(hits.map { it.movieId }).containsExactlyInAnyOrder(2L, 3L, RECENT, UPCOMING)
    }

    @Test
    fun `recommend — 최근 1년 안에 개봉했거나 개봉 예정인 장편은 투표 수가 적어도 추천된다`() {
        // given
        upsertLowVoteReleases()

        // when
        val hits = service.recommend(
            likedIds = listOf(1L),
            dislikedIds = emptyList(),
            watchedIds = emptyList(),
            limit = 10,
        )

        // then
        assertThat(hits.map { it.movieId }).containsExactlyInAnyOrder(2L, 3L, RECENT, UPCOMING)
    }

    @Test
    fun `Qdrant에 연결할 수 없으면 ExternalApiException이 발생한다`() {
        // given
        val unreachable = QdrantClient(
            QdrantGrpcClient.newBuilder("localhost", 1, false).withTimeout(Duration.ofSeconds(1)).build(),
        )
        val embeddingClient = EmbeddingClient(EmbeddingProperties(baseUrl = embedStub.baseUrl(), batchSize = 64))
        val failingService = SearchService(
            unreachable,
            embeddingClient,
            QdrantProperties(grpcUrl = "unused", collection = "movies"),
            MoodTagMatcher(embeddingClient),
            TitleMatcher(movieRepository, fixedClock),
            fixedClock,
        )

        // when & then
        unreachable.use {
            assertThrows<ExternalApiException> { failingService.search("쿼리", SearchFilter(limit = 5)) }
        }
    }

    private fun createCollection() {
        qdrantClient.createCollectionAsync(
            Collections.CreateCollection.newBuilder()
                .setCollectionName("movies")
                .setVectorsConfig(
                    Collections.VectorsConfig.newBuilder().setParamsMap(
                        Collections.VectorParamsMap.newBuilder().putMap(
                            "dense",
                            Collections.VectorParams.newBuilder().setSize(
                                4,
                            ).setDistance(Collections.Distance.Cosine).build(),
                        ),
                    ),
                )
                .setSparseVectorsConfig(
                    Collections.SparseVectorConfig.newBuilder()
                        .putMap("sparse", Collections.SparseVectorParams.newBuilder().build()),
                )
                .build(),
        ).get()
    }

    private fun upsertFixtures() {
        val points = listOf(
            point(
                1L,
                listOf(1f, 0f, 0f, 0f),
                listOf(1),
                2000,
                8.5,
                listOf(28L),
                listOf(100L),
                listOf(200L, 201L),
                142,
                25000,
            ),
            point(2L, listOf(0f, 1f, 0f, 0f), listOf(2), 1995, 7.0, listOf(53L), listOf(101L), listOf(202L), 95, 120),
            point(
                3L,
                listOf(0f, 0f, 1f, 0f),
                listOf(3),
                2010,
                9.0,
                listOf(28L, 53L),
                listOf(100L),
                listOf(200L),
                120,
                80,
            ),
        )
        qdrantClient.upsertAsync("movies", points).get()
    }

    /** 제목 일치 대상 — 쿼리 기본 방향(영화1)과 반대쪽이고 투표 12, 장르 18 */
    private fun upsertTitleMatchTarget() {
        val target =
            point(9L, listOf(0f, 0f, 0f, 1f), emptyList(), 2026, 0.0, listOf(18L), emptyList(), emptyList(), 101, 12)
        qdrantClient.upsertAsync("movies", listOf(target)).get()
    }

    private fun upsertNearMovie1() {
        val points = listOf(
            point(
                4L,
                listOf(0.9f, 0.1f, 0f, 0f),
                listOf(4),
                2001,
                7.0,
                emptyList(),
                emptyList(),
                emptyList(),
                100,
                300,
            ),
            point(5L, listOf(0.8f, 0f, 0.2f, 0f), listOf(5), 2002, 7.0, emptyList(), emptyList(), emptyList(), 100, 40),
        )
        qdrantClient.upsertAsync("movies", points).get()
    }

    /** 영화1과 거의 같은 방향·같은 sparse 토큰의 무명작 (투표 3) — 하한이 없으면 검색·유사·추천 상위에 온다 */
    private fun upsertObscure() {
        val obscure = point(
            6L,
            listOf(1f, 0.05f, 0f, 0f),
            listOf(1),
            2020,
            9.5,
            emptyList(),
            emptyList(),
            emptyList(),
            voteCount = 3,
        )
        qdrantClient.upsertAsync("movies", listOf(obscure)).get()
    }

    /**
     * 영화1 방향·같은 sparse 토큰의 투표 3 영화들 — 개봉일과 러닝타임만 다르다. 기준일은 [TODAY].
     * 최근(30일 전) 장편, 오래된(400일 전) 장편, 최근 단편(20분), 개봉 예정(30일 뒤) 장편
     */
    private fun upsertLowVoteReleases() {
        fun lowVote(id: Long, releaseDate: LocalDate, runtime: Int) = point(
            id,
            listOf(1f, 0.05f, 0f, 0f),
            listOf(1),
            releaseDate.year,
            7.0,
            emptyList(),
            emptyList(),
            emptyList(),
            runtime = runtime,
            voteCount = 3,
            releaseDate = releaseDate,
        )
        qdrantClient.upsertAsync(
            "movies",
            listOf(
                lowVote(RECENT, TODAY.minusDays(30), 100),
                lowVote(OLD, TODAY.minusDays(400), 100),
                lowVote(RECENT_SHORT, TODAY.minusDays(30), 20),
                lowVote(UPCOMING, TODAY.plusDays(30), 100),
            ),
        ).get()
    }

    /**
     * 장르 99의 분위기 비교용 영화 — 영화7은 쿼리 방향에 가깝고 태그가 없다. 영화9는 더 멀지만 잔잔한 태그를 갖는다.
     * 투표 수를 같게 두어 인기도 보정이 순위에 끼어들지 않게 하고, sparse는 쿼리와 겹치지 않는다
     */
    private fun upsertMoodFixtures() {
        qdrantClient.upsertAsync(
            "movies",
            listOf(
                point(
                    7L,
                    listOf(1f, 0.2f, 0f, 0f),
                    listOf(7),
                    2015,
                    7.0,
                    listOf(99L),
                    emptyList(),
                    emptyList(),
                    100,
                    100,
                ),
                point(
                    9L,
                    listOf(0.5f, 0f, 0f, 1f),
                    listOf(9),
                    2015,
                    7.0,
                    listOf(99L),
                    emptyList(),
                    emptyList(),
                    100,
                    100,
                    moodTags = listOf("잔잔한"),
                ),
            ),
        ).get()
    }

    private fun point(
        id: Long,
        dense: List<Float>,
        sparseIndices: List<Int>,
        releaseYear: Int,
        rating: Double,
        genreIds: List<Long>,
        directorIds: List<Long>,
        castIds: List<Long>,
        runtime: Int? = null,
        voteCount: Int? = null,
        moodTags: List<String> = emptyList(),
        releaseDate: LocalDate? = null,
    ): Points.PointStruct = Points.PointStruct.newBuilder()
        .setId(PointIdFactory.id(id))
        .setVectors(
            VectorsFactory.namedVectors(
                mapOf(
                    "dense" to VectorFactory.vector(dense),
                    // sparse는 values 먼저, indices 나중 (VectorFactory 계약) — 개수는 인덱스와 같아야 한다
                    "sparse" to VectorFactory.vector(List(sparseIndices.size) { 1f }, sparseIndices),
                ),
            ),
        )
        .putAllPayload(
            buildMap {
                put("release_year", ValueFactory.value(releaseYear.toLong()))
                put("rating", ValueFactory.value(rating))
                put("genre_ids", ValueFactory.list(genreIds.map { ValueFactory.value(it) }))
                put("director_ids", ValueFactory.list(directorIds.map { ValueFactory.value(it) }))
                put("cast_ids", ValueFactory.list(castIds.map { ValueFactory.value(it) }))
                runtime?.let { put("runtime", ValueFactory.value(it.toLong())) }
                voteCount?.let { put("vote_count", ValueFactory.value(it.toLong())) }
                put("mood_tags", ValueFactory.list(moodTags.map { ValueFactory.value(it) }))
                releaseDate?.let { put("release_date", ValueFactory.value("${it}T00:00:00Z")) }
            },
        )
        .build()

    /** 쿼리 임베딩 스텁 — dense는 기본 영화A 방향, sparse는 기본 토큰 1번(영화A가 보유) */
    private fun stubEmbed(sparse: String = """{"indices":[1],"values":[0.9]}""", dense: String = "[0.9,0.1,0.0,0.0]") {
        embedStub.stubFor(
            WireMock.post("/embed").willReturn(
                WireMock.okJson(
                    """
					{"model":"stub","items":[
						{"dense":$dense,"sparse":$sparse}
					]}
                    """.trimIndent(),
                ),
            ),
        )
    }

    /**
     * 분위기 태그 임베딩 스텁 — 후보 어휘 요청에만 응답한다. 잔잔한은 [0,0,0,1], 서정적은 [0,0,1,0], 나머지는 [-1,0,0,0]이라
     * 기본 쿼리([0.9,0.1,0,0])의 상위 2개 태그는 잔잔한·서정적이다
     */
    private fun stubTagEmbed() {
        val items = MoodVocabulary.CANDIDATE_TAGS.map { tag ->
            val dense = when (tag) {
                "잔잔한" -> "[0.0,0.0,0.0,1.0]"
                "서정적" -> "[0.0,0.0,1.0,0.0]"
                else -> "[-1.0,0.0,0.0,0.0]"
            }
            """{"dense":$dense,"sparse":{"indices":[1],"values":[0.1]}}"""
        }
        embedStub.stubFor(
            WireMock.post("/embed")
                .withRequestBody(WireMock.containing(MoodVocabulary.CANDIDATE_TAGS.first()))
                .atPriority(1)
                .willReturn(WireMock.okJson("""{"model":"stub","items":[${items.joinToString(",")}]}""")),
        )
    }

    companion object {
        /** 서비스 Clock의 오늘 (UTC 자정) */
        private val TODAY: LocalDate = LocalDate.of(2026, 10, 22)
        private const val RECENT = 12L
        private const val OLD = 13L
        private const val RECENT_SHORT = 14L
        private const val UPCOMING = 15L
    }
}
