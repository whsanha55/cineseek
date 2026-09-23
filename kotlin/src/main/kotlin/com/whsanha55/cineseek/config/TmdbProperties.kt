package com.whsanha55.cineseek.config

import org.springframework.boot.context.properties.ConfigurationProperties

/** cineseek.tmdb — TMDB 수집 설정 */
@ConfigurationProperties("cineseek.tmdb")
data class TmdbProperties(
	val baseUrl: String = "https://api.themoviedb.org/3",
	val accessToken: String = "",
	val language: String = "ko-KR",
	val pages: Int = 50, // discover 페이지 수 (페이지당 20건 → 기본 ~1000건 후보)
)
