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
)

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
