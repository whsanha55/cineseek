package com.whsanha55.cineseek.search

import com.ninjasquad.springmockk.MockkBean
import com.whsanha55.cineseek.global.exception.ExternalApiException
import com.whsanha55.cineseek.search.service.SearchService
import com.whsanha55.cineseek.search.vo.SearchResult
import io.mockk.every
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@WebMvcTest(SearchController::class)
class SearchControllerTest {

    @Autowired lateinit var mockMvc: MockMvc

    @MockkBean lateinit var searchService: SearchService

    @Test
    fun `검색하면 결과와 X-Request-Id 헤더를 응답한다`() {
        // given
        every { searchService.search("탈출", "범죄", 2000, 3) } returns
            listOf(SearchResult("쇼생크 탈출", 1994L, 8.7, 0.9f, listOf("범죄"), listOf("프랭크 다라본트")))

        // when
        val result = mockMvc.get("/cineseek/search") {
            param("q", "탈출")
            param("genre", "범죄")
            param("yearMin", "2000")
            param("limit", "3")
            header("X-Request-Id", "req-1")
        }

        // then
        result.andExpect {
            status { isOk() }
            header { string("X-Request-Id", "req-1") }
            jsonPath("$.query") { value("탈출") }
            jsonPath("$.filter.genre") { value("범죄") }
            jsonPath("$.count") { value(1) }
            jsonPath("$.results[0].title") { value("쇼생크 탈출") }
            jsonPath("$.results[0].releaseYear") { value(1994) }
            jsonPath("$.results[0].score") { value(0.9) }
            jsonPath("$.results[0].directors[0]") { value("프랭크 다라본트") }
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
        every { searchService.search(any<String>(), any(), any(), any()) } throws IllegalStateException("qdrant down")

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
        every { searchService.search(any<String>(), any(), any(), any()) } throws ExternalApiException("qdrant")

        // when
        val result = mockMvc.get("/cineseek/search") { param("q", "탈출") }

        // then
        result.andExpect {
            status { isServiceUnavailable() }
            jsonPath("$.code") { value("EXTERNAL_API_ERROR") }
        }
    }
}
