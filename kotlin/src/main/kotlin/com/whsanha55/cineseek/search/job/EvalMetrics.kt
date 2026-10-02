package com.whsanha55.cineseek.search.job

import kotlin.math.log2

/**
 * 검색 품질 지표(nDCG@k, RR@k) 순수 함수.
 * grades는 관련 tmdbId → 등급(1 또는 2). 없는 id는 등급 0(무관)으로 본다.
 */
object EvalMetrics {
    /** 1위의 할인 분모가 log2(2) = 1이 되도록 0-기반 순위에 더하는 값 */
    private const val RANK_DISCOUNT_OFFSET = 2.0

    /** nDCG@k. 정답이 없거나 IDCG가 0이면 0.0 */
    fun ndcg(ranked: List<Long>, grades: Map<Long, Int>, k: Int): Double {
        val idcg = dcg(grades.values.sortedDescending().take(k))
        if (idcg == 0.0) return 0.0
        return dcg(ranked.take(k).map { grades[it] ?: 0 }) / idcg
    }

    /** RR@k. 상위 k 안에서 첫 관련 항목(등급 1 이상) 순위의 역수, 없으면 0.0 */
    fun reciprocalRank(ranked: List<Long>, grades: Map<Long, Int>, k: Int): Double {
        val index = ranked.take(k).indexOfFirst { (grades[it] ?: 0) >= 1 }
        return if (index < 0) 0.0 else 1.0 / (index + 1)
    }

    private fun dcg(gradesInOrder: List<Int>): Double = gradesInOrder
        .mapIndexed { i, grade -> gain(grade) / log2(i + RANK_DISCOUNT_OFFSET) }
        .sum()

    /** 지수 이득 2^g - 1 — 등급 2를 등급 1보다 3배 무겁게 본다 */
    private fun gain(grade: Int): Double = ((1 shl grade) - 1).toDouble()
}
