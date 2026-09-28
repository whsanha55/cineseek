package com.whsanha55.cineseek

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class CineseekApplicationTests {

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:17")
    }

    @Autowired lateinit var mockMvc: MockMvc

    @Test
    fun `애플리케이션 컨텍스트가 뜬다`() {
        // 컨텍스트 로딩 실패 시 테스트가 실패한다 — 별도 검증 없음
    }

    @Test
    fun `OpenAPI 문서에 검색 API와 쿼리 파라미터가 나온다`() {
        // when
        val result = mockMvc.get("/v3/api-docs")

        // then
        result.andExpect {
            status { isOk() }
            jsonPath("$.paths['/cineseek/search'].get.summary") { value("자연어로 영화 검색") }
            jsonPath("$.paths['/cineseek/search'].get.parameters[?(@.name == 'q')].required") { value(true) }
        }
    }
}
