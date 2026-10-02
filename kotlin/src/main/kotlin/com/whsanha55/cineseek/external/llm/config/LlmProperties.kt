package com.whsanha55.cineseek.external.llm.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/** cineseek.llm — OpenAI 호환 /chat/completions 서버 접속 설정. apiKey가 비어 있으면 분위기 태깅 배치를 건너뛴다 */
@ConfigurationProperties("cineseek.llm")
data class LlmProperties(
    val baseUrl: String = "https://api.openai.com/v1",
    val apiKey: String = "",
    val model: String = "gpt-4o-mini",
    val temperature: Double = 0.2,
    val connectTimeout: Duration = DEFAULT_CONNECT_TIMEOUT,
    val readTimeout: Duration = DEFAULT_READ_TIMEOUT,
) {
    companion object {
        private val DEFAULT_CONNECT_TIMEOUT: Duration = Duration.ofSeconds(5)

        /** LLM 생성이 수 초에서 수십 초까지 걸린다 */
        private val DEFAULT_READ_TIMEOUT: Duration = Duration.ofSeconds(60)
    }
}
