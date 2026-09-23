package com.whsanha55.cineseek.client

import com.whsanha55.cineseek.config.EmbeddingProperties
import com.whsanha55.cineseek.config.http1RestClient
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

/** dense(1024, L2 정규화) + sparse(토큰 id → 가중치) */
data class Embedding(
	val dense: List<Float>,
	val sparse: Sparse,
) {
	data class Sparse(
		val indices: List<Long>,
		val values: List<Float>,
	)
}

/**
 * POST /embed 클라이언트 — 계약상 1회 최대 64개라 batchSize 단위로 나눠 보낸다.
 * 계약 문서: python/README.md
 */
@Component
class EmbeddingClient(properties: EmbeddingProperties) {
	private val batchSize = properties.batchSize

	// embed 서버는 uvicorn(HTTP/1.1) — h2c 업그레이드 시도를 끊고 1.1로 고정한다
	private val restClient = http1RestClient()
		.baseUrl(properties.baseUrl)
		.build()

	fun embed(texts: List<String>): List<Embedding> {
		require(texts.isNotEmpty()) { "texts는 1개 이상이어야 한다" }
		return texts.chunked(batchSize).flatMap { batch ->
			val response = restClient.post()
				.uri("/embed")
				.body(EmbedRequestBody(batch))
				.retrieve()
				.body(EmbedResponseBody::class.java)
				?: error("/embed 응답 본문이 없다")
			response.items.map { Embedding(it.dense, Embedding.Sparse(it.sparse.indices, it.sparse.values)) }
		}
	}
}

// 와이어 계약 DTO — 이 파일 안에서만 쓴다
private data class EmbedRequestBody(val texts: List<String>)

private data class EmbedResponseBody(
	val model: String,
	val items: List<Item>,
) {
	data class Item(
		val dense: List<Float>,
		val sparse: Sparse,
	)

	data class Sparse(
		val indices: List<Long>,
		val values: List<Float>,
	)
}
