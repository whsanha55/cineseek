package com.whsanha55.cineseek.search

import com.whsanha55.cineseek.search.dto.SearchFilter
import com.whsanha55.cineseek.search.dto.SearchResponse
import com.whsanha55.cineseek.search.service.SearchService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

/** GET /api/search — python /search와 같은 응답 구조 (필드명은 camelCase) */
@RestController
class SearchController(private val searchService: SearchService) {

    @GetMapping("/api/search")
    fun search(
        @RequestParam("q") q: String,
        @RequestParam("genre") genre: String? = null,
        @RequestParam("yearMin") yearMin: Int? = null,
        @RequestParam("limit") limit: Int = 5,
    ): SearchResponse {
        if (limit !in 1..50) {
            throw ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "limit은 1~50이어야 한다")
        }
        val results = searchService.search(q, genre, yearMin, limit)
        return SearchResponse(
            query = q,
            filter = SearchFilter(genre, yearMin),
            count = results.size,
            results = results,
        )
    }
}
