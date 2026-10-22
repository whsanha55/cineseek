package com.whsanha55.cineseek.search.service

import com.google.protobuf.Timestamp
import com.whsanha55.cineseek.external.embedding.client.EmbeddingClient
import com.whsanha55.cineseek.external.qdrant.config.QdrantProperties
import com.whsanha55.cineseek.global.exception.ExternalApiException
import com.whsanha55.cineseek.search.vo.Embedding
import com.whsanha55.cineseek.search.vo.MatchedTerms
import com.whsanha55.cineseek.search.vo.SearchFilter
import com.whsanha55.cineseek.search.vo.SearchHit
import com.whsanha55.cineseek.search.vo.SearchPage
import io.qdrant.client.ConditionFactory
import io.qdrant.client.ExpressionFactory
import io.qdrant.client.PointIdFactory
import io.qdrant.client.QdrantClient
import io.qdrant.client.QueryFactory
import io.qdrant.client.ValueFactory
import io.qdrant.client.VectorInputFactory
import io.qdrant.client.WithPayloadSelectorFactory
import io.qdrant.client.WithVectorsSelectorFactory
import io.qdrant.client.grpc.Common
import io.qdrant.client.grpc.Points
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future

/**
 * 하이브리드(dense+sparse+분위기 태그 RRF → 인기도 재정렬) 검색 + vector-to-vector 유사 조회 — search.py 이식.
 * 결과는 (movie_id, score)만 돌려주고 표시 필드는 PG 조립에 맡긴다 (PG=SoT, Qdrant=파생 인덱스)
 */
