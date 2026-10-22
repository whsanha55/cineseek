package com.whsanha55.cineseek.search.service

import com.whsanha55.cineseek.external.embedding.client.EmbeddingClient
import com.whsanha55.cineseek.movie.vo.MoodVocabulary
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import kotlin.math.sqrt

private val log = KotlinLogging.logger {}

/**
 * 쿼리 → 분위기 태그 매칭 (#40) — 후보 어휘 단어 자체를 임베딩해 두고, 쿼리 dense와 코사인이 가장 큰 태그를 고른다.
 * 검색 경로에 LLM을 두지 않기 위한 방식이다. 태그 벡터는 첫 검색 때 한 번 임베딩해 캐시한다.
 * 임베딩에 실패하면 경고만 남기고 빈 목록을 돌려준다 — 검색은 태그 없이 진행되고 다음 검색에서 다시 시도한다
 */
@Component
class MoodTagMatcher(private val embeddingClient: EmbeddingClient) {

    @Volatile
    private var tagVectors: List<List<Float>>? = null

    /** 쿼리 dense와 가장 가까운 태그 [TOP_TAGS]개. 문턱값은 두지 않는다 (eval로 정했다) */
    fun topTags(dense: List<Float>): List<String> {
        val vectors = tagVectors ?: load() ?: return emptyList()
        return TAGS.indices
            .sortedByDescending { cosine(dense, vectors[it]) }
            .take(TOP_TAGS)
            .map { TAGS[it] }
    }

    @Synchronized
    private fun load(): List<List<Float>>? = tagVectors ?: runCatching {
        embeddingClient.embed(TAGS).map { it.dense }
            .also { check(it.size == TAGS.size) { "태그 임베딩 수가 다르다. 요청=${TAGS.size}, 응답=${it.size}" } }
    }.onSuccess { tagVectors = it }
        .onFailure { log.warn(it) { "분위기 태그 임베딩 실패 — 이번 검색은 태그 후보 없이 진행하고 다음 검색에서 다시 시도한다" } }
        .getOrNull()

    private fun cosine(a: List<Float>, b: List<Float>): Double {
        var dot = 0.0
        var normA = 0.0
        var normB = 0.0
        a.indices.forEach { i ->
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        return dot / (sqrt(normA) * sqrt(normB))
    }

    companion object {
        private val TAGS = MoodVocabulary.CANDIDATE_TAGS
        private const val TOP_TAGS = 2
    }
}
