package com.whsanha55.cineseek.search.facade

import com.whsanha55.cineseek.movie.service.MovieQueryService
import com.whsanha55.cineseek.search.service.SearchService
import com.whsanha55.cineseek.search.vo.Embedding
import com.whsanha55.cineseek.search.vo.ScoredMovieCard
import com.whsanha55.cineseek.search.vo.SearchFilter
import com.whsanha55.cineseek.search.vo.SearchHit
import com.whsanha55.cineseek.search.vo.SearchItems
import com.whsanha55.cineseek.search.vo.SearchPage
import org.springframework.stereotype.Component

/** Qdrant 검색(movie_id, score) + PG 카드 조립. Qdrant에만 있고 PG에 없는 포인트는 스킵한다 */
@Component
class SearchFacade(private val searchService: SearchService, private val movieQueryService: MovieQueryService) {

    fun search(query: String, filter: SearchFilter): SearchItems = assemble(searchService.search(query, filter))

    /** EvalRunner용 — 이미 배치로 만든 임베딩을 재사용한다 */
    fun search(embedding: Embedding, filter: SearchFilter): SearchItems =
        assemble(searchService.search(embedding, filter))

    fun similar(movieId: Long, limit: Int): List<ScoredMovieCard> = toCards(searchService.similar(movieId, limit))

    private fun assemble(page: SearchPage) = SearchItems(
        items = toCards(page.hits),
        limit = page.limit,
        offset = page.offset,
        hasNext = page.hasNext,
    )

    private fun toCards(hits: List<SearchHit>): List<ScoredMovieCard> {
        val cards = movieQueryService.cards(hits.map { it.movieId })
        return hits.mapNotNull { hit -> cards[hit.movieId]?.let { ScoredMovieCard(it, hit.score.toDouble()) } }
    }
}
