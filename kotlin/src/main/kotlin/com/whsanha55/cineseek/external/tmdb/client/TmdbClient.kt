package com.whsanha55.cineseek.external.tmdb.client

import com.whsanha55.cineseek.external.tmdb.config.TmdbProperties
import com.whsanha55.cineseek.external.tmdb.dto.CreditsBody
import com.whsanha55.cineseek.external.tmdb.dto.DetailBody
import com.whsanha55.cineseek.external.tmdb.dto.DiscoverBody
import com.whsanha55.cineseek.external.tmdb.dto.PersonBody
import com.whsanha55.cineseek.external.tmdb.dto.PersonCreditsBody
import com.whsanha55.cineseek.global.config.http1RestClient
import com.whsanha55.cineseek.global.exception.ExternalApiException
import com.whsanha55.cineseek.movie.vo.TmdbCastMember
import com.whsanha55.cineseek.movie.vo.TmdbDiscoverResult
import com.whsanha55.cineseek.movie.vo.TmdbGenre
import com.whsanha55.cineseek.movie.vo.TmdbMovie
import com.whsanha55.cineseek.movie.vo.TmdbPerson
import com.whsanha55.cineseek.movie.vo.TmdbPersonCredit
import com.whsanha55.cineseek.movie.vo.TmdbPersonInfo
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.RestClientException
import java.time.Duration

/**
 * TMDB 클라이언트 — v4 Bearer 인증, ko-KR 고정 (pipeline.tmdb_get 이식).
 * discover → 상세 + credits, 인물 상세·필모그래피. 429와 일시 5xx(502/503/504)는 클라이언트 안에서 재시도한다.
 */
@Component
class TmdbClient(private val properties: TmdbProperties) {

    private val restClient = http1RestClient(properties.connectTimeout, properties.readTimeout)
        .baseUrl(properties.baseUrl)
        .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer ${properties.accessToken}")
        .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
        .build()

    /** 수집 기준별로 discover 페이지를 순회하고 합쳐서 중복을 제거한다. 기준마다 빈 페이지에서 조기 종료 */
    fun fetchTmdbIds(pages: Int = properties.pages): List<Long> =
        GLOBAL_DISCOVER_SOURCES.flatMap { params -> fetchDiscoverIds(params, endPage = pages).tmdbIds }.distinct()

    /**
     * 임의 파라미터로 discover를 순회한다 — 한국 수집처럼 호출자가 정렬·연도 구간 등 조건을 만들어 넘긴다.
     * startPage부터 endPage까지 읽고 빈 페이지·마지막 페이지(total_pages)에서 종료한다.
     * 반환값의 lastPage는 성공적으로 소비한 마지막 페이지로, 다음 실행의 체크포인트(startPage)로 쓴다.
     */
    fun fetchDiscoverIds(
        params: Map<String, String>,
        startPage: Int = 1,
        endPage: Int = properties.pages,
    ): TmdbDiscoverResult {
        val ids = mutableListOf<Long>()
        var lastPage = startPage - 1
        var totalPages = 0
        for (page in startPage..endPage) {
            val body = call {
                restClient.get()
                    .uri { builder ->
                        builder.path("/discover/movie")
                            .queryParam("language", properties.language)
                            .queryParam("page", page)
                        params.forEach { (key, value) -> builder.queryParam(key, value) }
                        builder.build()
                    }
                    .retrieve()
                    .body(DiscoverBody::class.java)
            } ?: return TmdbDiscoverResult(tmdbIds = ids, lastPage = lastPage, totalPages = totalPages)
            lastPage = page
            totalPages = body.totalPages ?: 0
            ids.addAll(body.results.map { it.id })
            if (body.results.isEmpty() || (totalPages > 0 && page >= totalPages)) break
        }
        return TmdbDiscoverResult(tmdbIds = ids, lastPage = lastPage, totalPages = totalPages)
    }

    /** 상세 + credits. 404는 null(영구 실패). overview가 비어도 반환한다 — 줄거리 누락은 보강 대상일 뿐 메타데이터를 버리지 않는다 */
    fun fetchDetail(tmdbId: Long): TmdbMovie? {
        val detail = call(notFoundAsNull = true) {
            restClient.get()
                .uri { builder ->
                    builder.path("/movie/{id}").queryParam("language", properties.language).build(tmdbId)
                }
                .retrieve()
                .body(DetailBody::class.java)
        } ?: return null

        val credits = call(notFoundAsNull = true) {
            restClient.get()
                .uri { builder ->
                    builder.path("/movie/{id}/credits").queryParam("language", properties.language).build(tmdbId)
                }
                .retrieve()
                .body(CreditsBody::class.java)
        } ?: CreditsBody()

        return detail.toMovie(credits)
    }

    /** 인물 상세. 404(삭제된 인물 등)는 null — 호출자가 영구 실패로 처리한다 */
    fun fetchPerson(personId: Long): TmdbPersonInfo? {
        val body = call(notFoundAsNull = true) {
            restClient.get()
                .uri { builder ->
                    builder.path("/person/{id}").queryParam("language", properties.language).build(personId)
                }
                .retrieve()
                .body(PersonBody::class.java)
        } ?: return null
        return TmdbPersonInfo(personId = body.id, name = body.name.orEmpty(), profilePath = body.profilePath)
    }

