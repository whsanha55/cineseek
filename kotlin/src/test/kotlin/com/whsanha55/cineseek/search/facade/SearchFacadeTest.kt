package com.whsanha55.cineseek.search.facade

import com.whsanha55.cineseek.movie.service.MovieQueryService
import com.whsanha55.cineseek.movie.vo.MovieCard
import com.whsanha55.cineseek.search.service.SearchService
import com.whsanha55.cineseek.search.vo.SearchFilter
import com.whsanha55.cineseek.search.vo.SearchHit
import com.whsanha55.cineseek.search.vo.SearchPage
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SearchFacadeTest {

    private val searchService = mockk<SearchService>()
    private val movieQueryService = mockk<MovieQueryService>()
    private val facade = SearchFacade(searchService, movieQueryService)

    @Test
    fun `Qdrant 순서를 유지해 카드로 조립하고 PG에 없는 id는 스킵한다`() {
        // given
        val filter = SearchFilter(limit = 3)
        every { searchService.search("쿼리", filter) } returns SearchPage(
            hits = listOf(SearchHit(2L, 0.9f), SearchHit(999L, 0.8f), SearchHit(1L, 0.7f)),
            limit = 3,
            offset = 0,
            hasNext = true,
        )
        every { movieQueryService.cards(listOf(2L, 999L, 1L)) } returns mapOf(1L to card(1L), 2L to card(2L))

        // when
        val result = facade.search("쿼리", filter)

        // then
        assertThat(result.items.map { it.card.movieId }).containsExactly(2L, 1L)
        assertThat(result.items.first().score).isEqualTo(0.9f.toDouble())
        assertThat(result.hasNext).isTrue()
    }

    private fun card(id: Long) = MovieCard(id, "영화$id", null, 2000, null, null, null, emptyList())
}
