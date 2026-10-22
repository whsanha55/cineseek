package com.whsanha55.cineseek.search.service

import com.whsanha55.cineseek.external.embedding.client.EmbeddingClient
import com.whsanha55.cineseek.global.exception.ExternalApiException
import com.whsanha55.cineseek.movie.vo.MoodVocabulary
import com.whsanha55.cineseek.search.vo.Embedding
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** MoodTagMatcher 단위 테스트 — 태그 벡터는 가짜 3차원, EmbeddingClient만 MockK */
class MoodTagMatcherTest {

    private val embeddingClient = mockk<EmbeddingClient>()
    private val matcher = MoodTagMatcher(embeddingClient)

    @Test
    fun `쿼리 dense와 코사인이 큰 순서로 태그 2개를 고른다`() {
        // given — 어두운이 가장 가깝고, 그다음은 크기만 다른 누아르(코사인은 크기와 무관)
        every { embeddingClient.embed(TAGS) } returns tagEmbeddings(
            mapOf("어두운" to listOf(1f, 0.1f, 0f), "누아르" to listOf(5f, 2f, 0f), "잔잔한" to listOf(0f, 1f, 0f)),
        )

        // when
        val tags = matcher.topTags(listOf(1f, 0.1f, 0f))

        // then
        assertThat(tags).containsExactly("어두운", "누아르")
    }

    @Test
    fun `태그 벡터는 한 번만 임베딩해 다시 쓴다`() {
        // given
        every { embeddingClient.embed(TAGS) } returns tagEmbeddings(mapOf("힐링" to listOf(1f, 0f, 0f)))

        // when
        matcher.topTags(listOf(1f, 0f, 0f))
        val tags = matcher.topTags(listOf(1f, 0f, 0f))

        // then
        assertThat(tags.first()).isEqualTo("힐링")
        verify(exactly = 1) { embeddingClient.embed(TAGS) }
    }

    @Test
    fun `임베딩이 실패하면 빈 목록을 돌려주고 다음 호출에서 다시 시도한다`() {
        // given
        every { embeddingClient.embed(TAGS) } throws ExternalApiException("embedding") andThen
            tagEmbeddings(mapOf("슬픈" to listOf(1f, 0f, 0f)))

        // when
        val failed = matcher.topTags(listOf(1f, 0f, 0f))
        val retried = matcher.topTags(listOf(1f, 0f, 0f))

        // then
        assertThat(failed).isEmpty()
        assertThat(retried.first()).isEqualTo("슬픈")
    }

    @Test
    fun `태그 수와 다른 수의 임베딩이 오면 실패로 보고 빈 목록을 돌려준다`() {
        // given
        every { embeddingClient.embed(TAGS) } returns listOf(embedding(listOf(1f, 0f, 0f)))

        // when
        val tags = matcher.topTags(listOf(1f, 0f, 0f))

        // then
        assertThat(tags).isEmpty()
    }

    /** 지정하지 않은 태그는 쿼리와 반대 방향 [-1,0,0] */
    private fun tagEmbeddings(vectors: Map<String, List<Float>>): List<Embedding> =
        TAGS.map { embedding(vectors[it] ?: listOf(-1f, 0f, 0f)) }

    private fun embedding(dense: List<Float>) = Embedding(dense, Embedding.Sparse(emptyList(), emptyList()))

    companion object {
        private val TAGS = MoodVocabulary.CANDIDATE_TAGS
    }
}
