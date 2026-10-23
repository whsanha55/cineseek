package com.whsanha55.cineseek.movie.service

import com.whsanha55.cineseek.external.llm.client.LlmClient
import com.whsanha55.cineseek.external.llm.client.LlmContentFilteredException
import com.whsanha55.cineseek.external.llm.config.LlmProperties
import com.whsanha55.cineseek.movie.entity.PersonEntity
import com.whsanha55.cineseek.movie.repository.PersonRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.readValue
import java.security.MessageDigest
import java.util.concurrent.Callable
import java.util.concurrent.Executors

private val log = KotlinLogging.logger {}

/** 번역 프롬프트 — 입력·응답 JSON 형식을 고정한다 (버전 해시의 입력) */
private val PROMPT = """
너는 영화 인물 정보를 한국어로 옮기는 번역가다. 반드시 아래 JSON 형식만으로 응답한다.
- placeOfBirth: 출생지를 한국어 지명으로 옮긴다. 국가부터 큰 단위 순서로 쓴다. 예: "Syracuse, New York, USA" → "미국 뉴욕주 시러큐스"
- biography: 영어 소개글을 자연스러운 한국어로 번역한다. 내용을 더하거나 빼지 않고 문단 구분(빈 줄)을 유지한다.
  인명·작품명은 한국에서 통용되는 표기를 쓴다.
- 입력이 null인 항목은 null로 둔다.

응답 형식:
{"placeOfBirth": "한국어 출생지", "biography": "한국어 소개"}

입력:
""".trimIndent()

/**
 * LLM(OpenAI 호환)으로 인물 출생지·영어 소개를 한국어로 옮겨 person에 저장한다. 영어 원문은 그대로 둔다.
 * 다시 물어도 결과가 같은 실패(콘텐츠 필터, 빈 결과)는 번역 버전과 함께 기록해 그 버전에서는 대상에서 뺀다
 */
@Service
class PersonTranslator(
    private val llmClient: LlmClient,
    private val personRepository: PersonRepository,
    private val llmProperties: LlmProperties,
    private val objectMapper: ObjectMapper,
) {

    /** 번역이 필요한 인물을 최근 갱신순으로 최대 [limit]명 번역한다. 번역해 저장한 인물 수를 반환한다 */
    fun translateAll(limit: Int): Int {
        if (llmProperties.apiKey.isBlank()) return 0
        return translate(personRepository.findIdsRequiringTranslation(version(), PageRequest.ofSize(limit)))
    }

    /**
     * [personIds]를 병렬 번역한다 — 고정 크기 스레드 풀로 LLM 동시 호출을 묶는다. 한 명 실패는 건너뛴다.
     * API 키가 비어 있으면 아무것도 하지 않는다. 번역해 저장한 인물 수를 반환한다
     */
    fun translate(personIds: List<Long>): Int {
        if (llmProperties.apiKey.isBlank() || personIds.isEmpty()) return 0
        val results = Executors.newFixedThreadPool(CONCURRENCY).use { executor ->
            executor.invokeAll(
                personIds.map { id ->
                    Callable {
                        runCatching { translateOne(id) }.onFailure { log.warn(it) { "인물 번역 스킵. personId=$id" } }
                    }
                },
            )
        }
        val translated = results.count { it.get().getOrDefault(false) }
        log.info { "인물 번역 완료. 대상=${personIds.size}, 성공=$translated" }
        return translated
    }

    /**
     * 한 명 번역 — 비어 있는 항목(출생지 번역, 한국어 소개)만 LLM에 묻는다. 저장했으면 true,
     * 번역할 항목이 없거나 이 버전에서 이미 영구 실패했으면 false. 영구 실패는 버전을 기록하고 false.
     * 그 밖의 실패(429·5xx·타임아웃·JSON 파싱)는 예외를 그대로 던져 다음 틱에 다시 시도된다.
     * 갱신은 personRepository.save의 자체 트랜잭션 하나로 충분하다 (MovieMoodTagger와 같은 이유로 @Transactional을 쓰지 않는다)
     */
    fun translateOne(personId: Long): Boolean {
        val person = personRepository.findById(personId).orElse(null)
        val pending = person?.takeIf { it.translationFailedModel != version() }?.let(::pendingOf) ?: return false

        val result = request(person, pending)
        if (result == null) {
            person.markTranslationFailed(version())
        } else {
            person.applyTranslation(placeOfBirthKo = result.placeOfBirth, biographyKo = result.biography)
        }
        personRepository.save(person)
        return result != null
    }

    /** 번역할 원문 — 비어 있는 항목(출생지 번역, 한국어 소개)만 담는다. 번역할 것이 없으면 null */
    private fun pendingOf(person: PersonEntity): LlmTranslation? = LlmTranslation(
        placeOfBirth = person.placeOfBirth?.takeIf { person.placeOfBirthKo == null },
        biography = person.biographyEn?.takeIf { person.biographyKo == null },
    ).takeIf { it.placeOfBirth != null || it.biography != null }

    /**
     * LLM에 [pending]의 번역을 묻는다 — 요청하지 않은 항목은 응답에 있어도 버린다.
     * 콘텐츠 필터 차단과, 요청한 항목이 모두 비어 돌아온 응답은 경고 후 null
     */
    private fun request(person: PersonEntity, pending: LlmTranslation): LlmTranslation? {
        val content = try {
            llmClient.chat(PROMPT + "\n" + objectMapper.writeValueAsString(pending))
        } catch (e: LlmContentFilteredException) {
            log.warn { "인물 번역 영구 실패 — 이 버전에서는 재시도 안 함. personId=${person.personId}, 원인=${e.message}" }
            null
        }
        val answer = content
            ?.let { objectMapper.readValue<LlmTranslation>(extractJson(it)) }
            ?.let {
                LlmTranslation(
                    placeOfBirth = it.placeOfBirth?.takeIf { v -> pending.placeOfBirth != null && v.isNotBlank() },
                    biography = it.biography?.takeIf { v -> pending.biography != null && v.isNotBlank() },
                )
            }
            ?.takeIf { it.placeOfBirth != null || it.biography != null }
        if (content != null && answer == null) {
            log.warn { "인물 번역 영구 실패 — 이 버전에서는 재시도 안 함. personId=${person.personId}, 원인=빈 결과" }
        }
        return answer
    }

    /** 번역 버전 — 모델명과 프롬프트 해시 앞 8자리. 프롬프트를 고치면 영구 실패했던 인물을 다시 시도한다 */
    fun version(): String {
        val hash = MessageDigest.getInstance("SHA-256").digest(PROMPT.toByteArray()).toHexString().take(HASH_LENGTH)
        return "${llmProperties.model}/person-$hash"
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

    /** 번역 입력이자 chat 응답 content에서 파싱되는 모양 — 프롬프트의 입력·응답 형식과 같다 */
    data class LlmTranslation(val placeOfBirth: String? = null, val biography: String? = null)

    companion object {
        private const val CONCURRENCY = 4

        private const val HASH_LENGTH = 8
    }
}
