package com.whsanha55.cineseek.search.job

import com.whsanha55.cineseek.search.enums.EvalQueryTypeEnum
import com.whsanha55.cineseek.search.vo.EvalLabel
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test

class EvalReportTest {

    @Test
    fun `전체와 유형별 평균을 낸다`() {
        // given
        val scores = listOf(
            score("q1", EvalQueryTypeEnum.PLOT, ndcg = 1.0, rr = 1.0),
            score("q2", EvalQueryTypeEnum.PLOT, ndcg = 0.5, rr = 0.5),
            score("q3", EvalQueryTypeEnum.MOOD, ndcg = 0.0, rr = 0.25),
        )

        // when
        val report = EvalReport(scores, baseline = null)

        // then
        assertThat(report.overall.ndcg).isCloseTo(0.5, within(1e-9))
        assertThat(report.overall.rr).isCloseTo(1.75 / 3, within(1e-9))
        assertThat(report.overall.count).isEqualTo(3)
        assertThat(report.byType).containsOnlyKeys(EvalQueryTypeEnum.PLOT, EvalQueryTypeEnum.MOOD)
        assertThat(report.byType[EvalQueryTypeEnum.PLOT]).isEqualTo(MeanScore(0.75, 0.75, 2))
        assertThat(report.byType[EvalQueryTypeEnum.MOOD]).isEqualTo(MeanScore(0.0, 0.25, 1))
    }

    @Test
    fun `점수가 없으면 평균은 0이다`() {
        // when
        val report = EvalReport(emptyList(), baseline = null)

        // then
        assertThat(report.overall).isEqualTo(MeanScore(0.0, 0.0, 0))
        assertThat(report.byType).isEmpty()
    }

    @Test
    fun `기준값이 없으면 비교 결과도 없다`() {
        // when
        val report = EvalReport(listOf(score("q1")), baseline = null)

        // then
        assertThat(report.comparison).isNull()
    }

    @Test
    fun `평균 nDCG가 0점03 떨어지면 회귀다`() {
        // given
        val baseline = baselineOf("q1" to BaselineScore(0.53, 0.5, HASH))

        // when
        val comparison = requireNotNull(EvalReport(listOf(score("q1", ndcg = 0.5, rr = 0.5)), baseline).comparison)

        // then
        assertThat(comparison.ndcgDelta).isCloseTo(-0.03, within(1e-9))
        assertThat(comparison.isRegression).isTrue()
    }

    @Test
    fun `평균 nDCG가 0점01 떨어지면 회귀가 아니다`() {
        // given
        val baseline = baselineOf("q1" to BaselineScore(0.51, 0.5, HASH))

        // when
        val comparison = requireNotNull(EvalReport(listOf(score("q1", ndcg = 0.5, rr = 0.5)), baseline).comparison)

        // then
        assertThat(comparison.ndcgDelta).isCloseTo(-0.01, within(1e-9))
        assertThat(comparison.isRegression).isFalse()
    }

    @Test
    fun `라벨 해시가 다른 쿼리는 labelChanged로 빠지고 평균에 들어가지 않는다`() {
        // given
        val scores = listOf(
            score("q1", ndcg = 1.0, rr = 1.0),
            score("q2", ndcg = 0.0, rr = 0.0),
        )
        val baseline = baselineOf(
            "q1" to BaselineScore(1.0, 1.0, HASH),
            "q2" to BaselineScore(1.0, 1.0, "otherhash"),
        )

        // when
        val comparison = requireNotNull(EvalReport(scores, baseline).comparison)

        // then
        assertThat(comparison.labelChanged).containsExactly("q2")
        assertThat(comparison.current).isEqualTo(MeanScore(1.0, 1.0, 1))
        assertThat(comparison.baseline).isEqualTo(MeanScore(1.0, 1.0, 1))
        assertThat(comparison.isRegression).isFalse()
    }

