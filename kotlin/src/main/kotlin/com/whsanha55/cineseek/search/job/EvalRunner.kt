package com.whsanha55.cineseek.search.job

import com.whsanha55.cineseek.external.embedding.client.EmbeddingClient
import com.whsanha55.cineseek.movie.service.MovieTmdbIdService
import com.whsanha55.cineseek.search.facade.SearchFacade
import com.whsanha55.cineseek.search.vo.EvalQuery
import com.whsanha55.cineseek.search.vo.ScoredMovieCard
import com.whsanha55.cineseek.search.vo.SearchFilter
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.readValue
import java.util.Locale
import kotlin.system.exitProcess

private val log = KotlinLogging.logger {}

/**
 * 라벨셋(eval/queries.json) 기준 nDCG@10·MRR 평가 — 쿼리별 top-5와 지표, 유형별·전체 평균, 기준값 대비 변화를 출력한다.
 * 기준값(eval/baseline.json)은 마지막 줄의 JSON을 그대로 저장해 갱신한다. 회귀면 종료 코드 1.
 * 실행: docker compose run --rm api --cineseek.job=eval (출력 후 종료)
 */
@Component
@ConditionalOnProperty(prefix = "cineseek", name = ["job"], havingValue = "eval")
class EvalRunner(
    private val searchFacade: SearchFacade,
    private val embeddingClient: EmbeddingClient,
    private val movieTmdbIdService: MovieTmdbIdService,
    private val objectMapper: ObjectMapper,
) : ApplicationRunner {

    override fun run(args: ApplicationArguments) {
        val queries = EvalQuery.load(objectMapper)
        val embeddings = embeddingClient.embed(queries.map { it.query }) // python처럼 한 번의 배치로
        val existing = movieTmdbIdService.existingTmdbIds(queries.flatMap { q -> q.labels.map { it.tmdbId } })
        val scores = queries.mapIndexedNotNull { i, query ->
            val items = searchFacade.search(embeddings[i], SearchFilter(limit = FETCH)).items
            score(query, items, existing)
        }
        val report = EvalReport(scores, loadBaseline())
        log.info { summary(report) }
        log.info { "기준값 JSON (eval/baseline.json에 저장하면 다음 실행의 비교 기준이 된다)\n" + baselineJson(report) }
        val isRegression = report.comparison?.isRegression == true
        exitProcess(if (isRegression) 1 else 0) // 배치 성격 — 출력 후 종료
    }

    /**
     * 쿼리 하나를 채점하고 로그를 남긴다. PG에 있는 라벨이 하나도 없으면 평균에서 빼도록 null.
     * RRF 동점은 Qdrant가 실행마다 순서를 바꾸므로 tmdbId 순으로 고정한다 — 그래야 같은 조건에서 같은 점수가 나온다
     */
    private fun score(query: EvalQuery, items: List<ScoredMovieCard>, existing: Set<Long>): QueryScore? {
        val tmdbIds = movieTmdbIdService.tmdbIds(items.map { it.card.movieId })
        val top = items
            .mapNotNull { item -> tmdbIds[item.card.movieId]?.let { it to item } }
            .sortedWith(compareByDescending<Pair<Long, ScoredMovieCard>> { it.second.score }.thenBy { it.first })
            .take(K)
        val ranked = top.map { it.first }
        val grades = query.grades.filterKeys { it in existing } // 코퍼스에 없는 정답은 IDCG에서 뺀다
        val ndcg = EvalMetrics.ndcg(ranked, grades, K)
        val rr = EvalMetrics.reciprocalRank(ranked, grades, K)
        val unlabeled = ranked.count { it !in query.grades }
        val lines = top.take(SHOW_TOP).mapIndexed { j, (tmdbId, r) ->
            val grade = query.grades[tmdbId] ?: 0
            val genres = r.card.genres.map { it.name }
            "  ${j + 1}. ${r.card.title} (${r.card.releaseYear})  score=${fmt(r.score)}  grade=$grade  genres=$genres"
        }
        val missing = query.grades.size - grades.size
        log.info {
            "▶ [${query.type}] ${query.query}  nDCG@$K=${fmt(ndcg)}  RR=${fmt(rr)}  " +
                "결과=${ranked.size}건  라벨없음=$unlabeled/${ranked.size}" +
                (if (missing > 0) "  코퍼스에없는라벨=$missing" else "") +
                "\n" + lines.joinToString("\n")
        }
        if (grades.isEmpty()) {
            log.warn { "PG에 있는 라벨이 없어 평균에서 제외한다. query=${query.query}" }
            return null
        }
        return QueryScore(query.query, query.type, ndcg, rr, EvalReport.labelHash(query.labels))
    }

    private fun loadBaseline(): EvalBaseline? =
        EvalRunner::class.java.classLoader.getResourceAsStream(BASELINE_RESOURCE)?.use { objectMapper.readValue(it) }

    private fun summary(report: EvalReport): String = buildString {
        appendLine("== 평가 요약 (nDCG@$K / MRR) ==")
        appendLine("전체  ${fmt(report.overall)}")
        report.byType.forEach { (type, mean) -> appendLine("$type  ${fmt(mean)}") }
        val comparison = report.comparison
        if (comparison == null) {
            append("기준값 없음 — $BASELINE_RESOURCE 를 추가하면 변화가 표시된다")
            return@buildString
        }
        val (base, current) = comparison.baseline to comparison.current
        appendLine(
            "기준값 대비 (공통 ${current.count}개)  " +
                "nDCG ${fmt(base.ndcg)} → ${fmt(current.ndcg)} (${delta(comparison.ndcgDelta)})  " +
                "MRR ${fmt(base.rr)} → ${fmt(current.rr)} (${delta(comparison.rrDelta)})",
        )
        if (comparison.labelChanged.isNotEmpty()) appendLine("라벨 변경으로 비교 제외: ${comparison.labelChanged}")
        if (comparison.newQueries.isNotEmpty()) appendLine("기준값에 없는 쿼리: ${comparison.newQueries}")
        comparison.drops.forEach { (query, d) -> appendLine("  하락(참고) $query  nDCG ${delta(d)}") }
        append(if (comparison.isRegression) "결과: REGRESSION" else "결과: OK")
    }

    private fun baselineJson(report: EvalReport): String = objectMapper.writerWithDefaultPrettyPrinter()
        .writeValueAsString(report.toBaseline())

    private fun fmt(value: Double): String = String.format(Locale.ROOT, "%.3f", value)

    private fun fmt(mean: MeanScore): String = "${fmt(mean.ndcg)} / ${fmt(mean.rr)}  (${mean.count}개)"

    private fun delta(value: Double): String = String.format(Locale.ROOT, "%+.3f", value)

    companion object {
        private const val K = 10
        private const val FETCH = K * 2 // 10위 경계의 동점이 잘리지 않게 넉넉히 받아 정렬 후 자른다
        private const val SHOW_TOP = 5
        private const val BASELINE_RESOURCE = "eval/baseline.json"
    }
}
