package com.whsanha55.cineseek.external.tmdb.client

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.stubbing.Scenario
import com.whsanha55.cineseek.external.tmdb.config.TmdbProperties
import com.whsanha55.cineseek.global.exception.ExternalApiException
import com.whsanha55.cineseek.movie.vo.TmdbGenre
import com.whsanha55.cineseek.movie.vo.TmdbPerson
import com.whsanha55.cineseek.movie.vo.TmdbPersonCredit
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** TMDB 클라이언트 테스트 — WireMock 스텁 (discover 파라미터화·체크포인트 재개, detail+credits 매핑, 404·재시도, 인물) */
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
            2,
            WireMock.getRequestedFor(WireMock.urlPathEqualTo("/discover/movie"))
                .withQueryParam("sort_by", WireMock.equalTo("popularity.desc"))
                .withQueryParam("vote_count.gte", WireMock.equalTo("20"))
                .withHeader("Authorization", WireMock.equalTo("Bearer test-token")),
        )
    }

    @Test
    fun `한국 discover는 with_origin_country와 연도 구간을 전달하고 투표 수 하한을 붙이지 않는다`() {
        // given
        wiremock.stubFor(
            WireMock.get(WireMock.urlPathEqualTo("/discover/movie"))
                .willReturn(WireMock.okJson("""{"results":[{"id":11},{"id":12}],"total_pages":1}""")),
        )

        // when
        val result = client.fetchDiscoverIds(
            TmdbClient.KR_DISCOVER_PARAMS + mapOf(
                "sort_by" to "primary_release_date.desc",
                "primary_release_date.gte" to "2000-01-01",
                "primary_release_date.lte" to "2009-12-31",
            ),
            endPage = 5,
        )

        // then — total_pages=1이라 1페이지에서 종료
        assertThat(result.tmdbIds).containsExactly(11L, 12L)
        assertThat(result.lastPage).isEqualTo(1)
        assertThat(result.totalPages).isEqualTo(1)
        wiremock.verify(
            WireMock.getRequestedFor(WireMock.urlPathEqualTo("/discover/movie"))
                .withQueryParam("language", WireMock.equalTo("ko-KR"))
                .withQueryParam("with_origin_country", WireMock.equalTo("KR"))
                .withQueryParam("sort_by", WireMock.equalTo("primary_release_date.desc"))
                .withQueryParam("primary_release_date.gte", WireMock.equalTo("2000-01-01"))
                .withQueryParam("primary_release_date.lte", WireMock.equalTo("2009-12-31")),
        )
        wiremock.verify(
            0,
            WireMock.getRequestedFor(WireMock.urlPathEqualTo("/discover/movie"))
                .withQueryParam("vote_count.gte", WireMock.matching(".+")),
        )
    }

    @Test
    fun `startPage를 지정하면 이전 페이지는 호출하지 않고 이어서 순회한다`() {
        // given
        wiremock.stubFor(
            WireMock.get(WireMock.urlPathEqualTo("/discover/movie")).withQueryParam("page", WireMock.equalTo("2"))
                .willReturn(WireMock.okJson("""{"results":[{"id":11},{"id":12}],"total_pages":4}""")),
        )
        wiremock.stubFor(
            WireMock.get(WireMock.urlPathEqualTo("/discover/movie")).withQueryParam("page", WireMock.equalTo("3"))
                .willReturn(WireMock.okJson("""{"results":[{"id":13}],"total_pages":4}""")),
        )
        wiremock.stubFor(
            WireMock.get(WireMock.urlPathEqualTo("/discover/movie")).withQueryParam("page", WireMock.equalTo("4"))
                .willReturn(WireMock.okJson("""{"results":[],"total_pages":4}""")),
        )

        // when — 체크포인트(2페이지)에서 재개
        val result = client.fetchDiscoverIds(
            TmdbClient.KO_LANGUAGE_DISCOVER_PARAMS + ("sort_by" to "primary_release_date.desc"),
            startPage = 2,
            endPage = 10,
        )

        // then — 4페이지가 빈 페이지라 조기 종료
        assertThat(result.tmdbIds).containsExactly(11L, 12L, 13L)
        assertThat(result.lastPage).isEqualTo(4)
        assertThat(result.totalPages).isEqualTo(4)
        wiremock.verify(
            0,
            WireMock.getRequestedFor(WireMock.urlPathEqualTo("/discover/movie"))
                .withQueryParam("page", WireMock.equalTo("1")),
        )
        wiremock.verify(
            WireMock.getRequestedFor(WireMock.urlPathEqualTo("/discover/movie"))
                .withQueryParam("with_original_language", WireMock.equalTo("ko")),
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
    fun `overview가 비어도 메타데이터를 반환하고 originCountry를 매핑한다`() {
        // given
        wiremock.stubFor(
            WireMock.get(WireMock.urlPathEqualTo("/movie/155")).willReturn(
                WireMock.okJson(
                    """
                    {"id":155,"title":"줄거리 없는 영화","overview":"","original_language":"ko",
                     "production_countries":[{"iso_3166_1":"KR","name":"대한민국"}]}
                    """.trimIndent(),
                ),
            ),
        )
        wiremock.stubFor(
            WireMock.get(WireMock.urlPathEqualTo("/movie/155/credits")).willReturn(
                WireMock.okJson("""{"cast":[],"crew":[]}"""),
            ),
        )

        // when
        val movie = client.fetchDetail(155L)!!

        // then — 줄거리 누락은 보강 대상일 뿐 메타데이터를 버리지 않는다
        assertThat(movie.title).isEqualTo("줄거리 없는 영화")
        assertThat(movie.overview).isEmpty()
        assertThat(movie.originCountry).isEqualTo("KR")
        wiremock.verify(1, WireMock.getRequestedFor(WireMock.urlPathEqualTo("/movie/155/credits")))
    }

    @Test
    fun `detail이 404면 null을 반환하고 재시도하지 않는다`() {
        // given
        wiremock.stubFor(
            WireMock.get(WireMock.urlPathEqualTo("/movie/999")).willReturn(WireMock.notFound()),
        )

        // when
        val result = client.fetchDetail(999L)

        // then — 영구 실패라 1번만 호출
        assertThat(result).isNull()
        wiremock.verify(1, WireMock.getRequestedFor(WireMock.urlPathEqualTo("/movie/999")))
        wiremock.verify(0, WireMock.getRequestedFor(WireMock.urlPathEqualTo("/movie/999/credits")))
    }

    @Test
    fun `429는 재시도해 성공한다`() {
        // given
        wiremock.stubFor(
            WireMock.get(WireMock.urlPathEqualTo("/discover/movie"))
                .inScenario("rate-limit")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(WireMock.aResponse().withStatus(429).withHeader("Retry-After", "0"))
                .willSetStateTo("recovered"),
        )
        wiremock.stubFor(
            WireMock.get(WireMock.urlPathEqualTo("/discover/movie"))
                .inScenario("rate-limit")
                .whenScenarioStateIs("recovered")
                .willReturn(WireMock.okJson("""{"results":[{"id":11}],"total_pages":1}""")),
        )

        // when
        val result = client.fetchDiscoverIds(TmdbClient.KR_DISCOVER_PARAMS, endPage = 3)

        // then — 재시도한 요청이 성공 응답을 받는다
        assertThat(result.tmdbIds).containsExactly(11L)
        wiremock.verify(2, WireMock.getRequestedFor(WireMock.urlPathEqualTo("/discover/movie")))
    }

    @Test
    fun `429가 계속되면 재시도를 소진해 status가 담긴 ExternalApiException이 발생한다`() {
        // given
        wiremock.stubFor(
            WireMock.get(WireMock.urlPathEqualTo("/discover/movie"))
                .willReturn(WireMock.aResponse().withStatus(429).withHeader("Retry-After", "0")),
        )

        // when
        val exception = assertThrows<ExternalApiException> {
            client.fetchDiscoverIds(TmdbClient.KR_DISCOVER_PARAMS, endPage = 1)
        }

        // then — 초기 호출 1회 + 재시도 3회
        assertThat(exception.status).isEqualTo(429)
        assertThat(exception.message).contains("target=tmdb")
        wiremock.verify(4, WireMock.getRequestedFor(WireMock.urlPathEqualTo("/discover/movie")))
    }

    @Test
    fun `TMDB가 5xx를 응답하면 재시도 소진 후 status를 담은 ExternalApiException이 발생한다`() {
        // given
        wiremock.stubFor(
            WireMock.get(WireMock.urlPathEqualTo("/discover/movie")).willReturn(WireMock.serviceUnavailable()),
        )

        // when
        val exception = assertThrows<ExternalApiException> { client.fetchTmdbIds(pages = 1) }

        // then — 503도 일시 오류로 재시도(1회 + 3회) 후 실패
        assertThat(exception.message).contains("target=tmdb")
        assertThat(exception.status).isEqualTo(503)
        wiremock.verify(4, WireMock.getRequestedFor(WireMock.urlPathEqualTo("/discover/movie")))
    }

    @Test
    fun `인물 상세를 매핑하고 404는 null을 반환한다`() {
        // given
        wiremock.stubFor(
            WireMock.get(WireMock.urlPathEqualTo("/person/3895")).willReturn(
                WireMock.okJson("""{"id":3895,"name":"크리스찬 베일","profile_path":"/bale.jpg","birthday":"1974-01-30"}"""),
            ),
        )
        wiremock.stubFor(
            WireMock.get(WireMock.urlPathEqualTo("/person/999")).willReturn(WireMock.notFound()),
        )

        // when
        val person = client.fetchPerson(3895L)!!
        val missing = client.fetchPerson(999L)

        // then
        assertThat(person.personId).isEqualTo(3895L)
        assertThat(person.name).isEqualTo("크리스찬 베일")
        assertThat(person.profilePath).isEqualTo("/bale.jpg")
        assertThat(missing).isNull()
        wiremock.verify(
            WireMock.getRequestedFor(WireMock.urlPathEqualTo("/person/3895"))
                .withQueryParam("language", WireMock.equalTo("ko-KR")),
        )
    }

    @Test
    fun `인물 필모그래피 cast를 매핑하고 404는 빈 목록을 반환한다`() {
        // given
        wiremock.stubFor(
            WireMock.get(WireMock.urlPathEqualTo("/person/3895/movie_credits")).willReturn(
                WireMock.okJson(
                    """
                    {"id":3895,
                     "cast":[{"id":155,"title":"다크 나이트","character":"브루스 웨인","order":0,"release_date":"2008-07-16"},
                              {"id":27205,"title":"인셉션","character":null,"order":2,"release_date":null}],
                     "crew":[{"id":999,"name":"필모그래피에 넣지 않는 크루"}]}
                    """.trimIndent(),
                ),
            ),
        )
        wiremock.stubFor(
            WireMock.get(WireMock.urlPathEqualTo("/person/1/movie_credits")).willReturn(WireMock.notFound()),
        )

        // when
        val credits = client.fetchPersonMovieCredits(3895L)
        val missing = client.fetchPersonMovieCredits(1L)

        // then — crew는 버리고 cast만
        assertThat(credits).containsExactly(
            TmdbPersonCredit(
                tmdbId = 155,
                title = "다크 나이트",
                character = "브루스 웨인",
                order = 0,
                releaseDate = "2008-07-16",
            ),
            TmdbPersonCredit(tmdbId = 27205, title = "인셉션", character = null, order = 2, releaseDate = null),
        )
        assertThat(missing).isEmpty()
    }
}
