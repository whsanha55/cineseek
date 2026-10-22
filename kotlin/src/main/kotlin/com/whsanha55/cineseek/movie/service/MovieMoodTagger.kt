package com.whsanha55.cineseek.movie.service

import com.whsanha55.cineseek.external.llm.client.LlmClient
import com.whsanha55.cineseek.external.llm.client.LlmContentFilteredException
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
 * 태깅 버전(모델+프롬프트 해시)이 바뀌면 findRequiringMoodTag 대상이 늘어나 전체 재태깅된다.
 * 다시 물어도 결과가 같은 실패(콘텐츠 필터, 빈 결과)는 버전과 함께 기록해 그 버전에서는 대상에서 뺀다
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
     * 분위기 태그가 없거나 버전이 다른 영화 전체를 병렬 태깅한다 — 재색인 배치용
     */
    fun tagAll(): List<Long> = tagAll(Int.MAX_VALUE)

    /**
     * 분위기 태그가 없거나 버전이 다른 영화를 최대 [limit]건 병렬 태깅한다 — 고정 크기 스레드 풀로 동시 제한.
     * 수집 틱이 갖는 LLM 호출 수를 상한으로 묶어 폭주를 막는다. 한 건 실패는 건너뛰고 실제로 태깅해 저장한 movieId를 반환한다.
     * 태그는 임베딩 텍스트에 들어가므로 호출하는 쪽이 이 영화들을 다시 색인해야 검색에 반영된다.
     * API 키가 비어 있으면 경고 후 빈 목록
     */
    fun tagAll(limit: Int): List<Long> {
        if (llmProperties.apiKey.isBlank()) {
            log.warn { "LLM API 키 미설정 — 분위기 태깅을 건너뛴다" }
            return emptyList()
        }
        val ids = movieRepository.findRequiringMoodTag(version()).mapNotNull { it.movieId }.take(limit)
        val results = Executors.newFixedThreadPool(TAG_CONCURRENCY).use { executor ->
            executor.invokeAll(
                ids.map { id ->
                    Callable { runCatching { tagOne(id) }.onFailure { log.warn(it) { "분위기 태깅 스킵. movieId=$id" } } }
                },
            )
        }
        val tagged = ids.filterIndexed { i, _ -> results[i].get().getOrDefault(false) }
        log.info { "분위기 태깅 완료. 대상=${ids.size}, 성공=${tagged.size}" }
        return tagged
    }

    /**
     * 영화 한 건 태깅 — 프롬프트 조립 → LLM → JSON 파싱 → applyMood 저장. 저장했으면 true,
     * 영화가 없거나 줄거리가 없어 태깅할 재료가 없으면 false.
     * 영구 실패(콘텐츠 필터, 빈 결과)는 markMoodFailed로 현재 버전을 기록하고 false — 매 틱 같은 실패를 되풀이하지 않는다.
     * 그 밖의 실패(429·5xx·타임아웃·JSON 파싱)는 예외를 그대로 던져 다음 틱에 다시 시도된다.
     * @Transactional은 쓰지 않는다: tagAll에서의 자기호출은 프록시를 우회해 어노테이션이 무효고,
     * 갱신은 movieRepository.save(SimpleJpaRepository)의 자체 트랜잭션 하나로 충분하다.
     * 조회는 genres까지 한 번에 가져오는 findAllById로 — OSIV=false라 findById의 지연 로딩은 프롬프트 조립에서 터진다
     */
    fun tagOne(movieId: Long): Boolean {
        val movie = movieRepository.findAllById(listOf(movieId)).firstOrNull()
        val overview = movie?.overview ?: return false
        val mood = requestMood(movie, overview)
        if (mood == null) {
            movie.markMoodFailed(version())
        } else {
            movie.applyMood(mood.tags.joinToString(", "), mood.description, version(), Instant.now(clock))
        }
        movieRepository.save(movie)
        return mood != null
    }

    /**
     * LLM에 분위기를 묻는다. 다시 물어도 결과가 같은 실패는 경고를 남기고 null —
     * 콘텐츠 필터 차단, 그리고 형식은 맞지만 태그·설명이 빈 응답(사실상 답변 거부)
     */
    private fun requestMood(movie: MovieEntity, overview: String): LlmMoodResult? {
        val content = try {
            llmClient.chat(prompt(movie, overview))
        } catch (e: LlmContentFilteredException) {
            log.warn { "분위기 태깅 영구 실패 — 이 버전에서는 재시도 안 함. movieId=${movie.movieId}, 원인=${e.message}" }
            return null
        }
        return objectMapper.readValue<LlmMoodResult>(extractJson(content))
            .takeIf { it.tags.isNotEmpty() && it.description.isNotBlank() }
            .also {
                if (it == null) {
                    log.warn { "분위기 태깅 영구 실패 — 이 버전에서는 재시도 안 함. movieId=${movie.movieId}, 원인=빈 결과" }
                }
            }
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
