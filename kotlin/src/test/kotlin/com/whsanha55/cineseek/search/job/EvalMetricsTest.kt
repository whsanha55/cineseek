package com.whsanha55.cineseek.search.job

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test
import kotlin.math.log2

class EvalMetricsTest {

    @Test
    fun `이상적인 순서면 nDCG는 1이다`() {
        // given
        val grades = mapOf(1L to 2, 2L to 1, 3L to 1)
        val ranked = listOf(1L, 2L, 3L, 4L, 5L)

        // when
        val ndcg = EvalMetrics.ndcg(ranked, grades, 5)

        // then
        assertThat(ndcg).isCloseTo(1.0, within(1e-9))
    }

    @Test
    fun `관련 항목이 하나도 없으면 nDCG는 0이다`() {
        // given
        val grades = mapOf(1L to 2, 2L to 1)
        val ranked = listOf(10L, 11L, 12L, 13L, 14L)

        // when
        val ndcg = EvalMetrics.ndcg(ranked, grades, 5)

        // then
        assertThat(ndcg).isEqualTo(0.0)
    }

    @Test
    fun `등급이 섞인 순서는 손계산 값과 같다`() {
        // given
        val grades = mapOf(1L to 2, 2L to 1)
        val ranked = listOf(2L, 9L, 1L)
        val dcg = 1.0 / 1.0 + 3.0 / 2.0
        val idcg = 3.0 / 1.0 + 1.0 / log2(3.0)

        // when
        val ndcg = EvalMetrics.ndcg(ranked, grades, 3)

        // then
        assertThat(ndcg).isCloseTo(dcg / idcg, within(1e-9))
        assertThat(ndcg).isCloseTo(0.6885288809, within(1e-9))
    }

    @Test
    fun `k 밖에 있는 정답은 무시한다`() {
        // given
        val grades = mapOf(1L to 2)
        val ranked = listOf(9L, 8L, 1L)

        // when
        val ndcg = EvalMetrics.ndcg(ranked, grades, 2)

        // then
        assertThat(ndcg).isEqualTo(0.0)
    }

    @Test
    fun `grades가 비어 있으면 nDCG는 0이다`() {
        // given
        val ranked = listOf(1L, 2L, 3L)

        // when
        val ndcg = EvalMetrics.ndcg(ranked, emptyMap(), 3)

        // then
        assertThat(ndcg).isEqualTo(0.0)
    }

    @Test
    fun `결과가 k보다 짧아도 계산한다`() {
        // given
        val grades = mapOf(1L to 1, 2L to 1)
        val ranked = listOf(1L)
        val idcg = 1.0 + 1.0 / log2(3.0)

        // when
        val ndcg = EvalMetrics.ndcg(ranked, grades, 5)

        // then
        assertThat(ndcg).isCloseTo(1.0 / idcg, within(1e-9))
    }

    @Test
    fun `첫 관련 항목이 2위면 RR은 0점5다`() {
        // given
        val grades = mapOf(1L to 1)
        val ranked = listOf(9L, 1L, 8L)

        // when
        val rr = EvalMetrics.reciprocalRank(ranked, grades, 3)

        // then
        assertThat(rr).isCloseTo(0.5, within(1e-9))
    }

    @Test
    fun `k 안에 관련 항목이 없으면 RR은 0이다`() {
        // given
        val grades = mapOf(1L to 2)
        val ranked = listOf(9L, 8L, 1L)

        // when
        val rr = EvalMetrics.reciprocalRank(ranked, grades, 2)

        // then
        assertThat(rr).isEqualTo(0.0)
    }
}