    /** 인물 영화 필모그래피(cast). 인물 404·빈 본문은 빈 목록 */
    fun fetchPersonMovieCredits(personId: Long): List<TmdbPersonCredit> {
        val body = call(notFoundAsNull = true) {
            restClient.get()
                .uri { builder ->
                    builder.path("/person/{id}/movie_credits")
                        .queryParam("language", properties.language)
                        .build(personId)
                }
                .retrieve()
                .body(PersonCreditsBody::class.java)
        } ?: return emptyList()
        return body.cast.map { item ->
            TmdbPersonCredit(
                tmdbId = item.id,
                title = item.title,
                character = item.character,
                order = item.order,
                releaseDate = item.releaseDate,
            )
        }
    }

    /**
     * 외부 호출 래퍼 — 429·일시 5xx는 재시도하고, 재시도 소진·나머지 HTTP 오류·타임아웃은 status를 담은
     * ExternalApiException으로 바꾼다(타임아웃·연결 실패는 status=null). notFoundAsNull이면 404를
     * null로 돌려준다(영구 실패라 재시도하지 않는다).
     */
    private fun <T> call(notFoundAsNull: Boolean = false, request: () -> T): T? {
        var attempt = 0
        while (true) {
            try {
                return request()
            } catch (e: HttpClientErrorException.NotFound) {
                if (notFoundAsNull) return null
                throw ExternalApiException(TARGET, e, e.statusCode.value())
            } catch (e: RestClientException) {
                val status = (e as? HttpStatusCodeException)?.statusCode?.value()
                if (status == null || status !in RETRYABLE_STATUSES || attempt >= MAX_RETRIES) {
                    throw ExternalApiException(TARGET, e, status)
                }
                Thread.sleep(retryDelay(attempt, e).toMillis())
                attempt++
            }
        }
    }

    /** 재시도 대기 — Retry-After 헤더(초)가 있으면 그 값을, 없으면 짧은 지수 백오프를 쓴다 */
    private fun retryDelay(attempt: Int, e: RestClientException): Duration {
        val retryAfterSeconds = (e as? HttpStatusCodeException)
            ?.responseHeaders?.getFirst(HttpHeaders.RETRY_AFTER)?.toLongOrNull()
        if (retryAfterSeconds != null && retryAfterSeconds > 0) {
            return Duration.ofSeconds(retryAfterSeconds)
        }
        return Duration.ofMillis(RETRY_BACKOFF_BASE_MILLIS shl attempt)
    }

    private fun DetailBody.toMovie(credits: CreditsBody) = TmdbMovie(
        tmdbId = id,
        title = title.orEmpty(),
        originalTitle = originalTitle,
        overview = overview.orEmpty(),
        releaseDate = releaseDate,
        runtime = runtime,
        voteAverage = voteAverage,
        voteCount = voteCount,
        posterPath = posterPath,
        backdropPath = backdropPath,
        originalLanguage = originalLanguage,
        originCountry = productionCountries.firstOrNull()?.isoCode,
        genres = genres.map { TmdbGenre(it.id, it.name) },
        directors = credits.crew.filter { it.job == DIRECTOR_JOB }.map { TmdbPerson(it.id, it.name) },
        cast = credits.cast.map { TmdbCastMember(it.id, it.name, it.character, it.order) },
    )

    companion object {
        /**
         * 전 세계 수집 기준 — 명작(투표 수 순, 투표 1000+ 고평점 순) + 최신 화제작(인기 순).
         * 수집 파이프라인이 payloadKey("global:{sort_by}")와 대응시키므로 공개한다
         */
        val GLOBAL_DISCOVER_SOURCES = listOf(
            mapOf("sort_by" to "vote_count.desc"),
            mapOf("sort_by" to "vote_average.desc", "vote_count.gte" to "1000"),
            mapOf("sort_by" to "popularity.desc", "vote_count.gte" to "20"),
        )

        /** 한국 수집 기준 — 제작국가 KR. 투표 수 하한 없음(한국 영화는 투표 수가 적어도 수집) */
        val KR_DISCOVER_PARAMS: Map<String, String> = mapOf("with_origin_country" to "KR")

        /** 한국 수집 기준 — 원어 한국어. 투표 수 하한 없음 */
        val KO_LANGUAGE_DISCOVER_PARAMS: Map<String, String> = mapOf("with_original_language" to "ko")

        private const val DIRECTOR_JOB = "Director"
        private const val TARGET = "tmdb"
        private const val MAX_RETRIES = 3
        private const val RETRY_BACKOFF_BASE_MILLIS = 200L
        private val RETRYABLE_STATUSES = setOf(
            HttpStatus.TOO_MANY_REQUESTS.value(),
            HttpStatus.BAD_GATEWAY.value(),
            HttpStatus.SERVICE_UNAVAILABLE.value(),
            HttpStatus.GATEWAY_TIMEOUT.value(),
        )
    }
}
