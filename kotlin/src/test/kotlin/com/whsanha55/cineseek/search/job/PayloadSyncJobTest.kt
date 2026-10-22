package com.whsanha55.cineseek.search.job

import com.whsanha55.cineseek.external.embedding.client.EmbeddingClient
import com.whsanha55.cineseek.external.embedding.config.EmbeddingProperties
import com.whsanha55.cineseek.external.qdrant.config.QdrantProperties
import com.whsanha55.cineseek.search.service.IndexedMovieAssembler
import com.whsanha55.cineseek.search.service.MovieIndexer
import com.whsanha55.cineseek.search.vo.PayloadSyncFields
import io.mockk.every
import io.mockk.mockk
import io.qdrant.client.PointIdFactory
import io.qdrant.client.QdrantClient
import io.qdrant.client.QdrantGrpcClient
import io.qdrant.client.ValueFactory
import io.qdrant.client.VectorFactory
import io.qdrant.client.VectorsFactory
import io.qdrant.client.grpc.Collections
import io.qdrant.client.grpc.JsonWithInt
import io.qdrant.client.grpc.Points
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName
import java.time.LocalDate

/**
 * PayloadSyncJob — 실제 Qdrant 컨테이너에 mood_tags·release_date 없이 색인된 포인트(이전 방식)를 두고 동기화를 검증한다.
 * PG 조립(IndexedMovieAssembler.payloadSyncFieldsAll)은 IndexedMovieAssemblerTest가 검증하므로 MockK로 대신한다.
 * 임베딩 서버는 닿지 않는 주소다 — 동기화가 임베딩을 부르면 실패한다
 */
class PayloadSyncJobTest {

    private lateinit var qdrant: GenericContainer<*>
    private lateinit var qdrantClient: QdrantClient
    private val assembler = mockk<IndexedMovieAssembler>()
    private lateinit var job: PayloadSyncJob

    @BeforeEach
    fun setUp() {
        qdrant = GenericContainer(DockerImageName.parse("qdrant/qdrant:latest"))
            .withExposedPorts(6334)
            .also { it.start() }
        qdrantClient = QdrantClient(
            QdrantGrpcClient.newBuilder(qdrant.host, qdrant.getMappedPort(6334), false).build(),
        )
        val indexer = MovieIndexer(
            qdrantClient,
            QdrantProperties(grpcUrl = "unused", collection = "movies"),
            EmbeddingClient(EmbeddingProperties(baseUrl = "http://localhost:1", batchSize = 64)),
        )
        job = PayloadSyncJob(assembler, indexer)

        createLegacyCollection()
    }

    @AfterEach
    fun tearDown() {
        qdrantClient.close()
        qdrant.stop()
    }

    @Test
    fun `색인된 포인트의 mood_tags·release_date만 덮어쓰고 색인되지 않은 영화는 건너뛴다`() {
        // given — 영화2는 예전 태그가 남아 있고 개봉일이 없다. 영화99는 PG에만 있다
        every { assembler.payloadSyncFieldsAll() } returns mapOf(
            1L to PayloadSyncFields(listOf("잔잔한", "힐링"), LocalDate.of(2026, 10, 21)),
            2L to PayloadSyncFields(emptyList(), null),
            99L to PayloadSyncFields(listOf("유쾌한"), LocalDate.of(2026, 1, 1)),
        )

        // when
        val synced = job.sync()
        val points = qdrantClient
            .retrieveAsync("movies", listOf(PointIdFactory.id(1L), PointIdFactory.id(2L)), true, false, null)
            .get()
            .associateBy { it.id.num }

        // then
        assertThat(synced).isEqualTo(2)
        assertThat(points.getValue(1L).payloadMap.tags()).containsExactly("잔잔한", "힐링")
        assertThat(points.getValue(2L).payloadMap.tags()).isEmpty()
        assertThat(points.getValue(1L).payloadMap["release_date"]?.stringValue).isEqualTo("2026-10-21T00:00:00Z")
        assertThat(points.getValue(2L).payloadMap).doesNotContainKey("release_date")
        points.values.forEach { point ->
            assertThat(point.payloadMap["rating"]?.doubleValue).isEqualTo(8.0)
            assertThat(point.payloadMap["input_hash"]?.stringValue).isEqualTo("hash${point.id.num}")
        }
        assertThat(qdrantClient.countAsync("movies").get()).isEqualTo(2L)
    }

    @Test
    fun `기존 컬렉션에 mood_tags keyword·release_date datetime 인덱스를 만든다 — 다시 실행해도 된다`() {
        // given
        every { assembler.payloadSyncFieldsAll() } returns mapOf(
            1L to PayloadSyncFields(listOf("잔잔한"), LocalDate.of(2026, 10, 21)),
        )

        // when
        job.sync()
        job.sync()
        val schema = qdrantClient.getCollectionInfoAsync("movies").get().payloadSchemaMap

        // then
        assertThat(schema["mood_tags"]?.dataType).isEqualTo(Collections.PayloadSchemaType.Keyword)
        assertThat(schema["release_date"]?.dataType).isEqualTo(Collections.PayloadSchemaType.Datetime)
    }

    private fun Map<String, JsonWithInt.Value>.tags() = get("mood_tags")?.listValue?.valuesList?.map { it.stringValue }

    /** mood_tags·release_date payload와 인덱스가 없던 때의 컬렉션 — 포인트 1, 2 */
    private fun createLegacyCollection() {
        qdrantClient.createCollectionAsync(
            Collections.CreateCollection.newBuilder()
                .setCollectionName("movies")
                .setVectorsConfig(
                    Collections.VectorsConfig.newBuilder().setParamsMap(
                        Collections.VectorParamsMap.newBuilder().putMap(
                            "dense",
                            Collections.VectorParams.newBuilder()
                                .setSize(4)
                                .setDistance(Collections.Distance.Cosine)
                                .build(),
                        ),
                    ),
                )
                .build(),
        ).get()
        qdrantClient.upsertAsync(
            "movies",
            listOf(
                legacyPoint(1L, mapOf()),
                legacyPoint(2L, mapOf("mood_tags" to ValueFactory.list(listOf(ValueFactory.value("슬픈"))))),
            ),
        ).get()
    }

    private fun legacyPoint(id: Long, extra: Map<String, JsonWithInt.Value>): Points.PointStruct =
        Points.PointStruct.newBuilder()
            .setId(PointIdFactory.id(id))
            .setVectors(VectorsFactory.namedVectors(mapOf("dense" to VectorFactory.vector(listOf(1f, 0f, 0f, 0f)))))
            .putAllPayload(
                mapOf(
                    "rating" to ValueFactory.value(8.0),
                    "input_hash" to ValueFactory.value("hash$id"),
                ) + extra,
            )
            .build()
}
