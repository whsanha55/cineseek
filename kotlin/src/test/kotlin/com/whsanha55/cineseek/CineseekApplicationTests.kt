package com.whsanha55.cineseek

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest
@Testcontainers
class CineseekApplicationTests {

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:17")
    }

    @Test
    fun `애플리케이션 컨텍스트가 뜬다`() {
        // 컨텍스트 로딩 실패 시 테스트가 실패한다 — 별도 검증 없음
    }
}
