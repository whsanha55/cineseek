package com.whsanha55.cineseek.collect.job

import com.whsanha55.cineseek.external.tmdb.client.TmdbClient
import com.whsanha55.cineseek.movie.entity.PersonEntity
import com.whsanha55.cineseek.movie.repository.MovieCastRepository
import com.whsanha55.cineseek.movie.repository.PersonRepository
import com.whsanha55.cineseek.movie.service.PersonTranslator
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import kotlin.system.exitProcess

private val log = KotlinLogging.logger {}

/**
 * 인물 상세 백필 — 출연 N편 이상인데 person 행이 없는 배우의 TMDB 상세(생몰일·출생지·소개·외부 링크)를 받아 저장하고,
 * 이어서 번역이 남은 인물 전체를 한국어로 번역한다. 필모그래피 확장은 하지 않는다 (filmo_checked_at은 NULL로 둬
 * 인물 페이지를 처음 열 때 필모 수집이 돈다). 이미 person 행이 있는 인물은 건너뛰므로 중단돼도 다시 돌리면 이어서 한다.
 * 실행: docker compose run --rm api --cineseek.job=person-details [--min-works=5]
 */
@Component
@ConditionalOnProperty(prefix = "cineseek", name = ["job"], havingValue = "person-details")
class PersonDetailsJob(
    private val movieCastRepository: MovieCastRepository,
    private val personRepository: PersonRepository,
    private val tmdbClient: TmdbClient,
    private val personTranslator: PersonTranslator,
) : ApplicationRunner {

    override fun run(args: ApplicationArguments) {
        val minWorks = args.getOptionValues("min-works")?.firstOrNull()?.toLong() ?: DEFAULT_MIN_WORKS
        fetchDetails(minWorks)
        translateAll()
        exitProcess(0) // 배치 성격 — 끝나면 종료
    }

    /** TMDB 상세를 받아 person 행을 만든다. 한 명 실패는 건너뛴다. @return 저장한 인물 수 */
    fun fetchDetails(minWorks: Long): Int {
        val ids = movieCastRepository.findPersonIdsWithoutDetails(minWorks)
        log.info { "인물 상세 수집 시작. 출연 ${minWorks}편 이상, 대상=${ids.size}" }
        val permits = Semaphore(TMDB_CONCURRENCY)
        val results = Executors.newVirtualThreadPerTaskExecutor().use { executor ->
            executor.invokeAll(
                ids.mapIndexed { index, id ->
                    Callable {
                        permits.acquire()
                        try {
                            runCatching { savePerson(id) }
                                .onFailure { log.warn(it) { "인물 상세 스킵. personId=$id" } }
                                .getOrDefault(false)
                        } finally {
                            permits.release()
                            if ((index + 1) % PROGRESS_EVERY == 0) log.info { "인물 상세 진행 ${index + 1}/${ids.size}" }
                        }
                    }
                },
            )
        }
        val saved = results.count { it.get() }
        log.info { "인물 상세 수집 완료. 대상=${ids.size}, 저장=$saved" }
        return saved
    }

    /** 번역이 남은 인물 전체를 묶음 단위로 번역한다. 실패한 인물은 수집 틱이 다시 집는다. @return 번역한 인물 수 */
    fun translateAll(): Int {
        val ids = personRepository.findIdsRequiringTranslation(personTranslator.version(), Pageable.unpaged())
        log.info { "인물 번역 시작. 대상=${ids.size}" }
        var translated = 0
        ids.chunked(TRANSLATE_CHUNK).forEachIndexed { index, chunk ->
            translated += personTranslator.translate(chunk)
            log.info { "인물 번역 진행 ${minOf((index + 1) * TRANSLATE_CHUNK, ids.size)}/${ids.size}, 성공 누적=$translated" }
        }
        return translated
    }

    /** @return TMDB에 인물이 있어 저장했으면 true (404는 false) */
    private fun savePerson(personId: Long): Boolean {
        val info = tmdbClient.fetchPerson(personId) ?: return false
        personRepository.findById(info.personId).orElseGet { PersonEntity(personId = info.personId, name = info.name) }
            .apply { updateFrom(info) }
            .let(personRepository::save)
        return true
    }

    companion object {
        const val DEFAULT_MIN_WORKS = 5L
        private const val TMDB_CONCURRENCY = 8
        private const val TRANSLATE_CHUNK = 200
        private const val PROGRESS_EVERY = 1000
    }
}
