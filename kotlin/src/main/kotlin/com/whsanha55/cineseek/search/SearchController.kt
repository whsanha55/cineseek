package com.whsanha55.cineseek.search

import com.whsanha55.cineseek.search.dto.SearchRequest
import com.whsanha55.cineseek.search.dto.SearchResponse
import com.whsanha55.cineseek.search.service.SearchService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springdoc.core.annotations.ParameterObject
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.RestController

/** GET /cineseek/search — python /search와 같은 응답 구조 (필드명은 camelCase) */
@Tag(name = "검색", description = "줄거리 의미 검색 (dense + sparse 하이브리드)")
@RestController
class SearchController(private val searchService: SearchService) {

    @Operation(summary = "자연어로 영화 검색")
    @GetMapping("/cineseek/search")
    fun search(@Valid @ParameterObject @ModelAttribute request: SearchRequest): SearchResponse {
        val results = searchService.search(request.q, request.genre, request.yearMin, request.limit)
        return SearchResponse.from(request, results)
    }
}
