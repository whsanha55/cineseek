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
import com.whsanha55.cineseek.movie.vo.WatchProvider
import com.whsanha55.cineseek.movie.vo.WatchProviders
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant
import java.time.LocalDate

/*
 * 영화 도메인 API 응답 — 클래스명이 프론트 openapi-typescript 타입명이 된다 (ui/src/api/types.ts).
 * 아이템은 평평하게 내려간다 — @JsonUnwrapped는 springdoc 스키마 전개를 보장하지 않는다
 */

data class GenreResponse(
    @field:Schema(description = "TMDB 장르 id", example = "28")
    val id: Long,
    val name: String,
) {
    companion object {
        fun from(genre: GenreItem) = GenreResponse(id = genre.genreId, name = genre.name)
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

    @field:Schema(
        description = "URL slug (영어 이름 기반, 예: /people/500-tom-cruise). 영어 이름을 모르면 null",
        example = "christian-bale",
    )
    val slug: String?,

    @field:Schema(description = "필모그래피 수집 상태 (NONE | COLLECTING | COMPLETE)", example = "COMPLETE")
    val filmoState: PersonFilmoStateEnum,

    @field:Schema(description = "필모그래피 마지막 갱신 확인 시각", example = "2026-10-01T00:00:00Z")
    val filmoCheckedAt: Instant?,

    @field:Schema(description = "총 출연 작품 수 (movie_cast 행 수)", example = "47")
    val totalWorks: Long,

    @field:Schema(description = "생년월일", example = "1974-01-30")
    val birthday: LocalDate?,

    @field:Schema(description = "사망일 (생존 중이면 null)")
    val deathday: LocalDate?,

    @field:Schema(description = "출생지 한국어 (기계 번역)", example = "영국 웨일스 하버퍼드웨스트")
    val placeOfBirthKo: String?,

    @field:Schema(description = "출생지 영어 (TMDB 원문)", example = "Haverfordwest, Pembrokeshire, Wales, UK")
    val placeOfBirthEn: String?,

    @field:Schema(description = "소개 한국어 — TMDB 한국어 소개, 없으면 영어 소개의 기계 번역", example = "크리스찬 베일은 영국의 배우다.")
    val biographyKo: String?,

    @field:Schema(description = "biographyKo가 영어 소개의 기계 번역인지", example = "true")
    val biographyKoTranslated: Boolean,

    @field:Schema(description = "소개 영어 (TMDB 원문)", example = "Christian Charles Philip Bale is an English actor.")
    val biographyEn: String?,

    @field:Schema(description = "IMDb id", example = "nm0000288")
    val imdbId: String?,

    @field:Schema(description = "인스타그램 계정", example = "christianbale")
    val instagramId: String?,

    @field:Schema(description = "공식 홈페이지", example = "https://www.example.com")
    val homepage: String?,
) {
    companion object {
        fun from(person: FilmographyPerson) = FilmographyPersonResponse(
            personId = person.personId,
            name = person.name,
            profilePath = person.profilePath,
            slug = person.slug,
            filmoState = person.filmoState,
            filmoCheckedAt = person.filmoCheckedAt,
            totalWorks = person.totalWorks,
            birthday = person.birthday,
            deathday = person.deathday,
            placeOfBirthKo = person.placeOfBirthKo,
            placeOfBirthEn = person.placeOfBirthEn,
            biographyKo = person.biographyKo,
            biographyKoTranslated = person.biographyKoTranslated,
            biographyEn = person.biographyEn,
            imdbId = person.imdbId,
            instagramId = person.instagramId,
            homepage = person.homepage,
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

data class WatchProviderResponse(
    @field:Schema(description = "TMDB 시청처 id", example = "8")
    val providerId: Long,
    @field:Schema(description = "시청처 이름", example = "Netflix")
    val name: String,
    @field:Schema(description = "로고 이미지 경로 (TMDB 이미지 CDN)", example = "/pbpMk2JmcoNnQwx5JGpXngfoWtp.jpg")
    val logoPath: String?,
) {
    companion object {
        fun from(provider: WatchProvider) = WatchProviderResponse(
            providerId = provider.providerId,
            name = provider.name,
            logoPath = provider.logoPath,
        )
    }
}

data class WatchProvidersResponse(
    @field:Schema(description = "TMDB 시청처 안내 페이지 — 출처(JustWatch) 링크로 쓴다")
    val link: String?,
    @field:Schema(description = "정액제 스트리밍")
    val flatrate: List<WatchProviderResponse>,
    @field:Schema(description = "대여")
    val rent: List<WatchProviderResponse>,
    @field:Schema(description = "구매")
    val buy: List<WatchProviderResponse>,
) {
    companion object {
        fun from(providers: WatchProviders) = WatchProvidersResponse(
            link = providers.link,
            flatrate = providers.flatrate.map(WatchProviderResponse::from),
            rent = providers.rent.map(WatchProviderResponse::from),
            buy = providers.buy.map(WatchProviderResponse::from),
        )
    }
}
