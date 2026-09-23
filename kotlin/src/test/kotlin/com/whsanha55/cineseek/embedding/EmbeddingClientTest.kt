package com.whsanha55.cineseek.embedding

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.whsanha55.cineseek.config.EmbeddingProperties
import kotlin.test.assertEquals
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tools.jackson.databind.ObjectMapper

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
		stubEmbed()

		val result = client.embed(listOf("a", "b"))

		assertEquals(listOf(0.1f, 0.2f), result.single().dense)
		assertEquals(listOf(10L, 20L), result.single().sparse.indices)
		assertEquals(listOf(0.5f, 0.25f), result.single().sparse.values)
		wiremock.verify(1, WireMock.postRequestedFor(WireMock.urlEqualTo("/embed")))
	}

	@Test
	fun `batchSize를 넘으면 나눠 보낸다 — 70개는 64와 6으로 두 번`() {
		stubEmbed()

		client.embed(List(70) { "t$it" })

		val requests = wiremock.findAll(WireMock.postRequestedFor(WireMock.urlEqualTo("/embed")))
		val sizes = requests.map { ObjectMapper().readTree(it.bodyAsString)["texts"].size() }
		assertEquals(listOf(64, 6), sizes)
	}
}
