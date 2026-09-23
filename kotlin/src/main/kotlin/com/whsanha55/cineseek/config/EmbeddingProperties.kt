package com.whsanha55.cineseek.config

import org.springframework.boot.context.properties.ConfigurationProperties

/** cineseek.embedding — /embed 서버(python) 접속 설정 */
@ConfigurationProperties("cineseek.embedding")
data class EmbeddingProperties(
	val baseUrl: String = "http://localhost:8001",
	val batchSize: Int = 64, // 계약: 1회 최대 64개
)
