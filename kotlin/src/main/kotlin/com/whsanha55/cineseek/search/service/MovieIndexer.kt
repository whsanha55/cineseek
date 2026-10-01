package com.whsanha55.cineseek.search.service

import com.whsanha55.cineseek.external.embedding.client.EmbeddingClient
import com.whsanha55.cineseek.external.qdrant.config.QdrantProperties
import com.whsanha55.cineseek.search.vo.IndexedMovie
import com.whsanha55.cineseek.search.vo.MoviePayload
import io.github.oshai.kotlinlogging.KotlinLogging
import io.qdrant.client.PointIdFactory
import io.qdrant.client.QdrantClient
import io.qdrant.client.ValueFactory
import io.qdrant.client.VectorFactory
import io.qdrant.client.VectorsFactory
import io.qdrant.client.WithPayloadSelectorFactory
import io.qdrant.client.WithVectorsSelectorFactory
import io.qdrant.client.grpc.Collections
import io.qdrant.client.grpc.JsonWithInt
import io.qdrant.client.grpc.Points
import org.springframework.stereotype.Component
import java.security.MessageDigest

private val log = KotlinLogging.logger {}

/**
 * Qdrant 파생 인덱스 관리 — 증분 색인. 컬렉션(dense 1024 cosine + sparse)이 없을 때만 만든다.
 * 줄거리 해시(payload overview_hash)가 다른 영화만 임베딩하고, 같은 영화는 payload만 덮어쓴다.
 * 임베딩은 묶음마다 바로 upsert한다 — 중간에 끊겨도 재실행하면 남은 영화만 이어서 처리된다
 */
@Component
class MovieIndexer(
    private val qdrantClient: QdrantClient,
    private val qdrantProperties: QdrantProperties,
    private val embeddingClient: EmbeddingClient,
) {

    /** @return 이번에 임베딩한 영화 수 */
    fun index(movies: List<IndexedMovie>): Int {
        ensureCollection()
        val stored = storedHashes()
        val (unchanged, changed) = movies.partition { stored[it.movieId] == hash(it.overview) }

        unchanged.forEach { movie ->
            qdrantClient.overwritePayloadAsync(
                qdrantProperties.collection,
                movie.toPayload(),
                PointIdFactory.id(movie.movieId),
                true,
                null,
                null,
            ).get()
        }
        log.info { "payload만 갱신. unchanged=${unchanged.size}, 임베딩 대상=${changed.size}" }

        changed.chunked(UPSERT_BATCH).forEachIndexed { i, batch ->
            val embeddings = embeddingClient.embed(batch.map { it.overview })
            val points = batch.mapIndexed { j, movie ->
                Points.PointStruct.newBuilder()
                    .setId(PointIdFactory.id(movie.movieId)) // movie_id(PG PK)를 포인트 id로
                    .setVectors(
                        VectorsFactory.namedVectors(
                            mapOf(
                                "dense" to VectorFactory.vector(embeddings[j].dense),
                                "sparse" to VectorFactory.vector(
                                    embeddings[j].sparse.values,
                                    embeddings[j].sparse.indices.map(Long::toInt),
                                ),
                            ),
                        ),
                    )
                    .putAllPayload(movie.toPayload())
                    .build()
            }
            qdrantClient.upsertAsync(qdrantProperties.collection, points).get()
            log.info { "임베딩 upsert. ${minOf((i + 1) * UPSERT_BATCH, changed.size)}/${changed.size}" }
        }
        return changed.size
    }

    private fun ensureCollection() {
        if (qdrantClient.collectionExistsAsync(qdrantProperties.collection).get()) {
            return
        }
        qdrantClient.createCollectionAsync(
            Collections.CreateCollection.newBuilder()
                .setCollectionName(qdrantProperties.collection)
                .setVectorsConfig(
                    Collections.VectorsConfig.newBuilder().setParamsMap(
                        Collections.VectorParamsMap.newBuilder().putMap(
                            "dense",
                            Collections.VectorParams.newBuilder().setSize(
                                DENSE_DIM,
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
        log.info { "컬렉션 생성(dense+sparse). collection=${qdrantProperties.collection}" }
    }

    /** 저장된 포인트별 줄거리 해시 — 해시가 없는 포인트(이전 방식 색인)는 다시 임베딩된다 */
    private fun storedHashes(): Map<Long, String> = buildMap {
        var offset: Points.PointId? = null
        do {
            val request = Points.ScrollPoints.newBuilder()
                .setCollectionName(qdrantProperties.collection)
                .setLimit(SCROLL_LIMIT)
                .setWithPayload(WithPayloadSelectorFactory.include(listOf(OVERVIEW_HASH)))
                .setWithVectors(WithVectorsSelectorFactory.enable(false))
            offset?.let(request::setOffset)
            val response = qdrantClient.scrollAsync(request.build()).get()
            response.resultList.forEach { point ->
                point.payloadMap[OVERVIEW_HASH]?.stringValue?.let { put(point.id.num, it) }
            }
            offset = if (response.hasNextPageOffset()) response.nextPageOffset else null
        } while (offset != null)
    }

    private fun IndexedMovie.toPayload(): Map<String, JsonWithInt.Value> =
        payload.toGrpc() + (OVERVIEW_HASH to ValueFactory.value(hash(overview)))

    private fun hash(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).toHexString()

    private fun MoviePayload.toGrpc(): Map<String, JsonWithInt.Value> = buildMap {
        title?.let { put("title", ValueFactory.value(it)) }
        releaseYear?.let { put("release_year", ValueFactory.value(it.toLong())) }
        rating?.let { put("rating", ValueFactory.value(it)) }
        put("genre_ids", ValueFactory.list(genreIds.map { ValueFactory.value(it) }))
        put("director_ids", ValueFactory.list(directorIds.map { ValueFactory.value(it) }))
        put("cast_ids", ValueFactory.list(castIds.map { ValueFactory.value(it) }))
    }

    companion object {
        private const val DENSE_DIM = 1024L // bge-m3
        private const val UPSERT_BATCH = 256 // 임베딩·upsert 묶음 — 묶음마다 저장돼 진행이 보존된다
        private const val SCROLL_LIMIT = 1000
        private const val OVERVIEW_HASH = "overview_hash"
    }
}
