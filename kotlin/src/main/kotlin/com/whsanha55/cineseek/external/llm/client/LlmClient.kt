package com.whsanha55.cineseek.external.llm.client

import com.whsanha55.cineseek.external.llm.config.LlmProperties
import com.whsanha55.cineseek.external.llm.dto.LlmChatRequest
import com.whsanha55.cineseek.external.llm.dto.LlmChatResponse
import com.whsanha55.cineseek.external.llm.dto.LlmMessage
import com.whsanha55.cineseek.global.config.http1RestClient
import com.whsanha55.cineseek.global.exception.ExternalApiException
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
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

    /** HTTP 오류·타임아웃을 ExternalApiException으로 바꾼다 — 200이어도 본문이 없으면 null */
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
    } catch (e: RestClientException) {
        throw ExternalApiException(TARGET, e)
    }

    companion object {
        private const val TARGET = "llm"
        private const val ROLE_USER = "user"
    }
}
