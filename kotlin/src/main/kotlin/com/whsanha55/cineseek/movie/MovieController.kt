package com.whsanha55.cineseek.movie

import com.whsanha55.cineseek.movie.dto.GenresResponse
import com.whsanha55.cineseek.movie.dto.MovieDetailResponse
import com.whsanha55.cineseek.movie.dto.MovieExploreRequest
import com.whsanha55.cineseek.movie.dto.MovieListResponse
import com.whsanha55.cineseek.movie.dto.PeopleRequest
import com.whsanha55.cineseek.movie.dto.PeopleResponse
import com.whsanha55.cineseek.movie.dto.PersonItemResponse
import com.whsanha55.cineseek.movie.dto.PersonRequest
import com.whsanha55.cineseek.movie.service.MovieQueryService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springdoc.core.annotations.ParameterObject
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

@Tag(name = "영화", description = "상세 · 탐색 · 장르 · 인물 조회 (PG 기준)")
@RestController
class MovieController(private val movieQueryService: MovieQueryService) {

    @Operation(summary = "영화 상세")
    @GetMapping("/cineseek/movies/{id}")
    fun detail(@PathVariable id: Long): MovieDetailResponse = MovieDetailResponse.from(movieQueryService.detail(id))

    @Operation(summary = "장르·인물 조건으로 영화 탐색")
    @GetMapping("/cineseek/movies")
    fun explore(@Valid @ParameterObject @ModelAttribute request: MovieExploreRequest): MovieListResponse =
        MovieListResponse.from(
            movieQueryService.explore(
                genreId = request.genreId,
                sort = request.sortEnum(),
                offset = request.page * request.limit,
                limit = request.limit,
                directorId = request.directorId,
                castId = request.castId,
            ),
        )

    @Operation(summary = "장르 목록")
    @GetMapping("/cineseek/genres")
    fun genres(): GenresResponse = GenresResponse.from(movieQueryService.genres())

    @Operation(summary = "감독·배우 자동완성")
    @GetMapping("/cineseek/people")
    fun people(@Valid @ParameterObject @ModelAttribute request: PeopleRequest): PeopleResponse =
        PeopleResponse.from(movieQueryService.people(request.q, request.role), request.role)

    @Operation(summary = "인물 이름 조회 (필터 칩 라벨 복원용)")
    @GetMapping("/cineseek/people/{id}")
    fun person(
        @PathVariable id: Long,
        @Valid @ParameterObject @ModelAttribute request: PersonRequest,
    ): PersonItemResponse = PersonItemResponse.from(movieQueryService.person(id, request.role), request.role)
}
