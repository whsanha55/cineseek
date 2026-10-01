package com.whsanha55.cineseek.external.tmdb.client

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.whsanha55.cineseek.external.tmdb.config.TmdbProperties
import com.whsanha55.cineseek.global.exception.ExternalApiException
import com.whsanha55.cineseek.movie.vo.TmdbGenre
import com.whsanha55.cineseek.movie.vo.TmdbPerson
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** TMDB 클라이언트 테스트 — WireMock 스텁 (discover 조기 종료, detail+credits 매핑) */
class TmdbClientTest {

    private val wiremock = WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort())
    private lateinit var client: TmdbClient

    @BeforeEach
    fun setUp() {
        wiremock.start()
        client = TmdbClient(
            TmdbProperties(baseUrl = wiremock.baseUrl(), accessToken = "test-token", language = "ko-KR", pages = 50),
        )
    }

    @AfterEach
    fun tearDown() {
        wiremock.stop()
    }

    @Test
    fun `수집 기준별로 discover를 순회해 합치고 빈 페이지에서 조기 종료한다`() {
        // given
        wiremock.stubFor(
            WireMock.get(WireMock.urlPathEqualTo("/discover/movie")).withQueryParam("page", WireMock.equalTo("1"))
                .willReturn(WireMock.okJson("""{"results":[{"id":155},{"id":27205}]}""")),
        )
        wiremock.stubFor(
            WireMock.get(WireMock.urlPathEqualTo("/discover/movie")).withQueryParam("page", WireMock.equalTo("2"))
                .willReturn(WireMock.okJson("""{"results":[]}""")),
        )

        // when
        val ids = client.fetchTmdbIds(pages = 5)

        // then — 기준 3개 × (1페이지 + 빈 2페이지). 같은 id는 한 번만
        assertThat(ids).containsExactly(155L, 27205L)
        wiremock.verify(6, WireMock.getRequestedFor(WireMock.urlPathEqualTo("/discover/movie")))
        wiremock.verify(
            2,
            WireMock.getRequestedFor(WireMock.urlPathEqualTo("/discover/movie"))
                .withQueryParam("sort_by", WireMock.equalTo("vote_count.desc")),
        )
        wiremock.verify(
            2,
            WireMock.getRequestedFor(WireMock.urlPathEqualTo("/discover/movie"))
                .withQueryParam("sort_by", WireMock.equalTo("vote_average.desc"))
                .withQueryParam("vote_count.gte", WireMock.equalTo("1000")),
        )
        wiremock.verify(
            WireMock.getRequestedFor(WireMock.urlPathEqualTo("/discover/movie"))
                .withQueryParam("sort_by", WireMock.equalTo("popularity.desc"))
                .withQueryParam("vote_count.gte", WireMock.equalTo("20"))
                .withHeader("Authorization", WireMock.equalTo("Bearer test-token")),
        )
    }

    @Test
    fun `detail과 credits를 도메인으로 매핑한다 — 감독은 job이 Director인 크루만`() {
        // given
        wiremock.stubFor(
            WireMock.get(WireMock.urlPathEqualTo("/movie/155")).willReturn(
                WireMock.okJson(
                    """
					{"id":155,"title":"다크 나이트","original_title":"The Dark Knight","overview":"배트맨과 조커",
					 "release_date":"2008-07-16","runtime":152,"vote_average":8.5,"vote_count":30000,
					 "poster_path":"/p.jpg","backdrop_path":"/b.jpg","original_language":"en",
					 "genres":[{"id":80,"name":"범죄"},{"id":28,"name":"액션"}],"unused_field":1}
                    """.trimIndent(),
                ),
            ),
        )
        wiremock.stubFor(
            WireMock.get(WireMock.urlPathEqualTo("/movie/155/credits")).willReturn(
                WireMock.okJson(
                    """
					{"crew":[{"id":525,"name":"크리스토퍼 놀란","job":"Director"},
					         {"id":999,"name":"조나단 놀란","job":"Writer"}],
					 "cast":[{"id":3895,"name":"크리스찬 베일","character":"브루스 웨인","order":0},
					         {"id":1,"name":"히스 레저","character":"조커","order":1}]}
                    """.trimIndent(),
                ),
            ),
        )

        // when
        val m = client.fetchDetail(155L)!!

        // then
        assertThat(m.title).isEqualTo("다크 나이트")
        assertThat(m.originalTitle).isEqualTo("The Dark Knight")
        assertThat(m.releaseDate).isEqualTo("2008-07-16")
        assertThat(m.voteAverage).isEqualByComparingTo("8.5")
        assertThat(m.genres).containsExactly(TmdbGenre(80, "범죄"), TmdbGenre(28, "액션"))
        assertThat(m.directors).containsExactly(TmdbPerson(525, "크리스토퍼 놀란"))
        assertThat(m.cast).hasSize(2)
        assertThat(m.cast.first().character).isEqualTo("브루스 웨인")
    }

    @Test
    fun `overview가 없으면 null을 반환하고 credits까지 가지 않는다`() {
        // given
        wiremock.stubFor(
            WireMock.get(WireMock.urlPathEqualTo("/movie/155")).willReturn(
                WireMock.okJson("""{"id":155,"title":"줄거리 없는 영화","overview":""}"""),
            ),
        )

        // when
        val result = client.fetchDetail(155L)

        // then
        assertThat(result).isNull()
        wiremock.verify(0, WireMock.getRequestedFor(WireMock.urlPathEqualTo("/movie/155/credits")))
    }

    @Test
    fun `TMDB가 5xx를 응답하면 ExternalApiException이 발생한다`() {
        // given
        wiremock.stubFor(
            WireMock.get(WireMock.urlPathEqualTo("/discover/movie")).willReturn(WireMock.serviceUnavailable()),
        )

        // when
        val exception = assertThrows<ExternalApiException> { client.fetchTmdbIds(pages = 1) }

        // then
        assertThat(exception.message).contains("target=tmdb")
    }
}
