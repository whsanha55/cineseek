package com.whsanha55.cineseek.movie.dto

import com.whsanha55.cineseek.movie.vo.CastMember
import com.whsanha55.cineseek.movie.vo.GenreItem
import com.whsanha55.cineseek.movie.vo.MovieCard
import com.whsanha55.cineseek.movie.vo.MovieDetail
import com.whsanha55.cineseek.movie.vo.MoviePage
import com.whsanha55.cineseek.movie.vo.PersonItem
import io.swagger.v3.oas.annotations.media.Schema

/*
 * 영화 도메인 API 응답 — 클래스명이 프론트 openapi-typescript 타입명이 된다 (ui/src/api/types.ts).
 * 아이템은 평평하게 내려간다 — @JsonUnwrapped는 springdoc 스키마 전개를 보장하지 않는다
 */

data class GenreResponse(
    @field:Schema(description = "TMDB 장르 id", example = "28")
    val id: Long,
    val name: String,
    val nameKo: String?,
) {
    companion object {
        fun from(genre: GenreItem) = GenreResponse(id = genre.genreId, name = genre.name, nameKo = genre.nameKo)
    }
}

data class MovieCardResponse(
    val movieId: Long,
    val title: String,
    val originalTitle: String?,
    val releaseYear: Int?,
    @field:Schema(description = "TMDB 평점 (0~10)", example = "8.7")
    val rating: Double?,
    val voteCount: Int?,
    val posterPath: String?,
    val genres: List<GenreResponse>,
) {
    companion object {
        fun from(card: MovieCard) = MovieCardResponse(
            movieId = card.movieId,
            title = card.title,
            originalTitle = card.originalTitle,
            releaseYear = card.releaseYear,
            rating = card.rating,
            voteCount = card.voteCount,
            posterPath = card.posterPath,
            genres = card.genres.map(GenreResponse::from),
        )
    }
}

data class PageResponse(
    val limit: Int,
    val offset: Int,
    val hasNext: Boolean,
    @field:Schema(description = "미제공 — 하이브리드 검색 전체 count 비용")
    val total: Long? = null,
)

data class MovieListResponse(val items: List<MovieCardResponse>, val page: PageResponse) {
    companion object {
        fun from(page: MoviePage) = MovieListResponse(
            items = page.items.map(MovieCardResponse::from),
            page = PageResponse(limit = page.limit, offset = page.offset, hasNext = page.hasNext),
        )
    }
}

data class PersonResponse(val personId: Long, val name: String)

data class CastMemberResponse(val personId: Long, val name: String, val character: String?) {
    companion object {
        fun from(member: CastMember) = CastMemberResponse(member.personId, member.name, member.character)
    }
}

data class MovieDetailResponse(
    val movieId: Long,
    val title: String,
    val originalTitle: String?,
    val releaseYear: Int?,
    val runtime: Int?,
    val rating: Double?,
    val voteCount: Int?,
    val posterPath: String?,
    val overview: String?,
    val genres: List<GenreResponse>,
    val directors: List<PersonResponse>,
    val cast: List<CastMemberResponse>,
) {
    companion object {
        fun from(detail: MovieDetail) = MovieDetailResponse(
            movieId = detail.card.movieId,
            title = detail.card.title,
            originalTitle = detail.card.originalTitle,
            releaseYear = detail.card.releaseYear,
            runtime = detail.runtime,
            rating = detail.card.rating,
            voteCount = detail.card.voteCount,
            posterPath = detail.card.posterPath,
            overview = detail.overview,
            genres = detail.card.genres.map(GenreResponse::from),
            directors = detail.directors.map { PersonResponse(it.personId, it.name) },
            cast = detail.cast.map(CastMemberResponse::from),
        )
    }
}

data class GenresResponse(val items: List<GenreResponse>) {
    companion object {
        fun from(genres: List<GenreItem>) = GenresResponse(items = genres.map(GenreResponse::from))
    }
}

data class PersonItemResponse(val personId: Long, val name: String, val role: String, val knownFor: List<String>) {
    companion object {
        fun from(person: PersonItem, role: String) = PersonItemResponse(
            personId = person.personId,
            name = person.name,
            role = role,
            knownFor = person.knownFor,
        )
    }
}

data class PeopleResponse(val items: List<PersonItemResponse>) {
    companion object {
        fun from(people: List<PersonItem>, role: String) =
            PeopleResponse(items = people.map { PersonItemResponse.from(it, role) })
    }
}
