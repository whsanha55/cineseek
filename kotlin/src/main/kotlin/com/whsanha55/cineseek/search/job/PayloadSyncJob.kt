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
 * payload 동기화 — PG의 분위기 태그(#40)·개봉일(#85)을 색인된 Qdrant 포인트의 mood_tags·release_date payload에만 덮어쓴다.
 * 임베딩은 하지 않고 다른 payload 키는 그대로 둔다. 이 필드가 payload에 들어가기 전에 색인된 포인트를 한 번 채우는 용도이고,
 * 그 뒤로는 색인(수집 틱·reindex)이 함께 쓴다. mood_tags·release_date payload 인덱스도 만든다.
 * 실행: docker compose run --rm api --cineseek.job=payload-sync
 */
@Component
@ConditionalOnProperty(prefix = "cineseek", name = ["job"], havingValue = "payload-sync")
class PayloadSyncJob(private val assembler: IndexedMovieAssembler, private val indexer: MovieIndexer) :
    ApplicationRunner {

    override fun run(args: ApplicationArguments) {
        sync()
        exitProcess(0) // 배치 성격 — 출력 후 종료
    }

    /** @return payload를 쓴 포인트 수 */
    fun sync(): Int {
        val fields = assembler.payloadSyncFieldsAll()
        val synced = indexer.syncPayload(fields)
        log.info {
            "payload 동기화 완료. PG movie=${fields.size}, 태그 있음=${fields.count { it.value.moodTags.isNotEmpty() }}, " +
                "개봉일 있음=${fields.count { it.value.releaseDate != null }}, 갱신 포인트=$synced"
        }
        return synced
    }
}
