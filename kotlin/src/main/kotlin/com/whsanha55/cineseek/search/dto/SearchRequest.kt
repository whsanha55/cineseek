package com.whsanha55.cineseek.search.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank

/** GET /cineseek/search 쿼리 파라미터 */
data class SearchRequest(
    @field:Schema(description = "검색어 (줄거리 묘사)", example = "감옥에서 탈출하는 이야기")
    @field:NotBlank
    val q: String,

    @field:Schema(description = "장르 필터 (한국어 장르명)", example = "범죄")
    val genre: String? = null,

    @field:Schema(description = "개봉 연도 하한", example = "2000")
    val yearMin: Int? = null,

    @field:Schema(description = "결과 수 (1~50)", example = "5")
    @field:Min(1)
    @field:Max(MAX_LIMIT)
    val limit: Int = DEFAULT_LIMIT,
) {
    companion object {
        const val DEFAULT_LIMIT = 5
        const val MAX_LIMIT = 50L
    }
}