@Service
class SearchService(
    private val qdrantClient: QdrantClient,
    private val embeddingClient: EmbeddingClient,
    private val qdrantProperties: QdrantProperties,
    private val moodTagMatcher: MoodTagMatcher,
    private val clock: Clock,
) {

    fun search(query: String, filter: SearchFilter): SearchPage =
        search(embeddingClient.embed(listOf(query)).single(), filter, query)

    /**
     * 쿼리 묶음을 한 번의 임베딩 배치로 처리하는 진입점 (EvalRunner용 — python eval.py와 같은 배치).
     * query가 있으면 결과의 저장 sparse를 함께 받아 매칭 단어를 계산한다 (재색인 없이 저장 벡터만 사용)
     *
     * dense·sparse 후보를 RRF(k=[RRF_K])로 합친 상위 [CANDIDATE_LIMIT]개를 인기도로 재정렬한다 (#78).
     * 후보 수를 dense·sparse·RRF 단계 모두 같게 두어 offset+limit이 후보 수 안이면 페이지마다 같은 후보 집합을
     * 재정렬하므로 페이지 사이에 중복·누락이 없다. 더 깊은 페이지는 offset+limit만큼 후보를 늘린다.
     *
     * RRF에는 세 번째 후보 목록으로 쿼리와 가까운 분위기 태그(MoodTagMatcher)를 하나라도 가진 영화의 dense 순위를 넣는다 (#40).
     * 분위기 쿼리에서 줄거리 단어보다 태그가 맞는 영화를 끌어올리기 위해서다. 같은 payload 필터를 걸고, 태그를 못 고르면 빠진다
     */
    fun search(embedding: Embedding, filter: SearchFilter, query: String? = null): SearchPage {
        val fetch = filter.limit + 1 // hasNext 판단용 1건 초과 조회
        val payloadFilter = buildFilter(filter)
        val candidateLimit = maxOf(CANDIDATE_LIMIT, (filter.offset + fetch).toLong())
        val dense = QueryFactory.nearest(VectorInputFactory.vectorInput(embedding.dense))
        val fused = Points.PrefetchQuery.newBuilder()
            .addPrefetch(
                prefetch(dense, "dense", payloadFilter, candidateLimit),
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
                    candidateLimit,
                ),
            )
            .apply {
                val moodTags = moodTagMatcher.topTags(embedding.dense)
                if (moodTags.isNotEmpty()) {
                    val moodFilter = payloadFilter.toBuilder()
                        .addMust(ConditionFactory.matchKeywords(MOOD_TAGS, moodTags))
                        .build()
                    addPrefetch(prefetch(dense, "dense", moodFilter, candidateLimit))
                }
            }
            .setQuery(QueryFactory.rrf(Points.Rrf.newBuilder().setK(RRF_K).build()))
            .setLimit(candidateLimit)
            .build()
        val request = Points.QueryPoints.newBuilder()
            .setCollectionName(qdrantProperties.collection)
            .addPrefetch(fused)
            .setQuery(QueryFactory.formula(POPULARITY_BOOST))
            .setOffset(filter.offset.toLong())
            .setLimit(fetch.toLong())
            .setWithPayload(WithPayloadSelectorFactory.enable(false))
            .setWithVectors(
                if (query == null) {
                    WithVectorsSelectorFactory.enable(false)
                } else {
                    WithVectorsSelectorFactory.include(listOf("sparse"))
                },
            )
            .build()

        val scored = qdrant(qdrantClient.queryAsync(request))
        return SearchPage(
            hits = scored.take(filter.limit).map { point ->
                val terms = query?.let { MatchedTerms.extract(it, embedding.sparse, storedSparse(point.vectors)) }
                SearchHit(point.id.num, point.score, terms.orEmpty())
            },
            limit = filter.limit,
            offset = filter.offset,
            hasNext = scored.size > filter.limit,
        )
    }

    /** 유사 영화 — 저장 dense 벡터로 vector-to-vector 검색 (코사인). 자기 자신과 투표 수 하한([voteCountFloor]) 미만은 제외 */
    fun similar(movieId: Long, limit: Int): List<SearchHit> {
        val dense = storedDense(movieId) ?: return emptyList() // 색인 없는 영화 → 유사 목록 없음

        val request = Points.QueryPoints.newBuilder()
            .setCollectionName(qdrantProperties.collection)
            .setQuery(QueryFactory.nearest(VectorInputFactory.vectorInput(dense.toList())))
            .setUsing("dense")
            .setFilter(
                Common.Filter.newBuilder()
                    .addMust(voteCountFloor(Instant.now(clock)))
                    .addMustNot(ConditionFactory.hasId(listOf(PointIdFactory.id(movieId))))
                    .build(),
            )
            .setLimit(limit.toLong())
            .setWithPayload(WithPayloadSelectorFactory.enable(false))
            .build()

        return qdrant(qdrantClient.queryAsync(request)).map { SearchHit(it.id.num, it.score) }
    }

    /**
     * 취향 추천 — Qdrant recommend(기본 average_vector)에 찜을 positive, 관심없음을 negative 포인트 id로 넘긴다.
     * 예시로 쓴 영화와 봤어요 영화, 투표 수 하한([voteCountFloor]) 미만 영화는 결과에서 뺀다. 없는 포인트 id를 넘기면 Qdrant가 요청 전체를 거절하므로
     * 색인된 id만 남기고, 남은 찜이 없으면 빈 목록이다
     */
    fun recommend(likedIds: List<Long>, dislikedIds: List<Long>, watchedIds: List<Long>, limit: Int): List<SearchHit> {
        val indexed = indexedIds(likedIds + dislikedIds)
        val positives = likedIds.filter { it in indexed }.distinct()
        if (positives.isEmpty()) {
            return emptyList()
        }
        val negatives = dislikedIds.filter { it in indexed }.distinct()
        val excluded = (likedIds + dislikedIds + watchedIds).distinct()

        val request = Points.QueryPoints.newBuilder()
            .setCollectionName(qdrantProperties.collection)
            .setQuery(
                QueryFactory.recommend(
                    Points.RecommendInput.newBuilder()
                        .addAllPositive(positives.map { VectorInputFactory.vectorInput(it) })
                        .addAllNegative(negatives.map { VectorInputFactory.vectorInput(it) })
                        .build(),
                ),
            )
            .setUsing("dense")
            .setFilter(
                Common.Filter.newBuilder()
                    .addMust(voteCountFloor(Instant.now(clock)))
                    .addMustNot(ConditionFactory.hasId(excluded.map { PointIdFactory.id(it) }))
                    .build(),
            )
            .setLimit(limit.toLong())
            .setWithPayload(WithPayloadSelectorFactory.enable(false))
            .build()

        return qdrant(qdrantClient.queryAsync(request)).map { SearchHit(it.id.num, it.score) }
    }

    private fun indexedIds(movieIds: List<Long>): Set<Long> = qdrant(
        qdrantClient.retrieveAsync(
            qdrantProperties.collection,
            movieIds.distinct().map { PointIdFactory.id(it) },
            WithPayloadSelectorFactory.enable(false),
            WithVectorsSelectorFactory.enable(false),
            null,
        ),
    ).map { it.id.num }.toSet()

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

    /** VectorOutput oneof — 신규는 sparse(SparseVector), 레거시는 indices + data */
    private fun storedSparse(vectors: Points.VectorsOutput): Map<Long, Float> {
        val output = vectors.vectors.vectorsMap["sparse"]
        val pairs = when {
            output == null -> emptyList()
            output.hasSparse() -> output.sparse.indicesList.zip(output.sparse.valuesList)
            output.hasIndices() -> output.indices.dataList.zip(output.dataList)
            else -> emptyList()
        }
        return pairs.associate { (index, value) -> index.toLong() to value }
    }

    private fun <T> qdrant(future: Future<T>): T = try {
        future.get()
    } catch (e: ExecutionException) {
        throw ExternalApiException(QDRANT, e)
    }

    private fun prefetch(
        query: Points.Query,
        using: String,
        filter: Common.Filter,
        limit: Long,
    ): Points.PrefetchQuery = Points.PrefetchQuery.newBuilder()
        .setQuery(query)
        .setUsing(using)
        .setFilter(filter)
        .setLimit(limit)
        .build()

    /**
     * payload 필터 — 장르는 OR(should), 제외 장르는 must_not, 나머지는 AND(must). 이름이 아닌 ID 기준.
     * voteCountMin·voteCountMax를 둘 다 생략하면 [DEFAULT_VOTE_COUNT_MIN] 하한을 건다 (#78, 최근 개봉 장편 예외는 [voteCountFloor]) —
     * 줄거리가 짧은 무명작은 제목 단어만으로 상위에 올라오기 때문이다. 하나라도 명시하면 명시값만 쓴다
     * ("숨은 명작" 50~500, 무명작 탐색 등)
     */
    private fun buildFilter(filter: SearchFilter): Common.Filter {
        val must = buildList {
            filter.yearMin?.let { add(range("release_year", Common.Range.newBuilder().setGte(it.toDouble()))) }
            filter.yearMax?.let { add(range("release_year", Common.Range.newBuilder().setLte(it.toDouble()))) }
            filter.ratingMin?.let { add(range("rating", Common.Range.newBuilder().setGte(it))) }
            filter.runtimeMin?.let { add(range("runtime", Common.Range.newBuilder().setGte(it.toDouble()))) }
            filter.runtimeMax?.let { add(range("runtime", Common.Range.newBuilder().setLte(it.toDouble()))) }
            filter.voteCountMin?.let { add(range("vote_count", Common.Range.newBuilder().setGte(it.toDouble()))) }
            filter.voteCountMax?.let { add(range("vote_count", Common.Range.newBuilder().setLte(it.toDouble()))) }
            if (filter.voteCountMin == null && filter.voteCountMax == null) {
                add(voteCountFloor(Instant.now(clock)))
            }
            filter.directorId?.let { add(ConditionFactory.match("director_ids", it)) }
            filter.castId?.let { add(ConditionFactory.match("cast_ids", it)) }
        }
        val should = filter.genreIds.map { ConditionFactory.match("genre_ids", it) }
        val mustNot = filter.excludeGenreIds.map { ConditionFactory.match("genre_ids", it) }
        return Common.Filter.newBuilder().addAllMust(must).addAllShould(should).addAllMustNot(mustNot).build()
    }

    private fun range(key: String, range: Common.Range.Builder) = ConditionFactory.range(key, range.build())

    companion object {
        private const val QDRANT = "qdrant"
        private const val MOOD_TAGS = "mood_tags"
        private const val CANDIDATE_LIMIT = 50L // 인기도 재정렬 후보 수 (dense·sparse·RRF 각 단계) — 페이지가 깊어지면 offset+limit으로 확장
        private const val DEFAULT_VOTE_COUNT_MIN = 10 // 검색 기본·유사·추천의 투표 수 하한 (#78)
        private const val RECENT_RELEASE_DAYS = 365L // 이 기간 안에 개봉했거나 개봉 예정인 장편은 하한에서 뺀다 (#85)
        private const val FEATURE_RUNTIME_MIN = 60 // 하한 예외를 받는 장편의 최소 러닝타임(분) — 단편 제외 (#85)
        private const val RELEASE_DATE = "release_date"
        private const val POPULARITY_WEIGHT = 0.05f // log10(1 + vote_count) 가중치 — RRF_K와 함께 eval로 정했다 (#78, #40)
        private const val RRF_K = 10 // RRF 순위 상수 1/(k + rank). Qdrant 기본값 2보다 완만하게 해 한 목록의 1위가 독주하지 않게 한다 (#40)

        /** `$score + w * log10(1 + vote_count)` — 투표 수가 많을수록 조금씩 올린다. vote_count가 없으면 0으로 본다 */
        private val POPULARITY_BOOST: Points.Formula = Points.Formula.newBuilder()
            .setExpression(
                ExpressionFactory.sum(
                    Points.SumExpression.newBuilder()
                        .addSum(ExpressionFactory.variable("\$score"))
                        .addSum(
                            ExpressionFactory.mult(
                                Points.MultExpression.newBuilder()
                                    .addMult(ExpressionFactory.constant(POPULARITY_WEIGHT))
                                    .addMult(
                                        ExpressionFactory.log10(
                                            ExpressionFactory.sum(
                                                Points.SumExpression.newBuilder()
                                                    .addSum(ExpressionFactory.constant(1f))
                                                    .addSum(ExpressionFactory.variable("vote_count"))
                                                    .build(),
                                            ),
                                        ),
                                    )
                                    .build(),
                            ),
                        )
                        .build(),
                ),
            )
            .putDefaults("vote_count", ValueFactory.value(0L))
            .build()

        /**
         * 투표 수 하한 — vote_count가 [DEFAULT_VOTE_COUNT_MIN] 이상이거나, [now] 기준 최근 [RECENT_RELEASE_DAYS]일 안에 개봉했거나
         * 개봉 예정인 [FEATURE_RUNTIME_MIN]분 이상 장편이면 통과한다 (#85). 한국 신작은 TMDB 투표가 적어 하한에 모두 걸리기 때문이다.
         * 러닝타임 조건은 투표가 적은 단편을 계속 걸러낸다.
         * vote_count·release_date·runtime payload가 없는 포인트는 해당 조건을 만족하지 못한다
         */
        private fun voteCountFloor(now: Instant): Common.Condition {
            val recentFrom = now.minus(Duration.ofDays(RECENT_RELEASE_DAYS))
            val recentFeature = Common.Filter.newBuilder()
                .addMust(
                    ConditionFactory.datetimeRange(
                        RELEASE_DATE,
                        Common.DatetimeRange.newBuilder()
                            .setGte(Timestamp.newBuilder().setSeconds(recentFrom.epochSecond).setNanos(recentFrom.nano))
                            .build(),
                    ),
                )
                .addMust(
                    ConditionFactory.range(
                        "runtime",
                        Common.Range.newBuilder().setGte(FEATURE_RUNTIME_MIN.toDouble()).build(),
                    ),
                )
                .build()
            val voteCountMin = ConditionFactory.range(
                "vote_count",
                Common.Range.newBuilder().setGte(DEFAULT_VOTE_COUNT_MIN.toDouble()).build(),
            )
            return ConditionFactory.filter(
                Common.Filter.newBuilder()
                    .addShould(voteCountMin)
                    .addShould(ConditionFactory.filter(recentFeature))
                    .build(),
            )
        }
    }
}
