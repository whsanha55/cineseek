package com.whsanha55.cineseek.global.config

import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.net.http.HttpClient
import java.time.Duration

/**
 * 외부 HTTP 클라이언트용 공용 빌더 — HTTP/1.1 고정.
 * embed(uvicorn)·TMDB 모두 1.1로 충분하고, JDK 클라이언트의 h2c 업그레이드는
 * WireMock(Jetty) 테스트에서 RST_STREAM을 유발하므로 아예 끊어둔다. timeout은 호출 대상마다 받는다
 */
fun http1RestClient(connectTimeout: Duration, readTimeout: Duration): RestClient.Builder {
    val httpClient = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)
        .connectTimeout(connectTimeout)
        .build()
    val requestFactory = JdkClientHttpRequestFactory(httpClient).apply { setReadTimeout(readTimeout) }
    return RestClient.builder().requestFactory(requestFactory)
}
