package com.whsanha55.cineseek.search

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.whsanha55.cineseek.config.EmbeddingProperties
import com.whsanha55.cineseek.config.QdrantProperties
import com.whsanha55.cineseek.embedding.EmbeddingClient
import io.qdrant.client.PointIdFactory
import io.qdrant.client.QdrantClient
import io.qdrant.client.QdrantGrpcClient
import io.qdrant.client.ValueFactory
import io.qdrant.client.VectorFactory
import io.qdrant.client.VectorsFactory
import io.qdrant.client.grpc.Collections
import io.qdrant.client.grpc.JsonWithInt
import io.qdrant.client.grpc.Points
import kotlin.test.assertEquals
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName

/**
 * SearchService 통합 테스트 — 실제 Qdrant 컨테이너(dense 4차원 + sparse) + WireMock 임베딩 스텁.
 * search.py의 prefetch 20 + RRF + payload 필터 이식이 실제로 맞물리는지 확인한다
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
	fun `RRF 하이브리드 — dense와 sparse 모두 상위인 영화A가 1위`() {
		val results = service.search("쿼리", null, null, 5)

		assertEquals(listOf("영화A", "영화B", "영화C"), results.map { it.title })
		assertEquals(listOf("범죄"), results.first().genres)
		assertEquals(2000L, results.first().releaseYear)
		assertEquals(8.5, results.first().rating)
	}

	@Test
	fun `genre 필터와 yearMin 필터가 payload에 적용된다`() {
		assertEquals(listOf("영화A", "영화C"), service.search("쿼리", "범죄", null, 5).map { it.title })
		assertEquals(listOf("영화A", "영화C"), service.search("쿼리", null, 2000, 5).map { it.title })
		assertEquals(listOf("영화C"), service.search("쿼리", "범죄", 2005, 5).map { it.title })
	}

	private fun createCollection() {
		qdrantClient.createCollectionAsync(
			Collections.CreateCollection.newBuilder()
				.setCollectionName("movies")
				.setVectorsConfig(
					Collections.VectorsConfig.newBuilder().setParamsMap(
						Collections.VectorParamsMap.newBuilder().putMap(
							"dense",
							Collections.VectorParams.newBuilder().setSize(4).setDistance(Collections.Distance.Cosine).build(),
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
			point(1L, listOf(1f, 0f, 0f, 0f), listOf(1), "영화A", 2000, 8.5, listOf("범죄"), listOf("감독A")),
			point(2L, listOf(0f, 1f, 0f, 0f), listOf(2), "영화B", 1995, 7.0, listOf("로맨스"), listOf("감독B")),
			point(3L, listOf(0f, 0f, 1f, 0f), listOf(3), "영화C", 2010, 9.0, listOf("범죄"), listOf("감독C")),
		)
		qdrantClient.upsertAsync("movies", points).get()
	}

	private fun point(
		id: Long,
		dense: List<Float>,
		sparseIndices: List<Int>,
		title: String,
		releaseYear: Int,
		rating: Double,
		genres: List<String>,
		directors: List<String>,
	): Points.PointStruct =
		Points.PointStruct.newBuilder()
			.setId(PointIdFactory.id(id))
			.setVectors(
				VectorsFactory.namedVectors(
					mapOf(
						"dense" to VectorFactory.vector(dense),
						// sparse는 values 먼저, indices 나중 (VectorFactory 계약)
						"sparse" to VectorFactory.vector(listOf(1f), sparseIndices),
					),
				),
			)
			.putAllPayload(
				mapOf(
					"title" to ValueFactory.value(title),
					"release_year" to ValueFactory.value(releaseYear.toLong()),
					"rating" to ValueFactory.value(rating),
					"genres" to ValueFactory.list(genres.map { ValueFactory.value(it) }),
					"directors" to ValueFactory.list(directors.map { ValueFactory.value(it) }),
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
