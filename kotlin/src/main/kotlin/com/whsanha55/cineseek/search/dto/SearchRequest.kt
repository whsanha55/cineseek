package com.whsanha55.cineseek.search.dto

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank

/** GET /cineseek/search 쿼리 파라미터 */
data class SearchRequest(
    @field:NotBlank
    val q: String,
    val genre: String? = null,
    val yearMin: Int? = null,
    @field:Min(1)
    @field:Max(MAX_LIMIT)
    val limit: Int = DEFAULT_LIMIT,
) {
    companion object {
        const val DEFAULT_LIMIT = 5
        const val MAX_LIMIT = 50L
    }
}
