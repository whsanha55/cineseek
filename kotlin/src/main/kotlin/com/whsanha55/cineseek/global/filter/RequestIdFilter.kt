package com.whsanha55.cineseek.global.filter

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

/** X-Request-Id를 이어받거나 새로 만들어 MDC와 응답 헤더에 넣는다 */
@Component
class RequestIdFilter : OncePerRequestFilter() {

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val requestId = request.getHeader(REQUEST_ID_HEADER)?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        MDC.put(REQUEST_ID, requestId)
        response.setHeader(REQUEST_ID_HEADER, requestId)
        try {
            chain.doFilter(request, response)
        } finally {
            MDC.remove(REQUEST_ID)
        }
    }

    companion object {
        const val REQUEST_ID = "requestId"
        const val REQUEST_ID_HEADER = "X-Request-Id"
    }
}
