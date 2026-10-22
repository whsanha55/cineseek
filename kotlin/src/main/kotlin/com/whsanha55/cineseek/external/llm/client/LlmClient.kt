package com.whsanha55.cineseek.external.llm.client

import com.whsanha55.cineseek.external.llm.config.LlmProperties
import com.whsanha55.cineseek.external.llm.dto.LlmChatRequest
import com.whsanha55.cineseek.external.llm.dto.LlmChatResponse
import com.whsanha55.cineseek.external.llm.dto.LlmErrorResponse
import com.whsanha55.cineseek.external.llm.dto.LlmMessage
import com.whsanha55.cineseek.global.config.http1RestClient
import com.whsanha55.cineseek.global.exception.ExternalApiException
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClientException

/**
 * OpenAI 호환 POST /chat/completions 클라이언트 — 단일 user 프롬프트로 완성 텍스트를 받는다.
 * 마크다운 펜스 제거·JSON 파싱은 응답을 해석하는 호출부(MovieMoodTagger)의 책임이다
 */
@Component
class LlmClient(properties: LlmProperties) {
    private val apiKey = properties.apiKey
    private val model = properties.model
    private val temperature = properties.temperature

    private val restClient = http1RestClient(properties.connectTimeout, properties.readTimeout)
        .baseUrl(properties.baseUrl)
        .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer $apiKey")
        .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
        .build()

    /** user 프롬프트 하나를 보내 choices[0].message.content를 반환한다 */
    fun chat(userPrompt: String): String {
        check(apiKey.isNotBlank()) { "LLM API 키가 설정되지 않았다" }
        val response = fetch(userPrompt) ?: throw ExternalApiException(TARGET)
        return response.choices.firstOrNull()?.message?.content ?: throw ExternalApiException(TARGET)
    }

    /**
     * HTTP 오류·타임아웃을 ExternalApiException으로 바꾼다 — 200이어도 본문이 없으면 null.
     * 400 중 콘텐츠 필터(error.code 1301)만 LlmContentFilteredException으로 구분한다 —
     * 1302·1305 등 나머지 코드는 다시 시도하면 풀리는 일시 실패로 본다
     */
    private fun fetch(userPrompt: String): LlmChatResponse? = try {
        restClient.post()
            .uri("/chat/completions")
            .body(
                LlmChatRequest(
                    model = model,
                    messages = listOf(LlmMessage(ROLE_USER, userPrompt)),
                    temperature = temperature,
                ),
            )
            .retrieve()
            .body(LlmChatResponse::class.java)
    } catch (e: HttpClientErrorException.BadRequest) {
        throw if (isContentFiltered(e)) LlmContentFilteredException(e) else ExternalApiException(TARGET, e)
    } catch (e: RestClientException) {
        throw ExternalApiException(TARGET, e)
    }

    private fun isContentFiltered(e: HttpClientErrorException): Boolean {
        val body = runCatching { e.getResponseBodyAs(LlmErrorResponse::class.java) }.getOrNull()
        return body?.error?.code == CONTENT_FILTER_CODE
    }

    companion object {
        private const val TARGET = "llm"
        private const val CONTENT_FILTER_CODE = "1301"
        private const val ROLE_USER = "user"
    }
}
