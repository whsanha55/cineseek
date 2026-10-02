package com.whsanha55.cineseek.search.job

import com.whsanha55.cineseek.search.enums.EvalQueryTypeEnum
import com.whsanha55.cineseek.search.vo.EvalLabel
import java.security.MessageDigest
import java.util.HexFormat

/** 라벨이 있는 쿼리 하나의 점수 */
data class QueryScore(
    val query: String,
    val type: EvalQueryTypeEnum,
    val ndcg: Double,
    val rr: Double,
    val labelHash: String,
)

/** 기준값 JSON 형식 — {"queries": {"<query>": {"ndcg":..,"rr":..,"labelHash":".."}}} */
data class BaselineScore(val ndcg: Double, val rr: Double, val labelHash: String)

data class EvalBaseline(val queries: Map<String, BaselineScore>)

/** 평균 nDCG·MRR과 집계한 쿼리 수 */
data class MeanScore(val ndcg: Double, val rr: Double, val count: Int)

/** 기준값과 라벨이 같은 공통 쿼리만으로 현재 점수를 비교한 결과 */
data class BaselineComparison(
    /** 비교 대상(공통+라벨 동일) 쿼리만의 현재 평균 */
    val current: MeanScore,
    /** 같은 쿼리들의 기준값 평균 */
    val baseline: MeanScore,
    /** 기준값에 있으나 labelHash가 다른 쿼리 (비교 제외) */
    val labelChanged: List<String>,
    /** 기준값에 없는 쿼리 (비교 제외) */
    val newQueries: List<String>,
    /** 비교 대상 중 nDCG가 QUERY_DROP_THRESHOLD 초과 하락한 (query, delta) — 참고용, 많이 떨어진 순 */
    val drops: List<Pair<String, Double>>,
) {
    val ndcgDelta: Double get() = current.ndcg - baseline.ndcg
    val rrDelta: Double get() = current.rr - baseline.rr

    /** 평균 nDCG 또는 MRR이 MEAN_DROP_THRESHOLD 초과 하락. 비교 대상이 0개면 false */
    val isRegression: Boolean
        get() = current.count > 0 &&
            (ndcgDelta < -EvalReport.MEAN_DROP_THRESHOLD || rrDelta < -EvalReport.MEAN_DROP_THRESHOLD)
}

/** 쿼리별 점수를 전체·유형별로 집계하고 기준값과 비교한다 */
class EvalReport(val scores: List<QueryScore>, baseline: EvalBaseline?) {
    val overall: MeanScore = mean(scores.map { it.ndcg to it.rr })

    /** 점수가 있는 유형만 담는다 */
    val byType: Map<EvalQueryTypeEnum, MeanScore> = scores
        .groupBy { it.type }
        .mapValues { (_, typeScores) -> mean(typeScores.map { it.ndcg to it.rr }) }

    /** baseline이 없으면 null */
    val comparison: BaselineComparison? = baseline?.let { compare(it) }

    /** 현재 점수를 다음 비교의 기준값으로 만든다 */
    fun toBaseline(): EvalBaseline = EvalBaseline(
        scores.associate { it.query to BaselineScore(it.ndcg, it.rr, it.labelHash) },
    )

    private fun compare(baseline: EvalBaseline): BaselineComparison {
        val labelChanged = mutableListOf<String>()
        val newQueries = mutableListOf<String>()
        val compared = mutableListOf<Pair<QueryScore, BaselineScore>>()
        scores.forEach { score ->
            val base = baseline.queries[score.query]
            when {
                base == null -> newQueries += score.query
                base.labelHash != score.labelHash -> labelChanged += score.query
                else -> compared += score to base
            }
        }
        val drops = compared
            .map { (score, base) -> score.query to score.ndcg - base.ndcg }
            .filter { (_, delta) -> delta < -QUERY_DROP_THRESHOLD }
            .sortedBy { (_, delta) -> delta }
        return BaselineComparison(
            current = mean(compared.map { (score, _) -> score.ndcg to score.rr }),
            baseline = mean(compared.map { (_, base) -> base.ndcg to base.rr }),
            labelChanged = labelChanged,
            newQueries = newQueries,
            drops = drops,
        )
    }

    companion object {
        const val MEAN_DROP_THRESHOLD = 0.02
        const val QUERY_DROP_THRESHOLD = 0.2
        private const val LABEL_HASH_LENGTH = 12

        /** 라벨 집합 해시 — (tmdbId:grade)를 tmdbId 순 정렬해 이은 문자열의 SHA-256 앞 12자(hex). 라벨 순서와 title 변경에는 무관 */
        fun labelHash(labels: List<EvalLabel>): String {
            val canonical = labels
                .sortedBy { it.tmdbId }
                .joinToString(",") { "${it.tmdbId}:${it.grade}" }
            val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
            return HexFormat.of().formatHex(digest).take(LABEL_HASH_LENGTH)
        }

        /** (nDCG, RR) 목록의 평균. 비어 있으면 0 */
        private fun mean(values: List<Pair<Double, Double>>): MeanScore {
            if (values.isEmpty()) return MeanScore(0.0, 0.0, 0)
            return MeanScore(
                ndcg = values.map { it.first }.average(),
                rr = values.map { it.second }.average(),
                count = values.size,
            )
        }
    }
}
