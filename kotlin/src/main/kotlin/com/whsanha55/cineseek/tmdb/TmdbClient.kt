package com.whsanha55.cineseek.tmdb

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.whsanha55.cineseek.config.TmdbProperties
import com.whsanha55.cineseek.config.http1RestClient
import java.math.BigDecimal
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

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
			.uri { builder -> builder.path("/movie/{id}/credits").queryParam("language", properties.language).build(tmdbId) }
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

// TMDB 와이어 DTO — snake_case JSON이라 @JsonProperty 명시 (자동 변환 안 함)
@JsonIgnoreProperties(ignoreUnknown = true)
private data class DiscoverBody(
	val results: List<DiscoverItem> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class DiscoverItem(
	val id: Long,
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class DetailBody(
	val id: Long,
	val title: String? = null,
	@JsonProperty("original_title") val originalTitle: String? = null,
	val overview: String? = null,
	@JsonProperty("release_date") val releaseDate: String? = null,
	val runtime: Int? = null,
	@JsonProperty("vote_average") val voteAverage: BigDecimal? = null,
	@JsonProperty("vote_count") val voteCount: Int? = null,
	@JsonProperty("poster_path") val posterPath: String? = null,
	@JsonProperty("backdrop_path") val backdropPath: String? = null,
	@JsonProperty("original_language") val originalLanguage: String? = null,
	val genres: List<GenreItem> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class GenreItem(
	val id: Long,
	val name: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class CreditsBody(
	val crew: List<CrewItem> = emptyList(),
	val cast: List<CastItem> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class CrewItem(
	val id: Long,
	val name: String,
	val job: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class CastItem(
	val id: Long,
	val name: String,
	val character: String? = null,
	val order: Int? = null,
)
