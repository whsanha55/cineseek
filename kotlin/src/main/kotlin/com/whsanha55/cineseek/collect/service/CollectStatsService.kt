package com.whsanha55.cineseek.collect.service

import com.whsanha55.cineseek.collect.config.CollectProperties
import com.whsanha55.cineseek.collect.enums.CollectTaskStatusEnum
import com.whsanha55.cineseek.collect.enums.CollectTaskTypeEnum
import com.whsanha55.cineseek.collect.repository.CollectTaskRepository
import com.whsanha55.cineseek.movie.repository.MovieRepository
import com.whsanha55.cineseek.movie.repository.PersonRepository
import com.whsanha55.cineseek.search.service.MovieIndexer
import org.springframework.stereotype.Service

/**
 * 수집 현황 집계 — 수집 틱의 통계 로그와 collect-report 배치가 같은 지표를 공유한다.
 * 큐 상태(유형별), 한국 영화 연도별 수, 줄거리 누락, 시드 인물 확보, PG/색인 수를 한 번에 모은다
 */
@Service
class CollectStatsService(
    private val collectTaskRepository: CollectTaskRepository,
    private val movieRepository: MovieRepository,
    private val personRepository: PersonRepository,
    private val indexer: MovieIndexer,
    private val properties: CollectProperties,
) {

    /** 현재 시점 스냅샷. Qdrant 조회 실패는 qdrantPointCount=null로만 반영하고 나머지는 그대로 담는다 */
    fun snapshot(): CollectSnapshot = CollectSnapshot(
        statusCountsByType = CollectTaskTypeEnum.entries.associateWith { type ->
            CollectTaskStatusEnum.entries.associateWith { status ->
                collectTaskRepository.countByTaskTypeAndStatus(type, status)
            }
        },
        krMoviesByYear = movieRepository.countKrMoviesByYear()
            .associateBy({ (it.get(YEAR_ALIAS) as Number).toInt() }, { (it.get(TOTAL_ALIAS) as Number).toLong() }),
        missingOverviewCount = movieRepository.countByOverviewIsNull(),
        seedCoverage = seedCoverage(),
        pgMovieCount = movieRepository.count(),
        qdrantPointCount = runCatching { indexer.countPoints() }.getOrNull(),
    )

    /** 시드 인물 확보 현황 — 아직 수집 전(행 없음)인 시드는 카운트가 null로 남는다 */
    private fun seedCoverage(): List<SeedCoverage> {
        val stored = personRepository.findAllById(properties.seedPersonIds).associateBy { it.personId }
        return properties.seedPersonIds.sorted().map { personId ->
            val person = stored[personId]
            SeedCoverage(
                personId = personId,
                name = person?.name,
                storedCount = person?.filmoStoredCount,
                externalCount = person?.filmoExternalCount,
            )
        }
    }

    companion object {
        private const val YEAR_ALIAS = "yr" // countKrMoviesByYear 선택 별칭과 같아야 한다
        private const val TOTAL_ALIAS = "total"
    }
}

/** 시드 인물 한 명의 필모그래피 확보 — 외부(TMDB) 대비 저장(DB) 출연작 수. null은 아직 수집 전 */
data class SeedCoverage(val personId: Long, val name: String?, val storedCount: Int?, val externalCount: Int?)

/** 수집 현황 스냅샷 — 로그 문자열 조립은 사용하는 쪽(스케줄러·보고 배치)이 정의한다 */
data class CollectSnapshot(
    val statusCountsByType: Map<CollectTaskTypeEnum, Map<CollectTaskStatusEnum, Long>>,
    val krMoviesByYear: Map<Int, Long>, // 연도 내림차순
    val missingOverviewCount: Long,
    val seedCoverage: List<SeedCoverage>,
    val pgMovieCount: Long,
    val qdrantPointCount: Long?, // null이면 Qdrant 조회 실패
) {
    /** 상태별 총계 — 유형별 집계를 합산한다 */
    fun count(status: CollectTaskStatusEnum): Long = statusCountsByType.values.sumOf { it[status] ?: 0L }

    /** 시드 인물의 저장된 출연작 합계 */
    val seedStoredTotal: Int
        get() = seedCoverage.sumOf { it.storedCount ?: 0 }

    /** 시드 인물의 외부 출연작 합계 */
    val seedExternalTotal: Int
        get() = seedCoverage.sumOf { it.externalCount ?: 0 }

    /** PG 대비 색인 미완료 수 — Qdrant 조회 실패 시 null */
    val unindexedCount: Long?
        get() = qdrantPointCount?.let { pgMovieCount - it }
}
