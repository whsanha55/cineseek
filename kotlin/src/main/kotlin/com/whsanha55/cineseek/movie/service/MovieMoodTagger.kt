package com.whsanha55.cineseek.movie.service

import com.whsanha55.cineseek.external.llm.client.LlmClient
import com.whsanha55.cineseek.external.llm.config.LlmProperties
import com.whsanha55.cineseek.movie.entity.MovieEntity
import com.whsanha55.cineseek.movie.repository.MovieRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.readValue
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.util.concurrent.Callable
import java.util.concurrent.Executors

private val log = KotlinLogging.logger {}

/** 시스템 프롬프트 — 후보 어휘와 응답 JSON 형식을 고정한다 (버전 해시의 입력) */
private val SYSTEM_PROMPT = """
너는 영화 줄거리를 보고 분위기·정서 태그를 붙이는 태거다. 반드시 아래 JSON 형식만으로 응답한다. 한국어로 답한다.
태그는 제공된 후보 어휘에서 우선 고르고, 맞는 게 없을 때만 새 태그를 만든다(공백 없는 짧은 단어).
tags는 3~6개, description은 40자 이내 한 문장이다.

후보 어휘: 잔잔한, 서정적, 긴장감 있는, 유쾌한, 어두운, 감동적인, 소름 돋는, 몽환적인, 힐링, 처절한, 잔혹한, 로맨틱, 냉소적, 희망적, 애절한, 코미디, 미스터리, 누아르, 판타지, 아늑한, 슬픈, 뜨거운, 차가운, 아련한, 절망적인

응답 형식:
{"tags": ["태그1", "태그2", "태그3"], "description": "한 문장 설명"}
""".trimIndent()

/** 사용자 프롬프트 뼈대 — %s에 제목·원제·장르·줄거리가 순서대로 들어간다 (버전 해시의 입력) */
private val USER_PROMPT_TEMPLATE = """
제목: %s
원제: %s
장르: %s
줄거리: %s
""".trimIndent()

/**
 * LLM(OpenAI 호환)로 줄거리에 분위기 태그를 붙이는 배치 — PG(SoT)의 mood 컬럼만 갱신한다.
 * 태깅 버전(모델+프롬프트 해시)이 바뀌면 findRequiringMoodTag 대상이 늘어나 전체 재태깅된다
 */
@Service
class MovieMoodTagger(
    private val llmClient: LlmClient,
    private val movieRepository: MovieRepository,
    private val llmProperties: LlmProperties,
    private val objectMapper: ObjectMapper,
    private val clock: Clock,
) {

    /**
     * 분위기 태그가 없거나 버전이 다른 영화를 병렬 태깅한다 — 고정 크기 스레드 풀로 동시 제한 (ponytail-audit #6: 가상 스레드 + Semaphore에서 변경).
     * 한 건 실패는 건너뛰고 성공 수를 반환한다. API 키가 비어 있으면 경고 후 0
     */
    fun tagAll(): Int {
        if (llmProperties.apiKey.isBlank()) {
            log.warn { "LLM API 키 미설정 — 분위기 태깅을 건너뛴다" }
            return 0
        }
        val ids = movieRepository.findRequiringMoodTag(version()).mapNotNull { it.movieId }
        val results = Executors.newFixedThreadPool(TAG_CONCURRENCY).use { executor ->
            executor.invokeAll(
                ids.map { id ->
                    Callable { runCatching { tagOne(id) }.onFailure { log.warn(it) { "분위기 태깅 스킵. movieId=$id" } } }
                },
            )
        }
        val succeeded = results.count { it.get().isSuccess }
        log.info { "분위기 태깅 완료. 대상=${ids.size}, 성공=$succeeded" }
        return succeeded
    }

    /**
     * 영화 한 건 태깅 — 프롬프트 조립 → LLM → JSON 파싱 → applyMood 저장.
     * @Transactional은 쓰지 않는다: tagAll에서의 자기호출은 프록시를 우회해 어노테이션이 무효고,
     * 갱신은 movieRepository.save(SimpleJpaRepository)의 자체 트랜잭션 하나로 충분하다.
     * 조회는 genres까지 한 번에 가져오는 findAllById로 — OSIV=false라 findById의 지연 로딩은 프롬프트 조립에서 터진다
     */
    fun tagOne(movieId: Long) {
        val movie = movieRepository.findAllById(listOf(movieId)).firstOrNull() ?: return
        val overview = movie.overview ?: return // 줄거리가 없으면 태깅 재료가 없다 → 스킵
        val content = llmClient.chat(prompt(movie, overview))
        val mood = objectMapper.readValue<LlmMoodResult>(extractJson(content))
        check(mood.tags.isNotEmpty() && mood.description.isNotBlank()) { "분위기 태깅 결과가 비어 있다. movieId=$movieId" }
        movie.applyMood(mood.tags.joinToString(", "), mood.description, version(), Instant.now(clock))
        movieRepository.save(movie)
    }

    /** 태깅 버전 — 모델명과 프롬프트 해시 앞 8자리. 프롬프트를 고치면 값이 변해 전체 재태깅된다 */
    fun version(): String {
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(PROMPT_TEMPLATE.toByteArray())
            .toHexString()
            .take(VERSION_HASH_LENGTH)
        return "${llmProperties.model}/mood-$hash"
    }

    /** LLM 응답에서 JSON 본문만 추출 — 마크다운 코드 펜스로 감싸거나 잡담을 섞어도 견딘다 */
    private fun extractJson(content: String): String {
        val trimmed = content.trim()
        if (trimmed.startsWith("{")) return trimmed
        val first = trimmed.indexOf('{')
        val last = trimmed.lastIndexOf('}')
        require(first in 0 until last) { "LLM 응답에서 JSON을 찾지 못했다. content=$trimmed" }
        return trimmed.substring(first, last + 1)
    }

    private fun prompt(movie: MovieEntity, overview: String): String = SYSTEM_PROMPT + "\n\n" +
        USER_PROMPT_TEMPLATE.format(
            movie.title,
            movie.originalTitle ?: "없음",
            movie.genres.joinToString(", ") { it.name },
            overview,
        )

    /** chat 응답 content에서 파싱되는 모양 — 시스템 프롬프트의 응답 형식과 같다 */
    data class LlmMoodResult(val tags: List<String> = emptyList(), val description: String = "")

    companion object {
        private val PROMPT_TEMPLATE = SYSTEM_PROMPT + "\n\n" + USER_PROMPT_TEMPLATE

        private const val TAG_CONCURRENCY = 8

        private const val VERSION_HASH_LENGTH = 8
    }
}
