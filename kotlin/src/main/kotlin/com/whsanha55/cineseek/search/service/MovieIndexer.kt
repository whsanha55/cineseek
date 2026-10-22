package com.whsanha55.cineseek.search.service

import com.whsanha55.cineseek.external.embedding.client.EmbeddingClient
import com.whsanha55.cineseek.external.qdrant.config.QdrantProperties
import com.whsanha55.cineseek.search.vo.IndexedMovie
import com.whsanha55.cineseek.search.vo.MoviePayload
import com.whsanha55.cineseek.search.vo.PayloadSyncFields
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
import java.time.LocalDate
import java.time.ZoneOffset

private val log = KotlinLogging.logger {}

/**
 * Qdrant 파생 인덱스 관리 — 증분 색인. 컬렉션(dense 1024 cosine + sparse)이 없을 때만 만든다.
 * 임베딩 입력 전체 해시(payload input_hash)가 다른 영화만 임베딩하고, 같은 영화는 payload만 덮어쓴다.
 * 임베딩은 묶음마다 바로 upsert한다 — 중간에 끊겨도 재실행하면 남은 영화만 이어서 처리된다
 */
@Component
class MovieIndexer(
    private val qdrantClient: QdrantClient,
    private val qdrantProperties: QdrantProperties,
    private val embeddingClient: EmbeddingClient,
) {

    /** 전체 재색인(ReindexJob) — 컬렉션 전체를 scroll해 저장 해시와 비교한다. @return 이번에 임베딩한 영화 수 */
    fun index(movies: List<IndexedMovie>): Int {
        ensureCollection()
        return upsertByHash(movies, storedHashes())
    }

    /**
     * 증분 색인(수집 틱) — 대상 포인트만 retrieveAsync로 조회해 저장 해시와 비교한다.
     * 매 틱 전체 scroll을 하지 않기 위한 경로다. @return 이번에 임베딩한 영화 수
     */
    fun indexTouched(movies: List<IndexedMovie>): Int {
        if (movies.isEmpty()) {
            return 0
        }
        ensureCollection()
        val points = qdrantClient
            .retrieveAsync(
                qdrantProperties.collection,
                movies.map { PointIdFactory.id(it.movieId) },
                true,
                false,
                null,
            )
            .get()
        val stored = buildMap {
            points.forEach { point -> point.payloadMap[INPUT_HASH]?.stringValue?.let { put(point.id.num, it) } }
        }
        return upsertByHash(movies, stored)
    }

    /**
     * payload 동기화(PayloadSyncJob) — 색인된 포인트의 mood_tags·release_date만 set_payload로 덮어쓴다.
     * 다른 키(input_hash 포함)는 그대로 두고 임베딩은 다시 하지 않는다. 이 필드가 payload에 들어가기 전에 색인된 포인트를
     * 채우는 용도다. PG에만 있고 색인되지 않은 영화는 건너뛰고, 개봉일이 없는 영화는 release_date를 쓰지 않는다.
     * @return payload를 쓴 포인트 수
     */
    fun syncPayload(fields: Map<Long, PayloadSyncFields>): Int {
        ensureCollection()
        createPayloadIndexes()
        val targets = storedHashes().keys.filter { it in fields }
        targets.chunked(SET_PAYLOAD_BATCH).forEachIndexed { i, batch ->
            val operations = batch.map { movieId ->
                val synced = fields.getValue(movieId)
                Points.PointsUpdateOperation.newBuilder()
                    .setSetPayload(
                        Points.PointsUpdateOperation.SetPayload.newBuilder()
                            .putPayload(MOOD_TAGS, ValueFactory.list(synced.moodTags.map { ValueFactory.value(it) }))
                            .apply { synced.releaseDate?.let { putPayload(RELEASE_DATE, releaseDateValue(it)) } }
                            .setPointsSelector(
                                Points.PointsSelector.newBuilder()
                                    .setPoints(Points.PointsIdsList.newBuilder().addIds(PointIdFactory.id(movieId))),
                            ),
                    )
                    .build()
            }
            qdrantClient.batchUpdateAsync(qdrantProperties.collection, operations).get()
            log.info { "payload 동기화. ${minOf((i + 1) * SET_PAYLOAD_BATCH, targets.size)}/${targets.size}" }
        }
        return targets.size
    }

    /** 컬렉션의 포인트 수 — 수집 통계(색인 미완료 = PG 영화 수 − 포인트 수)용. 컬렉션이 없으면 0 */
    fun countPoints(): Long {
        if (!qdrantClient.collectionExistsAsync(qdrantProperties.collection).get()) {
            return 0L
        }
        return qdrantClient.countAsync(qdrantProperties.collection).get()
    }

    private fun upsertByHash(movies: List<IndexedMovie>, stored: Map<Long, String>): Int {
        val (unchanged, changed) = movies.partition { stored[it.movieId] == hash(it.embeddingInput) }

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
            val embeddings = embeddingClient.embed(batch.map { it.embeddingInput })
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
        createPayloadIndexes()
        log.info { "컬렉션 생성(dense+sparse). collection=${qdrantProperties.collection}" }
    }

    /**
     * payload 인덱스 — mood_tags(keyword)는 검색의 태그 일치 필터용, release_date(datetime)는 투표 수 하한의 최근 개봉 조건용.
     * 이미 있으면 Qdrant가 그대로 둔다
     */
    private fun createPayloadIndexes() {
        mapOf(
            MOOD_TAGS to Collections.PayloadSchemaType.Keyword,
            RELEASE_DATE to Collections.PayloadSchemaType.Datetime,
        ).forEach { (field, type) ->
            qdrantClient.createPayloadIndexAsync(qdrantProperties.collection, field, type, null, true, null, null).get()
        }
    }

    /** 저장된 포인트별 임베딩 입력 해시 — 해시가 없는 포인트(이전 방식 색인)는 다시 임베딩된다 */
    private fun storedHashes(): Map<Long, String> = buildMap {
        var offset: Points.PointId? = null
        do {
            val request = Points.ScrollPoints.newBuilder()
                .setCollectionName(qdrantProperties.collection)
                .setLimit(SCROLL_LIMIT)
                .setWithPayload(WithPayloadSelectorFactory.include(listOf(INPUT_HASH)))
                .setWithVectors(WithVectorsSelectorFactory.enable(false))
            offset?.let(request::setOffset)
            val response = qdrantClient.scrollAsync(request.build()).get()
            response.resultList.forEach { point ->
                point.payloadMap[INPUT_HASH]?.stringValue?.let { put(point.id.num, it) }
            }
            offset = if (response.hasNextPageOffset()) response.nextPageOffset else null
        } while (offset != null)
    }

    private fun IndexedMovie.toPayload(): Map<String, JsonWithInt.Value> =
        payload.toGrpc() + (INPUT_HASH to ValueFactory.value(hash(embeddingInput)))

    private fun hash(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).toHexString()

    private fun MoviePayload.toGrpc(): Map<String, JsonWithInt.Value> = buildMap {
        title?.let { put("title", ValueFactory.value(it)) }
        releaseYear?.let { put("release_year", ValueFactory.value(it.toLong())) }
        rating?.let { put("rating", ValueFactory.value(it)) }
        put("genre_ids", ValueFactory.list(genreIds.map { ValueFactory.value(it) }))
        put("director_ids", ValueFactory.list(directorIds.map { ValueFactory.value(it) }))
        put("cast_ids", ValueFactory.list(castIds.map { ValueFactory.value(it) }))
        runtime?.let { put("runtime", ValueFactory.value(it.toLong())) }
        voteCount?.let { put("vote_count", ValueFactory.value(it.toLong())) }
        put(MOOD_TAGS, ValueFactory.list(moodTags.map { ValueFactory.value(it) }))
        releaseDate?.let { put(RELEASE_DATE, releaseDateValue(it)) }
    }

    companion object {
        private const val DENSE_DIM = 1024L // bge-m3
        private const val UPSERT_BATCH = 256 // 임베딩·upsert 묶음 — 묶음마다 저장돼 진행이 보존된다
        private const val SCROLL_LIMIT = 1000
        private const val INPUT_HASH = "input_hash"
        private const val MOOD_TAGS = "mood_tags"
        private const val RELEASE_DATE = "release_date"
        private const val SET_PAYLOAD_BATCH = 500

        /** Qdrant datetime payload는 RFC 3339 문자열이다 — 개봉일 자정(UTC). 예: 2026-10-21T00:00:00Z */
        private fun releaseDateValue(date: LocalDate): JsonWithInt.Value =
            ValueFactory.value(date.atStartOfDay(ZoneOffset.UTC).toInstant().toString())
    }
}
