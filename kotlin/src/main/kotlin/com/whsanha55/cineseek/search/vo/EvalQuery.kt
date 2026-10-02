package com.whsanha55.cineseek.search.vo

import com.whsanha55.cineseek.search.enums.EvalQueryTypeEnum
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.readValue

/** 정답 영화 하나 — title은 사람이 라벨을 검토할 때 보려고 둔다 */
data class EvalLabel(val tmdbId: Long, val title: String, val grade: Int)

/** 평가 쿼리와 정답 라벨 */
data class EvalQuery(val query: String, val type: EvalQueryTypeEnum, val labels: List<EvalLabel>) {
    /** tmdbId → grade */
    val grades: Map<Long, Int>
        get() = labels.associate { it.tmdbId to it.grade }

    companion object {
        private val GRADE_RANGE = 1..2

        /** classpath 리소스에서 평가 쿼리를 읽고 검증한다 */
        fun load(mapper: ObjectMapper, resource: String = "eval/queries.json"): List<EvalQuery> {
            val stream = requireNotNull(EvalQuery::class.java.classLoader.getResourceAsStream(resource)) {
                "평가 쿼리 리소스가 classpath에 없다. resource=$resource"
            }
            val queries: List<EvalQuery> = stream.use { mapper.readValue(it) }
            validate(queries)
            return queries
        }

        private fun validate(queries: List<EvalQuery>) {
            val duplicatedQueries = queries
                .groupBy { it.query }
                .filterValues { it.size > 1 }
                .keys
            require(duplicatedQueries.isEmpty()) { "쿼리가 중복됐다. queries=$duplicatedQueries" }
            queries.forEach { q ->
                require(q.query.isNotBlank()) { "쿼리가 비어 있다. query='${q.query}'" }
                q.labels.forEach { label ->
                    require(label.grade in GRADE_RANGE) {
                        "grade는 $GRADE_RANGE 범위여야 한다. " +
                            "query=${q.query}, tmdbId=${label.tmdbId}, grade=${label.grade}"
                    }
                }
                val duplicatedIds = q.labels
                    .groupBy { it.tmdbId }
                    .filterValues { it.size > 1 }
                    .keys
                require(duplicatedIds.isEmpty()) {
                    "한 쿼리 안에서 tmdbId가 중복됐다. query=${q.query}, tmdbIds=$duplicatedIds"
                }
            }
        }
    }
}
