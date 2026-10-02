package com.whsanha55.cineseek.search

import com.ninjasquad.springmockk.MockkBean
import com.whsanha55.cineseek.global.exception.ExternalApiException
import com.whsanha55.cineseek.movie.vo.GenreItem
import com.whsanha55.cineseek.movie.vo.MovieCard
import com.whsanha55.cineseek.search.facade.SearchFacade
import com.whsanha55.cineseek.search.vo.ScoredMovieCard
import com.whsanha55.cineseek.search.vo.SearchFilter
import com.whsanha55.cineseek.search.vo.SearchItems
import io.mockk.every
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@WebMvcTest(SearchController::class)
class SearchControllerTest {

    @Autowired lateinit var mockMvc: MockMvc

    @MockkBean lateinit var searchFacade: SearchFacade

    @Test
    fun `검색하면 카드와 페이지 메타, X-Request-Id 헤더를 응답한다`() {
        // given
        val filter = SearchFilter(genreIds = listOf(80L, 18L), yearMin = 2000, limit = 3)
        every { searchFacade.search("탈출", filter) } returns
            SearchItems(listOf(ScoredMovieCard(card(), 0.032)), limit = 3, offset = 0, hasNext = true)

        // when
        val result = mockMvc.get("/cineseek/search") {
            param("q", "탈출")
            param("genreId", "80", "18")
            param("yearMin", "2000")
            param("limit", "3")
            header("X-Request-Id", "req-1")
        }

        // then
        result.andExpect {
            status { isOk() }
            header { string("X-Request-Id", "req-1") }
            jsonPath("$.items[0].movieId") { value(1) }
            jsonPath("$.items[0].title") { value("쇼생크 탈출") }
            jsonPath("$.items[0].originalTitle") { value(nullValue()) }
            jsonPath("$.items[0].genres[0].id") { value(80) }
            jsonPath("$.items[0].genres[0].nameKo") { value("범죄") }
            jsonPath("$.items[0].score") { value(0.032) }
            jsonPath("$.page.limit") { value(3) }
            jsonPath("$.page.hasNext") { value(true) }
            jsonPath("$.page.total") { value(nullValue()) }
        }
    }

    @Test
    fun `runtime과 voteCount 파라미터가 필터로 전달된다`() {
        // given
        val filter = SearchFilter(runtimeMin = 90, runtimeMax = 180, voteCountMin = 50, voteCountMax = 500)
        every { searchFacade.search("다큐멘터리", filter) } returns
            SearchItems(listOf(ScoredMovieCard(card(), 0.021)), limit = 20, offset = 0, hasNext = false)

        // when
        val result = mockMvc.get("/cineseek/search") {
            param("q", "다큐멘터리")
            param("runtimeMin", "90")
            param("runtimeMax", "180")
            param("voteCountMin", "50")
            param("voteCountMax", "500")
        }

        // then
        result.andExpect {
            status { isOk() }
            jsonPath("$.items[0].movieId") { value(1) }
            jsonPath("$.page.hasNext") { value(false) }
        }
    }

    @Test
    fun `유사 영화는 페이지네이션 없이 응답한다`() {
        // given
        every { searchFacade.similar(1L, 8) } returns listOf(ScoredMovieCard(card(), 0.95))

        // when
        val result = mockMvc.get("/cineseek/movies/1/similar")

        // then
        result.andExpect {
            status { isOk() }
            jsonPath("$.items[0].score") { value(0.95) }
            jsonPath("$.page.hasNext") { value(false) }
        }
    }

    @Test
    fun `limit이 범위를 벗어나면 400과 필드 오류를 ProblemDetail로 응답한다`() {
        // when
        val result = mockMvc.get("/cineseek/search") {
            param("q", "탈출")
            param("limit", "51")
            header("X-Request-Id", "req-2")
        }

        // then
        result.andExpect {
            status { isBadRequest() }
            content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.code") { value("INVALID_REQUEST") }
            jsonPath("$.requestId") { value("req-2") }
            jsonPath("$.errors[0].field") { value("limit") }
        }
    }

    @Test
    fun `q가 없으면 400으로 응답한다`() {
        // when
        val result = mockMvc.get("/cineseek/search")

        // then
        result.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INVALID_REQUEST") }
        }
    }

    @Test
    fun `예상하지 못한 예외는 500 INTERNAL_ERROR로 응답하고 내부 메시지를 숨긴다`() {
        // given
        every { searchFacade.search(any<String>(), any()) } throws IllegalStateException("qdrant down")

        // when
        val result = mockMvc.get("/cineseek/search") { param("q", "탈출") }

        // then
        result.andExpect {
            status { isInternalServerError() }
            jsonPath("$.code") { value("INTERNAL_ERROR") }
            jsonPath("$.detail") { value("잠시 후 다시 시도해주세요.") }
        }
    }

    @Test
    fun `외부 API 장애는 503 EXTERNAL_API_ERROR로 응답한다`() {
        // given
        every { searchFacade.search(any<String>(), any()) } throws ExternalApiException("qdrant")

        // when
        val result = mockMvc.get("/cineseek/search") { param("q", "탈출") }

        // then
        result.andExpect {
            status { isServiceUnavailable() }
            jsonPath("$.code") { value("EXTERNAL_API_ERROR") }
        }
    }

    private fun card() = MovieCard(
        movieId = 1L,
        title = "쇼생크 탈출",
        originalTitle = null,
        releaseYear = 1994,
        rating = 8.7,
        voteCount = 27000,
        posterPath = "/example.jpg",
        genres = listOf(GenreItem(80L, "Crime", "범죄")),
    )
}
