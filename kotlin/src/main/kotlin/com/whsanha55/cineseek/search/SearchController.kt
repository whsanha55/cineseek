package com.whsanha55.cineseek.search

import com.whsanha55.cineseek.search.dto.SearchRequest
import com.whsanha55.cineseek.search.dto.SearchResponse
import com.whsanha55.cineseek.search.dto.SimilarRequest
import com.whsanha55.cineseek.search.facade.SearchFacade
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springdoc.core.annotations.ParameterObject
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

@Tag(name = "검색", description = "줄거리 의미 검색 (dense + sparse 하이브리드) · 유사 영화")
@RestController
class SearchController(private val searchFacade: SearchFacade) {

    @Operation(summary = "자연어로 영화 검색")
    @GetMapping("/cineseek/search")
    fun search(@Valid @ParameterObject @ModelAttribute request: SearchRequest): SearchResponse =
        SearchResponse.from(searchFacade.search(request.q, request.toFilter()))

    @Operation(summary = "줄거리가 비슷한 영화")
    @GetMapping("/cineseek/movies/{id}/similar")
    fun similar(
        @PathVariable id: Long,
        @Valid @ParameterObject @ModelAttribute request: SimilarRequest,
    ): SearchResponse = SearchResponse.from(searchFacade.similar(id, request.limit), request.limit)
}
