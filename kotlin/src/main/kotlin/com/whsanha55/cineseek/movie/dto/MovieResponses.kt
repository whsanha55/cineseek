package com.whsanha55.cineseek.movie.dto

import com.whsanha55.cineseek.movie.enums.PersonFilmoStateEnum
import com.whsanha55.cineseek.movie.vo.CastMember
import com.whsanha55.cineseek.movie.vo.FilmographyItem
import com.whsanha55.cineseek.movie.vo.FilmographyPage
import com.whsanha55.cineseek.movie.vo.FilmographyPerson
import com.whsanha55.cineseek.movie.vo.GenreItem
import com.whsanha55.cineseek.movie.vo.MovieCard
import com.whsanha55.cineseek.movie.vo.MovieDetail
import com.whsanha55.cineseek.movie.vo.MoviePage
import com.whsanha55.cineseek.movie.vo.PersonItem
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

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

data class FilmographyPersonResponse(
    @field:Schema(description = "TMDB person id", example = "3895")
    val personId: Long,

    @field:Schema(description = "이름", example = "크리스찬 베일")
    val name: String,

    @field:Schema(description = "TMDB 프로필 이미지 경로", example = "/fuTEtOeIwXiqtVpIoL0GyyWcnxh.jpg")
    val profilePath: String?,

    @field:Schema(description = "필모그래피 수집 상태 (NONE | COLLECTING | COMPLETE)", example = "COMPLETE")
    val filmoState: PersonFilmoStateEnum,

    @field:Schema(description = "필모그래피 마지막 갱신 확인 시각", example = "2026-10-01T00:00:00Z")
    val filmoCheckedAt: Instant?,

    @field:Schema(description = "총 출연 작품 수 (movie_cast 행 수)", example = "47")
    val totalWorks: Long,
) {
    companion object {
        fun from(person: FilmographyPerson) = FilmographyPersonResponse(
            personId = person.personId,
            name = person.name,
            profilePath = person.profilePath,
            filmoState = person.filmoState,
            filmoCheckedAt = person.filmoCheckedAt,
            totalWorks = person.totalWorks,
        )
    }
}

/** 필모그래피 아이템 — 카드 필드에 해당 배우의 배역을 평평하게 함께 내린다 */
data class FilmographyItemResponse(
    @field:Schema(description = "영화 id", example = "155")
    val movieId: Long,

    @field:Schema(description = "제목", example = "다크 나이트")
    val title: String,

    @field:Schema(description = "원제", example = "The Dark Knight")
    val originalTitle: String?,

    @field:Schema(description = "개봉 연도", example = "2008")
    val releaseYear: Int?,

    @field:Schema(description = "TMDB 평점 (0~10)", example = "8.5")
    val rating: Double?,

    @field:Schema(description = "TMDB 투표 수", example = "30000")
    val voteCount: Int?,

    @field:Schema(description = "포스터 이미지 경로", example = "/qJ2tW6WMUDux911r6m7haRef0WH.jpg")
    val posterPath: String?,

    @field:Schema(description = "장르 목록")
    val genres: List<GenreResponse>,

    @field:Schema(description = "해당 배우의 배역명", example = "브루스 웨인")
    val character: String?,
) {
    companion object {
        fun from(item: FilmographyItem) = FilmographyItemResponse(
            movieId = item.card.movieId,
            title = item.card.title,
            originalTitle = item.card.originalTitle,
            releaseYear = item.card.releaseYear,
            rating = item.card.rating,
            voteCount = item.card.voteCount,
            posterPath = item.card.posterPath,
            genres = item.card.genres.map(GenreResponse::from),
            character = item.character,
        )
    }
}

data class FilmographyResponse(
    val person: FilmographyPersonResponse,
    val items: List<FilmographyItemResponse>,
    val page: PageResponse,

    @field:Schema(description = "필모그래피 백그라운드 갱신이 대기/실행 중인지 — true면 잠시 후 다시 조회해 갱신된 목록을 받는다", example = "false")
    val collecting: Boolean,
) {
    companion object {
        fun from(filmography: FilmographyPage) = FilmographyResponse(
            person = FilmographyPersonResponse.from(filmography.person),
            items = filmography.items.map(FilmographyItemResponse::from),
            page = PageResponse(
                limit = filmography.limit,
                offset = filmography.offset,
                hasNext = filmography.hasNext,
            ),
            collecting = filmography.collecting,
        )
    }
}
