package com.whsanha55.cineseek.external.embedding.client

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.whsanha55.cineseek.external.embedding.config.EmbeddingProperties
import com.whsanha55.cineseek.global.exception.ExternalApiException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tools.jackson.databind.ObjectMapper
import java.time.Duration

/** /embed 계약 테스트(WireMock 스텁) — 계약 문서: python/README.md */
class EmbeddingClientTest {

    private val wiremock = WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort())
    private lateinit var client: EmbeddingClient

    @BeforeEach
    fun setUp() {
        wiremock.start()
        client = EmbeddingClient(EmbeddingProperties(baseUrl = wiremock.baseUrl(), batchSize = 64))
    }

    @AfterEach
    fun tearDown() {
        wiremock.stop()
    }

    private fun stubEmbed() {
        wiremock.stubFor(
            WireMock.post("/embed").willReturn(
                WireMock.okJson(
                    """
					{"model":"BAAI/bge-m3","items":[
						{"dense":[0.1,0.2],"sparse":{"indices":[10,20],"values":[0.5,0.25]}}
					]}
                    """.trimIndent(),
                ),
            ),
        )
    }

    @Test
    fun `텍스트 2개를 한 번의 호출로 임베딩한다`() {
        // given
        stubEmbed()

        // when
        val result = client.embed(listOf("a", "b"))

        // then
        val embedding = result.single()
        assertThat(embedding.dense).containsExactly(0.1f, 0.2f)
        assertThat(embedding.sparse.indices).containsExactly(10L, 20L)
        assertThat(embedding.sparse.values).containsExactly(0.5f, 0.25f)
        wiremock.verify(1, WireMock.postRequestedFor(WireMock.urlEqualTo("/embed")))
    }

    @Test
    fun `batchSize를 넘으면 나눠 보낸다 — 70개는 64와 6으로 두 번`() {
        // given
        stubEmbed()

        // when
        client.embed(List(70) { "t$it" })

        // then
        val requests = wiremock.findAll(WireMock.postRequestedFor(WireMock.urlEqualTo("/embed")))
        val sizes = requests.map { ObjectMapper().readTree(it.bodyAsString)["texts"].size() }
        assertThat(sizes).containsExactly(64, 6)
    }

    @Test
    fun `embed 서버가 5xx를 응답하면 ExternalApiException이 발생한다`() {
        // given
        wiremock.stubFor(WireMock.post("/embed").willReturn(WireMock.serverError()))

        // when
        val exception = assertThrows<ExternalApiException> { client.embed(listOf("a")) }

        // then
        assertThat(exception.message).contains("target=embedding")
    }

    @Test
    fun `응답이 readTimeout보다 늦으면 ExternalApiException이 발생한다`() {
        // given
        val slowClient = EmbeddingClient(
            EmbeddingProperties(baseUrl = wiremock.baseUrl(), readTimeout = Duration.ofMillis(200)),
        )
        wiremock.stubFor(
            WireMock.post("/embed").willReturn(WireMock.okJson("""{"model":"m","items":[]}""").withFixedDelay(1_000)),
        )

        // when & then
        assertThrows<ExternalApiException> { slowClient.embed(listOf("a")) }
    }
}
