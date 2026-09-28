package com.whsanha55.cineseek.external.embedding.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/** cineseek.embedding — /embed 서버(python) 접속 설정 */
@ConfigurationProperties("cineseek.embedding")
data class EmbeddingProperties(
    val baseUrl: String = "http://localhost:8001",
    val batchSize: Int = 64, // 계약: 1회 최대 64개
    val connectTimeout: Duration = DEFAULT_CONNECT_TIMEOUT,
    val readTimeout: Duration = DEFAULT_READ_TIMEOUT,
) {
    companion object {
        private val DEFAULT_CONNECT_TIMEOUT: Duration = Duration.ofSeconds(3)

        /** CPU bge-m3로 64개를 임베딩하는 시간을 감안 */
        private val DEFAULT_READ_TIMEOUT: Duration = Duration.ofSeconds(120)
    }
}
