package com.whsanha55.cineseek.search

import com.whsanha55.cineseek.search.dto.SearchFilter
import com.whsanha55.cineseek.search.dto.SearchRequest
import com.whsanha55.cineseek.search.dto.SearchResponse
import com.whsanha55.cineseek.search.service.SearchService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.RestController

/** GET /api/search — python /search와 같은 응답 구조 (필드명은 camelCase) */
@RestController
class SearchController(private val searchService: SearchService) {

    @GetMapping("/api/search")
    fun search(@Valid @ModelAttribute request: SearchRequest): SearchResponse {
        val results = searchService.search(request.q, request.genre, request.yearMin, request.limit)
        return SearchResponse(
            query = request.q,
            filter = SearchFilter(request.genre, request.yearMin),
            count = results.size,
            results = results,
        )
    }
}