    @Test
    fun `기준값에 없는 쿼리는 newQueries로 빠진다`() {
        // given
        val scores = listOf(score("q1"), score("q2", ndcg = 0.0, rr = 0.0))
        val baseline = baselineOf("q1" to BaselineScore(0.5, 0.5, HASH))

        // when
        val comparison = requireNotNull(EvalReport(scores, baseline).comparison)

        // then
        assertThat(comparison.newQueries).containsExactly("q2")
        assertThat(comparison.current.count).isEqualTo(1)
    }

    @Test
    fun `drops에는 nDCG가 0점2 초과 하락한 쿼리만 많이 떨어진 순으로 담긴다`() {
        // given
        val scores = listOf(
            score("small", ndcg = 0.9, rr = 1.0),
            score("big", ndcg = 0.1, rr = 1.0),
            score("mid", ndcg = 0.6, rr = 1.0),
        )
        val baseline = baselineOf(
            "small" to BaselineScore(1.0, 1.0, HASH),
            "big" to BaselineScore(1.0, 1.0, HASH),
            "mid" to BaselineScore(0.9, 1.0, HASH),
        )

        // when
        val comparison = requireNotNull(EvalReport(scores, baseline).comparison)

        // then
        assertThat(comparison.drops.map { it.first }).containsExactly("big", "mid")
        assertThat(comparison.drops[0].second).isCloseTo(-0.9, within(1e-9))
        assertThat(comparison.drops[1].second).isCloseTo(-0.3, within(1e-9))
    }

    @Test
    fun `labelHash는 라벨 순서와 title에 무관하다`() {
        // given
        val labels = listOf(EvalLabel(1L, "A", 2), EvalLabel(2L, "B", 1))
        val reordered = listOf(EvalLabel(2L, "B 수정", 1), EvalLabel(1L, "A 수정", 2))

        // when
        val hash = EvalReport.labelHash(labels)

        // then
        assertThat(hash).hasSize(12).matches("[0-9a-f]+")
        assertThat(EvalReport.labelHash(reordered)).isEqualTo(hash)
    }

    @Test
    fun `labelHash는 grade가 바뀌면 달라진다`() {
        // given
        val labels = listOf(EvalLabel(1L, "A", 2), EvalLabel(2L, "B", 1))
        val regraded = listOf(EvalLabel(1L, "A", 1), EvalLabel(2L, "B", 1))

        // when
        val hash = EvalReport.labelHash(labels)

        // then
        assertThat(EvalReport.labelHash(regraded)).isNotEqualTo(hash)
    }

    @Test
    fun `toBaseline으로 만든 기준값과 비교하면 차이가 없다`() {
        // given
        val scores = listOf(
            score("q1", EvalQueryTypeEnum.PLOT, ndcg = 0.8, rr = 1.0),
            score("q2", EvalQueryTypeEnum.EN, ndcg = 0.3, rr = 0.5),
        )
        val baseline = EvalReport(scores, baseline = null).toBaseline()

        // when
        val comparison = requireNotNull(EvalReport(scores, baseline).comparison)

        // then
        assertThat(baseline.queries).containsEntry("q1", BaselineScore(0.8, 1.0, HASH))
        assertThat(comparison.current).isEqualTo(comparison.baseline)
        assertThat(comparison.labelChanged).isEmpty()
        assertThat(comparison.newQueries).isEmpty()
        assertThat(comparison.drops).isEmpty()
        assertThat(comparison.isRegression).isFalse()
    }

    private fun score(
        query: String,
        type: EvalQueryTypeEnum = EvalQueryTypeEnum.PLOT,
        ndcg: Double = 0.5,
        rr: Double = 0.5,
    ) = QueryScore(query, type, ndcg, rr, HASH)

    private fun baselineOf(vararg entries: Pair<String, BaselineScore>) = EvalBaseline(mapOf(*entries))

    companion object {
        private const val HASH = "abc123def456"
    }
}
