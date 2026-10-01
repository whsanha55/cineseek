package com.whsanha55.cineseek.search.service

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.whsanha55.cineseek.external.embedding.client.EmbeddingClient
import com.whsanha55.cineseek.external.embedding.config.EmbeddingProperties
import com.whsanha55.cineseek.external.qdrant.config.QdrantProperties
import com.whsanha55.cineseek.global.exception.ExternalApiException
import com.whsanha55.cineseek.search.vo.SearchFilter
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
import java.time.Duration

/**
 * SearchService 통합 테스트 — 실제 Qdrant 컨테이너(dense 4차원 + sparse) + WireMock 임베딩 스텁.
 * prefetch 20 + RRF + payload ID 필터 이식이 실제로 맞물리는지 확인한다
 */
class SearchServiceTest {

    private lateinit var qdrant: GenericContainer<*>
    private lateinit var embedStub: WireMockServer
    private lateinit var qdrantClient: QdrantClient
    private lateinit var service: SearchService

    @BeforeEach
    fun setUp() {
        qdrant = GenericContainer(DockerImageName.parse("qdrant/qdrant:latest"))
            .withExposedPorts(6334)
            .also { it.start() }
        embedStub = WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort()).also { it.start() }

        qdrantClient = QdrantClient(
            QdrantGrpcClient.newBuilder(qdrant.host, qdrant.getMappedPort(6334), false).build(),
        )
        service = SearchService(
            qdrantClient,
            EmbeddingClient(EmbeddingProperties(baseUrl = embedStub.baseUrl(), batchSize = 64)),
            QdrantProperties(grpcUrl = "unused", collection = "movies"),
        )

        createCollection()
        upsertFixtures()
        stubEmbed()
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
    fun `genreId 다중은 OR로 적용된다`() {
        // when
        val single = service.search("쿼리", SearchFilter(genreIds = listOf(28L), limit = 5))
        val multi = service.search("쿼리", SearchFilter(genreIds = listOf(28L, 53L), limit = 5))

        // then
        assertThat(single.hits.map { it.movieId }).containsExactly(1L, 3L)
        assertThat(multi.hits.map { it.movieId }).containsExactly(1L, 2L, 3L)
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
    fun `similar — 색인 없는 영화는 빈 목록`() {
        // when
        val hits = service.similar(movieId = 99L, limit = 3)

        // then
        assertThat(hits).isEmpty()
    }

    @Test
    fun `Qdrant에 연결할 수 없으면 ExternalApiException이 발생한다`() {
        // given
        val unreachable = QdrantClient(
            QdrantGrpcClient.newBuilder("localhost", 1, false).withTimeout(Duration.ofSeconds(1)).build(),
        )
        val failingService = SearchService(
            unreachable,
            EmbeddingClient(EmbeddingProperties(baseUrl = embedStub.baseUrl(), batchSize = 64)),
            QdrantProperties(grpcUrl = "unused", collection = "movies"),
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
            point(1L, listOf(1f, 0f, 0f, 0f), listOf(1), 2000, 8.5, listOf(28L), listOf(100L), listOf(200L, 201L)),
            point(2L, listOf(0f, 1f, 0f, 0f), listOf(2), 1995, 7.0, listOf(53L), listOf(101L), listOf(202L)),
            point(3L, listOf(0f, 0f, 1f, 0f), listOf(3), 2010, 9.0, listOf(28L, 53L), listOf(100L), listOf(200L)),
        )
        qdrantClient.upsertAsync("movies", points).get()
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
            mapOf(
                "release_year" to ValueFactory.value(releaseYear.toLong()),
                "rating" to ValueFactory.value(rating),
                "genre_ids" to ValueFactory.list(genreIds.map { ValueFactory.value(it) }),
                "director_ids" to ValueFactory.list(directorIds.map { ValueFactory.value(it) }),
                "cast_ids" to ValueFactory.list(castIds.map { ValueFactory.value(it) }),
            ),
        )
        .build()

    /** 쿼리 임베딩 스텁 — dense는 영화A 방향, sparse는 토큰 1번(영화A가 보유) */
    private fun stubEmbed() {
        embedStub.stubFor(
            WireMock.post("/embed").willReturn(
                WireMock.okJson(
                    """
					{"model":"stub","items":[
						{"dense":[0.9,0.1,0.0,0.0],"sparse":{"indices":[1],"values":[0.9]}}
					]}
                    """.trimIndent(),
                ),
            ),
        )
    }
}
