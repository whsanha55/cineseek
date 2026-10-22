package com.whsanha55.cineseek.search.job

import com.whsanha55.cineseek.search.service.IndexedMovieAssembler
import com.whsanha55.cineseek.search.service.MovieIndexer
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import kotlin.system.exitProcess

private val log = KotlinLogging.logger {}

/**
 * 분위기 태그 payload 동기화 — PG의 분위기 태그를 색인된 Qdrant 포인트의 mood_tags payload에만 덮어쓴다 (#40).
 * 임베딩은 하지 않는다. mood_tags가 payload에 들어가기 전에 색인된 포인트를 한 번 채우는 용도이고,
 * 그 뒤로는 색인(수집 틱·reindex)이 mood_tags를 함께 쓴다. mood_tags payload 인덱스도 만든다.
 * 실행: docker compose run --rm api --cineseek.job=mood-tag-sync
 */
@Component
@ConditionalOnProperty(prefix = "cineseek", name = ["job"], havingValue = "mood-tag-sync")
class MoodTagSyncJob(private val assembler: IndexedMovieAssembler, private val indexer: MovieIndexer) :
    ApplicationRunner {

    override fun run(args: ApplicationArguments) {
        sync()
        exitProcess(0) // 배치 성격 — 출력 후 종료
    }

    /** @return mood_tags를 쓴 포인트 수 */
    fun sync(): Int {
        val moodTags = assembler.moodTagsAll()
        val synced = indexer.syncMoodTags(moodTags)
        log.info {
            "mood_tags 동기화 완료. PG movie=${moodTags.size}, 태그 있음=${moodTags.count { it.value.isNotEmpty() }}, " +
                "갱신 포인트=$synced"
        }
        return synced
    }
}
