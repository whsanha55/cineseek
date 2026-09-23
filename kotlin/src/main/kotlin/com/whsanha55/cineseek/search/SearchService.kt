package com.whsanha55.cineseek.search

import com.whsanha55.cineseek.config.QdrantProperties
import com.whsanha55.cineseek.embedding.Embedding
import com.whsanha55.cineseek.embedding.EmbeddingClient
import io.qdrant.client.ConditionFactory
import io.qdrant.client.QdrantClient
import io.qdrant.client.QueryFactory
import io.qdrant.client.VectorInputFactory
import io.qdrant.client.WithPayloadSelectorFactory
import io.qdrant.client.grpc.Points
import org.springframework.stereotype.Service

/** 검색 결과 — Qdrant payload 사본 기반 */
data class SearchResult(
	val title: String?,
	val releaseYear: Long?,
	val rating: Double?,
	val score: Float,
	val genres: List<String> = emptyList(),
	val directors: List<String> = emptyList(),
)

/**
 * 하이브리드(dense+sparse RRF) 검색 — search.py 이식.
 * 검색은 Qdrant payload만 쓰고 JPA를 타지 않는다 (PG=SoT, Qdrant=파생 인덱스)
 */
@Service
class SearchService(
	private val qdrantClient: QdrantClient,
	private val embeddingClient: EmbeddingClient,
	private val qdrantProperties: QdrantProperties,
) {

	fun search(query: String, genre: String?, yearMin: Int?, limit: Int): List<SearchResult> =
		search(embeddingClient.embed(listOf(query)).single(), genre, yearMin, limit)

	/** 쿼리 묶음을 한 번의 임베딩 배치로 처리하는 진입점 (EvalRunner용 — python eval.py와 같은 배치) */
	fun search(embedding: Embedding, genre: String?, yearMin: Int?, limit: Int): List<SearchResult> {
		val filter = buildFilter(genre, yearMin)
		val request = Points.QueryPoints.newBuilder()
			.setCollectionName(qdrantProperties.collection)
			.addPrefetch(
				prefetch(
					QueryFactory.nearest(VectorInputFactory.vectorInput(embedding.dense)),
					"dense",
					filter,
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
					filter,
				),
			)
			.setQuery(QueryFactory.fusion(Points.Fusion.RRF))
			.setLimit(limit.toLong())
			.setWithPayload(WithPayloadSelectorFactory.enable(true))
			.build()

		return qdrantClient.queryAsync(request).get().map { it.toSearchResult() }
	}

	private fun prefetch(query: Points.Query, using: String, filter: Points.Filter?): Points.PrefetchQuery {
		val builder = Points.PrefetchQuery.newBuilder()
			.setQuery(query)
			.setUsing(using)
			.setLimit(PREFETCH_LIMIT)
		filter?.let(builder::setFilter)
		return builder.build()
	}

	/** payload 필터 — python build_filter 이식 (genres match + release_year >=) */
	private fun buildFilter(genre: String?, yearMin: Int?): Points.Filter? {
		val must = buildList {
			genre?.let { add(ConditionFactory.matchKeyword("genres", it)) }
			yearMin?.let {
				add(ConditionFactory.range("release_year", Points.Range.newBuilder().setGte(it.toDouble()).build()))
			}
		}
		return if (must.isEmpty()) {
			null
		} else {
			Points.Filter.newBuilder().addAllMust(must).build()
		}
	}

	private fun Points.ScoredPoint.toSearchResult() = SearchResult(
		title = payloadMap["title"]?.stringValue,
		releaseYear = payloadMap["release_year"]?.integerValue,
		rating = payloadMap["rating"]?.doubleValue,
		score = score,
		genres = payloadMap["genres"]?.listValue?.valuesList?.map { it.stringValue }.orEmpty(),
		directors = payloadMap["directors"]?.listValue?.valuesList?.map { it.stringValue }.orEmpty(),
	)

	companion object {
		private const val PREFETCH_LIMIT = 20L // dense/sparse 각각 상위 20개 후보 (search.py와 동일)
	}
}
