package com.whsanha55.cineseek.service

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.whsanha55.cineseek.config.EmbeddingProperties
import com.whsanha55.cineseek.config.QdrantProperties
import com.whsanha55.cineseek.client.EmbeddingClient
import io.qdrant.client.QdrantClient
import io.qdrant.client.QdrantGrpcClient
import kotlin.test.assertEquals
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
	fun `reindex가 컬렉션을 재생성하고 포인트를 upsert한다 — 재실행해도 idempotent`() {
		stubEmbed()
		val movies = (1L..3L).map {
			IndexedMovie(it, "줄거리 $it", MoviePayload("영화$it", 2000, 8.0, listOf("액션"), listOf("감독$it"), listOf("배우$it")))
		}

		indexer.reindex(movies)
		assertEquals(3L, qdrantClient.countAsync("movies").get())

		indexer.reindex(movies) // 재생성 → 재upsert — 포인트 중복 없음
		assertEquals(3L, qdrantClient.countAsync("movies").get())
	}

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
