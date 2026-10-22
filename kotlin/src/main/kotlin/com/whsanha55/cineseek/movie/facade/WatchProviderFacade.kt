package com.whsanha55.cineseek.movie.facade

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.whsanha55.cineseek.external.tmdb.client.TmdbClient
import com.whsanha55.cineseek.global.exception.ErrorCodeEnum
import com.whsanha55.cineseek.movie.exception.MovieException
import com.whsanha55.cineseek.movie.service.MovieTmdbIdService
import com.whsanha55.cineseek.movie.vo.WatchProviders
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * 영화 시청처(KR). 자주 바뀌는 데이터라 색인·PG에 넣지 않고 조회 때 TMDB에서 가져와 짧게 캐시한다.
 * TMDB 실패는 캐시하지 않고 그대로 던진다 — 상세 화면은 별도 요청이라 시청처 영역만 숨긴다
 */
@Component
class WatchProviderFacade(private val movieTmdbIdService: MovieTmdbIdService, private val tmdbClient: TmdbClient) {

    private val cache: Cache<Long, WatchProviders> = Caffeine.newBuilder()
        .expireAfterWrite(CACHE_TTL)
        .maximumSize(CACHE_MAX_SIZE)
        .build()

    fun watchProviders(movieId: Long): WatchProviders {
        cache.getIfPresent(movieId)?.let { return it }
        val tmdbId = movieTmdbIdService.tmdbIds(listOf(movieId))[movieId]
            ?: throw MovieException(ErrorCodeEnum.MOVIE_NOT_FOUND)
        return tmdbClient.fetchWatchProviders(tmdbId, REGION).also { cache.put(movieId, it) }
    }

    private companion object {
        const val REGION = "KR"
        val CACHE_TTL: Duration = Duration.ofHours(1)
        const val CACHE_MAX_SIZE = 10_000L
    }
}
