package com.whsanha55.cineseek.search.service

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.whsanha55.cineseek.external.embedding.client.EmbeddingClient
import com.whsanha55.cineseek.external.embedding.config.EmbeddingProperties
import com.whsanha55.cineseek.external.qdrant.config.QdrantProperties
import com.whsanha55.cineseek.search.vo.IndexedMovie
import com.whsanha55.cineseek.search.vo.MoviePayload
import io.qdrant.client.PointIdFactory
import io.qdrant.client.QdrantClient
import io.qdrant.client.QdrantGrpcClient
import io.qdrant.client.grpc.JsonWithInt
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName

/** MovieIndexer — 실제 Qdrant 컨테이너 + 임베딩 스텁으로 컬렉션 재생성·upsert 검증 */
class MovieIndexerTest {

    private lateinit var qdrant: GenericContainer<*>
    private lateinit var embedStub: WireMockServer
    private lateinit var qdrantClient: QdrantClient
    private lateinit var indexer: MovieIndexer

    @BeforeEach
    fun setUp() {
        qdrant = GenericContainer(DockerImageName.parse("qdrant/qdrant:latest"))
            .withExposedPorts(6334)
            .also { it.start() }
        embedStub = WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort()).also { it.start() }

        qdrantClient = QdrantClient(
            QdrantGrpcClient.newBuilder(qdrant.host, qdrant.getMappedPort(6334), false).build(),
        )
        indexer = MovieIndexer(
            qdrantClient,
            QdrantProperties(grpcUrl = "unused", collection = "movies"),
            EmbeddingClient(EmbeddingProperties(baseUrl = embedStub.baseUrl(), batchSize = 64)),
        )
    }

    @AfterEach
    fun tearDown() {
        qdrantClient.close()
        qdrant.stop()
        embedStub.stop()
    }

    @Test
    fun `재실행하면 줄거리가 같은 영화는 다시 임베딩하지 않는다`() {
        // given
        stubEmbed()
        val movies = (1L..3L).map { movie(it, "줄거리 $it") }

        // when
        val first = indexer.index(movies)
        val second = indexer.index(movies)

        // then
        assertThat(first).isEqualTo(3)
        assertThat(second).isZero()
        assertThat(qdrantClient.countAsync("movies").get()).isEqualTo(3L)
        embedStub.verify(1, WireMock.postRequestedFor(WireMock.urlPathEqualTo("/embed")))
    }

    @Test
    fun `줄거리가 바뀐 영화만 다시 임베딩하고 나머지는 payload만 갱신한다`() {
        // given
        stubEmbed()
        indexer.index(listOf(movie(1L, "줄거리 1"), movie(2L, "줄거리 2")))
        val updated = listOf(movie(1L, "줄거리 1", rating = 9.5), movie(2L, "바뀐 줄거리 2"))

        // when
        val embedded = indexer.index(updated)
        val point1 = qdrantClient
            .retrieveAsync("movies", listOf(PointIdFactory.id(1L)), true, false, null)
            .get()
            .single()

        // then
        assertThat(embedded).isEqualTo(1)
        assertThat(point1.payloadMap["rating"]?.doubleValue).isEqualTo(9.5)
    }

    private fun movie(id: Long, overview: String, rating: Double = 8.0) = IndexedMovie(
        id,
        overview,
        MoviePayload("영화$id", 2000, rating, listOf(28L), listOf(100L + id), listOf(200L + id)),
    )

    @Test
    fun `payload에 필터용 id 필드가 내려간다`() {
        // given
        stubEmbed()
        val payload = MoviePayload("영화", 2000, 8.0, listOf(28L), listOf(100L), listOf(200L, 201L))
        val movie = IndexedMovie(1L, "줄거리", payload)

        // when
        indexer.index(listOf(movie))
        val point = qdrantClient
            .retrieveAsync("movies", listOf(PointIdFactory.id(1L)), true, false, null)
            .get()
            .single()

        // then
        assertThat(point.payloadMap.ids("genre_ids")).containsExactly(28L)
        assertThat(point.payloadMap.ids("director_ids")).containsExactly(100L)
        assertThat(point.payloadMap.ids("cast_ids")).containsExactly(200L, 201L)
    }

    private fun Map<String, JsonWithInt.Value>.ids(key: String) =
        get(key)?.listValue?.valuesList?.map { it.integerValue }

    /** dense 1024(bge-m3 차원) 스텁 — 요청 3텍스트에 대해 항목 3개 반환 */
    private fun stubEmbed() {
        val item = """{"dense":[${List(1024) { "0.01" }.joinToString(",")}],"sparse":{"indices":[1],"values":[0.5]}}"""
        embedStub.stubFor(
            WireMock.post("/embed").willReturn(
                WireMock.okJson("""{"model":"stub","items":[$item, $item, $item]}"""),
            ),
        )
    }
}
