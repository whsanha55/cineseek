package com.whsanha55.cineseek.external.llm.client

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.whsanha55.cineseek.external.llm.config.LlmProperties
import com.whsanha55.cineseek.global.exception.ExternalApiException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tools.jackson.databind.ObjectMapper

/** /chat/completions(OpenAI 호환) 계약 테스트(WireMock 스텁) — 펜스 제거·JSON 파싱은 MovieMoodTaggerTest가 담당한다 */
class LlmClientTest {

    private val wiremock = WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort())
    private lateinit var client: LlmClient

    @BeforeEach
    fun setUp() {
        wiremock.start()
        client = LlmClient(LlmProperties(baseUrl = wiremock.baseUrl(), apiKey = "test-key"))
    }

    @AfterEach
    fun tearDown() {
        wiremock.stop()
    }

    @Test
    fun `Bearer 인증으로 호출해 choices 첫 content를 반환한다`() {
        // given
        wiremock.stubFor(
            WireMock.post("/chat/completions").willReturn(
                WireMock.okJson(
                    """
					{"id":"chatcmpl-1","object":"chat.completion","model":"gpt-4o-mini",
					 "choices":[{"index":0,"message":{"role":"assistant","content":"안녕"},"finish_reason":"stop"}]}
                    """.trimIndent(),
                ),
            ),
        )

        // when
        val content = client.chat("매트릭스 줄거리를 봐줘")

        // then
        assertThat(content).isEqualTo("안녕")
        wiremock.verify(
            1,
            WireMock.postRequestedFor(WireMock.urlEqualTo("/chat/completions"))
                .withHeader("Authorization", WireMock.equalTo("Bearer test-key")),
        )
    }

    @Test
    fun `요청 본문에 모델과 user 프롬프트를 담는다`() {
        // given
        wiremock.stubFor(
            WireMock.post("/chat/completions").willReturn(
                WireMock.okJson("""{"choices":[{"message":{"role":"assistant","content":"ok"}}]}"""),
            ),
        )

        // when
        client.chat("프롬프트")

        // then
        val request = wiremock.findAll(WireMock.postRequestedFor(WireMock.urlEqualTo("/chat/completions"))).single()
        val body = ObjectMapper().readTree(request.bodyAsString)
        assertThat(body["model"].asString()).isEqualTo("gpt-4o-mini")
        assertThat(body["temperature"].asDouble()).isEqualTo(0.2)
        assertThat(body["messages"].size()).isEqualTo(1)
        assertThat(body["messages"][0]["role"].asString()).isEqualTo("user")
        assertThat(body["messages"][0]["content"].asString()).isEqualTo("프롬프트")
    }

    @Test
    fun `choices가 비어 있으면 ExternalApiException이 발생한다`() {
        // given
        wiremock.stubFor(WireMock.post("/chat/completions").willReturn(WireMock.okJson("""{"choices":[]}""")))

        // when
        val exception = assertThrows<ExternalApiException> { client.chat("프롬프트") }

        // then
        assertThat(exception.message).contains("target=llm")
    }

    @Test
    fun `서버가 5xx를 응답하면 ExternalApiException이 발생한다`() {
        // given
        wiremock.stubFor(WireMock.post("/chat/completions").willReturn(WireMock.serverError()))

        // when
        val exception = assertThrows<ExternalApiException> { client.chat("프롬프트") }

        // then
        assertThat(exception.message).contains("target=llm")
    }

    @Test
    fun `API 키가 비어 있으면 IllegalStateException이 발생한다`() {
        // given
        val keylessClient = LlmClient(LlmProperties(baseUrl = wiremock.baseUrl(), apiKey = ""))

        // when & then
        assertThrows<IllegalStateException> { keylessClient.chat("프롬프트") }
        wiremock.verify(0, WireMock.postRequestedFor(WireMock.urlEqualTo("/chat/completions")))
    }
}
