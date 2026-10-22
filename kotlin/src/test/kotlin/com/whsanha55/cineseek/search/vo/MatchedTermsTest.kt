package com.whsanha55.cineseek.search.vo

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** 토큰 id·가중치는 bge-m3 실제 출력("감옥에서 탈출하는 이야기", "a prison escape story")에서 가져왔다 */
class MatchedTermsTest {

    private val korean = Embedding.Sparse(
        indices = listOf(26187L, 54583L, 1180L, 76230L, 14842L, 2123L, 31198L),
        values = listOf(0.116f, 0.242f, 0.108f, 0.202f, 0.222f, 0.086f, 0.19f),
        tokens = listOf("▁감", "옥", "에서", "▁탈", "출", "하는", "▁이야기"),
    )

    @Test
    fun `음절 조각 토큰을 쿼리 원문 단어로 이어 붙이고 기여도 순으로 돌려준다`() {
        // given — 조사 '에서', 범용어 '이야기'도 문서에 있다
        val doc =
            mapOf(26187L to 0.02f, 54583L to 0.2f, 76230L to 0.21f, 14842L to 0.26f, 1180L to 0.3f, 31198L to 0.3f)

        // when
        val terms = MatchedTerms.extract("감옥에서 탈출하는 이야기", korean, doc)

        // then
        assertThat(terms).containsExactly("탈출", "감옥")
    }

    @Test
    fun `단어의 일부인 한 글자 조각만 겹치면 근거로 쓰지 않는다`() {
        // given — 문서에 '옥'만 있다 (예: 탈옥)
        val doc = mapOf(54583L to 0.2f)

        // when
        val terms = MatchedTerms.extract("감옥에서 탈출하는 이야기", korean, doc)

        // then
        assertThat(terms).isEmpty()
    }

    @Test
    fun `한 글자여도 그 자체가 한 단어면 근거로 쓴다`() {
        // given
        val sparse = Embedding.Sparse(listOf(1L, 2L), listOf(0.29f, 0.25f), listOf("▁비", "▁오는"))

        // when
        val terms = MatchedTerms.extract("비 오는 날", sparse, mapOf(1L to 0.2f))

        // then
        assertThat(terms).containsExactly("비")
    }

    @Test
    fun `영어 불용어는 빼고 최대 3개까지 돌려준다`() {
        // given
        val sparse = Embedding.Sparse(
            indices = listOf(10L, 101085L, 144281L, 13765L, 5L),
            values = listOf(0.144f, 0.266f, 0.297f, 0.218f, 0.1f),
            tokens = listOf("▁a", "▁prison", "▁escape", "▁story", "▁break"),
        )
        val doc = mapOf(10L to 0.3f, 101085L to 0.2f, 144281L to 0.3f, 13765L to 0.3f, 5L to 0.1f, 99L to 0.5f)

        // when
        val terms = MatchedTerms.extract("a prison escape story break", sparse, doc)

        // then
        assertThat(terms).containsExactly("escape", "prison", "break")
    }

    @Test
    fun `공통 토큰이 없거나 토큰 문자열이 없으면 빈 목록`() {
        // when
        val noOverlap = MatchedTerms.extract("감옥에서 탈출하는 이야기", korean, mapOf(99L to 0.5f))
        val noTokens = MatchedTerms.extract(
            "감옥에서 탈출하는 이야기",
            korean.copy(tokens = null),
            mapOf(54583L to 0.2f, 76230L to 0.21f),
        )

        // then
        assertThat(noOverlap).isEmpty()
        assertThat(noTokens).isEmpty()
    }
}
