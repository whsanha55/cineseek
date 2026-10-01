package com.whsanha55.cineseek.search.service

import com.whsanha55.cineseek.external.embedding.client.EmbeddingClient
import com.whsanha55.cineseek.external.qdrant.config.QdrantProperties
import com.whsanha55.cineseek.global.exception.ExternalApiException
import com.whsanha55.cineseek.search.vo.Embedding
import com.whsanha55.cineseek.search.vo.SearchFilter
import com.whsanha55.cineseek.search.vo.SearchHit
import com.whsanha55.cineseek.search.vo.SearchPage
import io.qdrant.client.ConditionFactory
import io.qdrant.client.PointIdFactory
import io.qdrant.client.QdrantClient
import io.qdrant.client.QueryFactory
import io.qdrant.client.VectorInputFactory
import io.qdrant.client.WithPayloadSelectorFactory
import io.qdrant.client.WithVectorsSelectorFactory
import io.qdrant.client.grpc.Points
import org.springframework.stereotype.Service
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future

/**
 * 하이브리드(dense+sparse RRF) 검색 + vector-to-vector 유사 조회 — search.py 이식.
 * 결과는 (movie_id, score)만 돌려주고 표시 필드는 PG 조립에 맡긴다 (PG=SoT, Qdrant=파생 인덱스)
 */
@Service
class SearchService(
    private val qdrantClient: QdrantClient,
    private val embeddingClient: EmbeddingClient,
    private val qdrantProperties: QdrantProperties,
) {

    fun search(query: String, filter: SearchFilter): SearchPage =
        search(embeddingClient.embed(listOf(query)).single(), filter)

    /** 쿼리 묶음을 한 번의 임베딩 배치로 처리하는 진입점 (EvalRunner용 — python eval.py와 같은 배치) */
    fun search(embedding: Embedding, filter: SearchFilter): SearchPage {
        val fetch = filter.limit + 1 // hasNext 판단용 1건 초과 조회
        val payloadFilter = buildFilter(filter)
        val prefetchLimit = maxOf(PREFETCH_LIMIT, (filter.offset + fetch).toLong())
        val request = Points.QueryPoints.newBuilder()
            .setCollectionName(qdrantProperties.collection)
            .addPrefetch(
                prefetch(
                    QueryFactory.nearest(VectorInputFactory.vectorInput(embedding.dense)),
                    "dense",
                    payloadFilter,
                    prefetchLimit,
                ),
            )
            .addPrefetch(
                prefetch(
                    QueryFactory.nearest(
                        VectorInputFactory.vectorInput(
                            embedding.sparse.values,
                            embedding.sparse.indices.map(Long::toInt),
                        ),
                    ),
                    "sparse",
                    payloadFilter,
                    prefetchLimit,
                ),
            )
            .setQuery(QueryFactory.fusion(Points.Fusion.RRF))
            .setOffset(filter.offset.toLong())
            .setLimit(fetch.toLong())
            .setWithPayload(WithPayloadSelectorFactory.enable(false))
            .build()

        val scored = qdrant(qdrantClient.queryAsync(request))
        return SearchPage(
            hits = scored.take(filter.limit).map { SearchHit(it.id.num, it.score) },
            limit = filter.limit,
            offset = filter.offset,
            hasNext = scored.size > filter.limit,
        )
    }

    /** 유사 영화 — 해당 영화의 저장 dense 벡터로 vector-to-vector 검색 (코사인). 자기 자신은 제외 */
    fun similar(movieId: Long, limit: Int): List<SearchHit> {
        val dense = storedDense(movieId) ?: return emptyList() // 색인 없는 영화 → 유사 목록 없음

        val request = Points.QueryPoints.newBuilder()
            .setCollectionName(qdrantProperties.collection)
            .setQuery(QueryFactory.nearest(VectorInputFactory.vectorInput(dense.toList())))
            .setUsing("dense")
            .setFilter(
                Points.Filter.newBuilder()
                    .addMustNot(ConditionFactory.hasId(listOf(PointIdFactory.id(movieId))))
                    .build(),
            )
            .setLimit(limit.toLong())
            .setWithPayload(WithPayloadSelectorFactory.enable(false))
            .build()

        return qdrant(qdrantClient.queryAsync(request)).map { SearchHit(it.id.num, it.score) }
    }

    /** VectorOutput oneof — 신규는 dense(DenseVector), 레거시는 data(repeated float) */
    private fun storedDense(movieId: Long): List<Float>? {
        val point = qdrant(
            qdrantClient.retrieveAsync(
                qdrantProperties.collection,
                listOf(PointIdFactory.id(movieId)),
                WithPayloadSelectorFactory.enable(false),
                WithVectorsSelectorFactory.enable(true),
                null,
            ),
        ).firstOrNull()
        val output = point?.vectors?.vectors?.vectorsMap?.get("dense")
        return when {
            output == null -> null
            output.hasDense() -> output.dense.dataList
            output.dataCount > 0 -> output.dataList
            else -> null
        }
    }

    private fun <T> qdrant(future: Future<T>): T = try {
        future.get()
    } catch (e: ExecutionException) {
        throw ExternalApiException(QDRANT, e)
    }

    private fun prefetch(
        query: Points.Query,
        using: String,
        filter: Points.Filter?,
        limit: Long,
    ): Points.PrefetchQuery {
        val builder = Points.PrefetchQuery.newBuilder()
            .setQuery(query)
            .setUsing(using)
            .setLimit(limit)
        filter?.let(builder::setFilter)
        return builder.build()
    }

    /** payload 필터 — 장르는 OR(should), 나머지는 AND(must). 이름이 아닌 ID 기준 */
    private fun buildFilter(filter: SearchFilter): Points.Filter? {
        val must = buildList {
            filter.yearMin?.let { add(range("release_year", Points.Range.newBuilder().setGte(it.toDouble()))) }
            filter.yearMax?.let { add(range("release_year", Points.Range.newBuilder().setLte(it.toDouble()))) }
            filter.ratingMin?.let { add(range("rating", Points.Range.newBuilder().setGte(it))) }
            filter.directorId?.let { add(ConditionFactory.match("director_ids", it)) }
            filter.castId?.let { add(ConditionFactory.match("cast_ids", it)) }
        }
        val should = filter.genreIds.map { ConditionFactory.match("genre_ids", it) }
        if (must.isEmpty() && should.isEmpty()) {
            return null
        }
        return Points.Filter.newBuilder().addAllMust(must).addAllShould(should).build()
    }

    private fun range(key: String, range: Points.Range.Builder) = ConditionFactory.range(key, range.build())

    companion object {
        private const val QDRANT = "qdrant"
        private const val PREFETCH_LIMIT = 20L // dense/sparse 각 최소 후보 (search.py와 동일) — 페이지가 깊어지면 offset+limit으로 확장
    }
}
