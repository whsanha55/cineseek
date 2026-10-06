package com.whsanha55.cineseek.collect.job

import com.whsanha55.cineseek.collect.entity.CollectStateEntity
import com.whsanha55.cineseek.collect.repository.CollectStateRepository
import com.whsanha55.cineseek.collect.service.CollectSnapshot
import com.whsanha55.cineseek.collect.service.CollectStatsService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import kotlin.system.exitProcess

private val log = KotlinLogging.logger {}

/**
 * 수집 현황 보고 배치 — 모드·마지막 실행, 태스크 유형별 상태 집계, 한국 영화 연도별 수, 줄거리 누락,
 * 시드 배우 확보율(외부 대비 저장), PG 대비 색인 미완료 수를 콘솔 로그로 출력한다.
 * 실행: docker compose run --rm api --cineseek.job=collect-report (출력 후 종료)
 */
@Component
@ConditionalOnProperty(prefix = "cineseek", name = ["job"], havingValue = "collect-report")
class CollectReportRunner(
    private val collectStateRepository: CollectStateRepository,
    private val statsService: CollectStatsService,
) : ApplicationRunner {

    override fun run(args: ApplicationArguments) {
        val state = collectStateRepository.findById(CollectStateEntity.SINGLETON_ID).orElse(null)
        log.info { report(state, statsService.snapshot()) }
        exitProcess(0) // 배치 성격 — 출력 후 종료
    }

    private fun report(state: CollectStateEntity?, snapshot: CollectSnapshot): String = buildString {
        appendLine("== 수집 현황 ==")
        if (state == null) {
            appendLine("수집 상태 행 없음 — 아직 bootstrap 전이다")
        } else {
            appendLine("모드=${state.mode}, 전환시각=${state.modeChangedAt}, bootstrap=${state.bootstrappedAt}")
            appendLine("마지막 실행=${state.lastRunAt}, 요약=${state.lastSummary}")
        }
        appendLine("-- 태스크 유형별 상태 --")
        snapshot.statusCountsByType.forEach { (type, counts) ->
            appendLine("$type  " + counts.entries.joinToString("  ") { "${it.key}=${it.value}" })
        }
        appendLine("-- 영화 --")
        appendLine(
            "PG 전체=${snapshot.pgMovieCount}, 색인미완료=${snapshot.unindexedCount ?: "n/a"}, " +
                "줄거리누락=${snapshot.missingOverviewCount}",
        )
        appendLine("한국 영화(연도:수)=${snapshot.krMoviesByYear.entries.joinToString(",") { "${it.key}:${it.value}" }}")
        appendLine("-- 시드 배우 확보 (저장/외부) --")
        if (snapshot.seedCoverage.isEmpty()) {
            appendLine("설정된 시드 인물 없음")
        } else {
            snapshot.seedCoverage.forEach { seed ->
                val counts = if (seed.storedCount == null || seed.externalCount == null) {
                    "미수집"
                } else {
                    "${seed.storedCount}/${seed.externalCount}"
                }
                appendLine("  ${seed.name ?: "person"}(${seed.personId})  $counts")
            }
            appendLine("합계  ${snapshot.seedStoredTotal}/${snapshot.seedExternalTotal}")
        }
    }
}
