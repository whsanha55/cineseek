package com.whsanha55.cineseek.external.tmdb.client

import com.whsanha55.cineseek.external.tmdb.config.TmdbProperties
import com.whsanha55.cineseek.external.tmdb.dto.CreditsBody
import com.whsanha55.cineseek.external.tmdb.dto.DetailBody
import com.whsanha55.cineseek.external.tmdb.dto.DiscoverBody
import com.whsanha55.cineseek.global.config.http1RestClient
import com.whsanha55.cineseek.movie.vo.TmdbCastMember
import com.whsanha55.cineseek.movie.vo.TmdbGenre
import com.whsanha55.cineseek.movie.vo.TmdbMovie
import com.whsanha55.cineseek.movie.vo.TmdbPerson
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component

/**
 * TMDB 클라이언트 — v4 Bearer 인증, ko-KR 고정 (pipeline.tmdb_get 이식).
 * discover(popularity.desc, vote_count.gte=20) → 상세 + credits
 */
@Component
class TmdbClient(private val properties: TmdbProperties) {

    private val restClient = http1RestClient()
        .baseUrl(properties.baseUrl)
        .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer ${properties.accessToken}")
        .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
        .build()

    /** discover 페이지 순회 → tmdb id 목록. 빈 페이지가 나오면 조기 종료 (python과 동일) */
    fun fetchTmdbIds(pages: Int = properties.pages): List<Long> = buildList {
        for (page in 1..pages) {
            val body = restClient.get()
                .uri { builder ->
                    builder.path("/discover/movie")
                        .queryParam("language", properties.language)
                        .queryParam("page", page)
                        .queryParam("sort_by", "popularity.desc")
                        .queryParam("vote_count.gte", 20)
                        .build()
                }
                .retrieve()
                .body(DiscoverBody::class.java) ?: return@buildList
            if (body.results.isEmpty()) break
            addAll(body.results.map { it.id })
        }
    }

    /** 상세 + credits. overview가 없으면 null — 임베딩 불가라 스킵 (python과 동일) */
    fun fetchDetail(tmdbId: Long): TmdbMovie? {
        val detail = restClient.get()
            .uri { builder -> builder.path("/movie/{id}").queryParam("language", properties.language).build(tmdbId) }
            .retrieve()
            .body(DetailBody::class.java) ?: return null
        if (detail.overview.isNullOrEmpty()) return null

        val credits = restClient.get()
            .uri { builder ->
                builder.path("/movie/{id}/credits").queryParam("language", properties.language).build(tmdbId)
            }
            .retrieve()
            .body(CreditsBody::class.java) ?: CreditsBody()

        return detail.toMovie(credits)
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
        private const val DIRECTOR_JOB = "Director"
    }
}
