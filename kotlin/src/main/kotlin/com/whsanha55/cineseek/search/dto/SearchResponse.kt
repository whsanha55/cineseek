package com.whsanha55.cineseek.search.dto

import com.whsanha55.cineseek.movie.dto.GenreResponse
import com.whsanha55.cineseek.movie.dto.PageResponse
import com.whsanha55.cineseek.search.vo.ScoredMovieCard
import com.whsanha55.cineseek.search.vo.SearchItems
import io.swagger.v3.oas.annotations.media.Schema

data class SearchResponse(
    @field:Schema(description = "검색 결과 (점수 내림차순)")
    val items: List<SearchItemResponse>,
    val page: PageResponse,
) {
    companion object {
        fun from(result: SearchItems) = SearchResponse(
            items = result.items.map(SearchItemResponse::from),
            page = PageResponse(limit = result.limit, offset = result.offset, hasNext = result.hasNext),
        )

        /** 유사 영화 — 페이지네이션 없음 */
        fun from(items: List<ScoredMovieCard>, limit: Int) = SearchResponse(
            items = items.map(SearchItemResponse::from),
            page = PageResponse(limit = limit, offset = 0, hasNext = false),
        )
    }
}

/** 카드 필드 + score — 평평하게 내려간다 (MovieCardResponse와 같은 필드) */
data class SearchItemResponse(
    val movieId: Long,
    val title: String,
    val originalTitle: String?,
    val releaseYear: Int?,
    val rating: Double?,
    val voteCount: Int?,
    val posterPath: String?,
    val genres: List<GenreResponse>,
    @field:Schema(description = "검색은 RRF 점수 + 0.1 × log10(1 + 투표 수), 유사 영화는 코사인 유사도", example = "0.62")
    val score: Double,
    @field:Schema(
        description = "쿼리와 줄거리 등 색인 텍스트에 함께 있는 단어 (sparse 기여도 순, 최대 3개). " +
            "비어 있으면 의미(dense)로만 찾은 결과. 유사 영화는 항상 빈 목록",
        example = "[\"탈출\", \"감옥\"]",
    )
    val matchedTerms: List<String>,
) {
    companion object {
        fun from(scored: ScoredMovieCard) = SearchItemResponse(
            movieId = scored.card.movieId,
            title = scored.card.title,
            originalTitle = scored.card.originalTitle,
            releaseYear = scored.card.releaseYear,
            rating = scored.card.rating,
            voteCount = scored.card.voteCount,
            posterPath = scored.card.posterPath,
            genres = scored.card.genres.map(GenreResponse::from),
            score = scored.score,
            matchedTerms = scored.matchedTerms,
        )
    }
}
