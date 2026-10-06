package com.whsanha55.cineseek.external.tmdb.client

import com.whsanha55.cineseek.external.tmdb.config.TmdbProperties
import com.whsanha55.cineseek.external.tmdb.dto.CreditsBody
import com.whsanha55.cineseek.external.tmdb.dto.DetailBody
import com.whsanha55.cineseek.external.tmdb.dto.DiscoverBody
import com.whsanha55.cineseek.global.config.http1RestClient
import com.whsanha55.cineseek.global.exception.ExternalApiException
import com.whsanha55.cineseek.movie.vo.TmdbCastMember
import com.whsanha55.cineseek.movie.vo.TmdbGenre
import com.whsanha55.cineseek.movie.vo.TmdbMovie
import com.whsanha55.cineseek.movie.vo.TmdbPerson
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClientException

/**
 * TMDB 클라이언트 — v4 Bearer 인증, ko-KR 고정 (pipeline.tmdb_get 이식).
 * discover(popularity.desc, vote_count.gte=20) → 상세 + credits
 */
@Component
class TmdbClient(private val properties: TmdbProperties) {

    private val restClient = http1RestClient(properties.connectTimeout, properties.readTimeout)
        .baseUrl(properties.baseUrl)
        .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer ${properties.accessToken}")
        .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
        .build()

    /** 수집 기준별로 discover 페이지를 순회하고 합쳐서 중복을 제거한다. 기준마다 빈 페이지에서 조기 종료 (ponytail-audit #13: 위에 남아 있던 옛 KDoc 한 줄 삭제) */
    fun fetchTmdbIds(pages: Int = properties.pages): List<Long> =
        DISCOVER_SOURCES.flatMap { params -> fetchDiscover(params, pages) }.distinct()

    private fun fetchDiscover(params: Map<String, String>, pages: Int): List<Long> = buildList {
        for (page in 1..pages) {
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
            } ?: return@buildList
            if (body.results.isEmpty()) break
            addAll(body.results.map { it.id })
        }
    }

    /** 상세 + credits. overview가 없으면 null — 임베딩 불가라 스킵 (python과 동일) */
    fun fetchDetail(tmdbId: Long): TmdbMovie? {
        val detail = call {
            restClient.get()
                .uri { builder ->
                    builder.path("/movie/{id}").queryParam("language", properties.language).build(tmdbId)
                }
                .retrieve()
                .body(DetailBody::class.java)
        }
        if (detail == null || detail.overview.isNullOrEmpty()) return null

        val credits = call {
            restClient.get()
                .uri { builder ->
                    builder.path("/movie/{id}/credits").queryParam("language", properties.language).build(tmdbId)
                }
                .retrieve()
                .body(CreditsBody::class.java)
        } ?: CreditsBody()

        return detail.toMovie(credits)
    }

    /** HTTP 오류·타임아웃을 ExternalApiException으로 바꾼다 */
    private fun <T> call(request: () -> T): T = try {
        request()
    } catch (e: RestClientException) {
        throw ExternalApiException(TARGET, e)
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
        genres = genres.map { TmdbGenre(it.id, it.name) },
        directors = credits.crew.filter { it.job == DIRECTOR_JOB }.map { TmdbPerson(it.id, it.name) },
        cast = credits.cast.map { TmdbCastMember(it.id, it.name, it.character, it.order) },
    )

    companion object {
        /** 수집 기준 — 명작(투표 수 순, 투표 1000+ 고평점 순) + 최신 화제작(인기 순) */
        private val DISCOVER_SOURCES = listOf(
            mapOf("sort_by" to "vote_count.desc"),
            mapOf("sort_by" to "vote_average.desc", "vote_count.gte" to "1000"),
            mapOf("sort_by" to "popularity.desc", "vote_count.gte" to "20"),
        )

        private const val DIRECTOR_JOB = "Director"
        private const val TARGET = "tmdb"
    }
}
