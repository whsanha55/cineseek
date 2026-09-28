package com.whsanha55.cineseek.external.qdrant.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/** cineseek.qdrant — java 클라이언트는 gRPC(6334)만 제공한다 */
@ConfigurationProperties("cineseek.qdrant")
data class QdrantProperties(
    val grpcUrl: String = "localhost:6334",
    val collection: String = "movies",
    val timeout: Duration = DEFAULT_TIMEOUT,
) {
    companion object {
        /** gRPC 호출 1건 기준 */
        private val DEFAULT_TIMEOUT: Duration = Duration.ofSeconds(30)
    }
}
