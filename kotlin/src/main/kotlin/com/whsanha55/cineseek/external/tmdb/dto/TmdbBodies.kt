package com.whsanha55.cineseek.external.tmdb.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal

/* TMDB 와이어 DTO — snake_case JSON이라 @JsonProperty 명시 (자동 변환 안 함). TmdbClient 밖으로 내보내지 않는다 */

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class DiscoverBody(
    val results: List<DiscoverItem> = emptyList(),
    @JsonProperty("total_pages") val totalPages: Int? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class DiscoverItem(val id: Long)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class DetailBody(
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
    @JsonProperty("origin_country") val originCountries: List<String> = emptyList(),
    @JsonProperty("production_countries") val productionCountries: List<ProductionCountryItem> = emptyList(),
    val genres: List<GenreItem> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class GenreItem(val id: Long, val name: String)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class CreditsBody(val crew: List<CrewItem> = emptyList(), val cast: List<CastItem> = emptyList())

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class CrewItem(val id: Long, val name: String, val job: String? = null)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class CastItem(val id: Long, val name: String, val character: String? = null, val order: Int? = null)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class ProductionCountryItem(@JsonProperty("iso_3166_1") val isoCode: String? = null)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class PersonBody(
    val id: Long,
    val name: String? = null,
    @JsonProperty("profile_path") val profilePath: String? = null,
    val birthday: String? = null,
    val deathday: String? = null,
    @JsonProperty("place_of_birth") val placeOfBirth: String? = null,
    val biography: String? = null,
    val homepage: String? = null,
    val translations: PersonTranslations? = null,
    @JsonProperty("external_ids") val externalIds: PersonExternalIds? = null,
)

/** append_to_response=external_ids — 외부 서비스 id */
@JsonIgnoreProperties(ignoreUnknown = true)
internal data class PersonExternalIds(
    @JsonProperty("imdb_id") val imdbId: String? = null,
    @JsonProperty("instagram_id") val instagramId: String? = null,
)

/** append_to_response=translations — 언어별 이름. 번역이 없는 언어는 목록에 없거나 name이 빈 문자열이다 */
@JsonIgnoreProperties(ignoreUnknown = true)
internal data class PersonTranslations(val translations: List<PersonTranslation> = emptyList())

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class PersonTranslation(
    @JsonProperty("iso_639_1") val language: String? = null,
    val data: PersonTranslationData? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class PersonTranslationData(val name: String? = null, val biography: String? = null)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class PersonCreditsBody(val cast: List<PersonCastItem> = emptyList())

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class PersonCastItem(
    val id: Long, // 출연 영화의 TMDB id
    val title: String? = null,
    val character: String? = null,
    val order: Int? = null,
    @JsonProperty("release_date") val releaseDate: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class WatchProvidersBody(val results: Map<String, WatchProviderRegion> = emptyMap())

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class WatchProviderRegion(
    val link: String? = null,
    val flatrate: List<WatchProviderItem> = emptyList(),
    val rent: List<WatchProviderItem> = emptyList(),
    val buy: List<WatchProviderItem> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class WatchProviderItem(
    @JsonProperty("provider_id") val providerId: Long,
    @JsonProperty("provider_name") val providerName: String? = null,
    @JsonProperty("logo_path") val logoPath: String? = null,
    @JsonProperty("display_priority") val displayPriority: Int? = null,
)
