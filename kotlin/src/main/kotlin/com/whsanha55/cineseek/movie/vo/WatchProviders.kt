package com.whsanha55.cineseek.movie.vo

/** 한 국가의 영화 시청처 (TMDB watch/providers — JustWatch 제공). 구분별로 노출 우선순위 순 */
data class WatchProviders(
    val link: String?,
    val flatrate: List<WatchProvider>,
    val rent: List<WatchProvider>,
    val buy: List<WatchProvider>,
) {
    companion object {
        val EMPTY = WatchProviders(link = null, flatrate = emptyList(), rent = emptyList(), buy = emptyList())
    }
}

data class WatchProvider(val providerId: Long, val name: String, val logoPath: String?)
