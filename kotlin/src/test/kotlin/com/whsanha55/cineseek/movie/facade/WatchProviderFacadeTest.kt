package com.whsanha55.cineseek.movie.facade

import com.whsanha55.cineseek.external.tmdb.client.TmdbClient
import com.whsanha55.cineseek.global.exception.ErrorCodeEnum
import com.whsanha55.cineseek.global.exception.ExternalApiException
import com.whsanha55.cineseek.movie.exception.MovieException
import com.whsanha55.cineseek.movie.service.MovieTmdbIdService
import com.whsanha55.cineseek.movie.vo.WatchProvider
import com.whsanha55.cineseek.movie.vo.WatchProviders
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class WatchProviderFacadeTest {

    private val movieTmdbIdService = mockk<MovieTmdbIdService>()
    private val tmdbClient = mockk<TmdbClient>()
    private val facade = WatchProviderFacade(movieTmdbIdService, tmdbClient)

    @Test
    fun `movieId를 tmdbId로 바꿔 KR 시청처를 조회하고 다음 조회는 캐시에서 돌려준다`() {
        // given
        every { movieTmdbIdService.tmdbIds(listOf(1L)) } returns mapOf(1L to 155L)
        every { tmdbClient.fetchWatchProviders(155L, "KR") } returns PROVIDERS

        // when
        val first = facade.watchProviders(1L)
        val second = facade.watchProviders(1L)

        // then — 캐시가 목적이라 TMDB 호출 횟수를 검증한다
        assertThat(first).isEqualTo(PROVIDERS)
        assertThat(second).isEqualTo(PROVIDERS)
        verify(exactly = 1) { tmdbClient.fetchWatchProviders(155L, "KR") }
    }

    @Test
    fun `TMDB 실패는 캐시하지 않아 다음 조회에서 다시 시도한다`() {
        // given
        every { movieTmdbIdService.tmdbIds(listOf(1L)) } returns mapOf(1L to 155L)
        every { tmdbClient.fetchWatchProviders(155L, "KR") } throws ExternalApiException("tmdb") andThen PROVIDERS

        // when
        assertThrows<ExternalApiException> { facade.watchProviders(1L) }
        val retried = facade.watchProviders(1L)

        // then
        assertThat(retried).isEqualTo(PROVIDERS)
    }

    @Test
    fun `없는 영화는 MOVIE_NOT_FOUND 예외가 발생한다`() {
        // given
        every { movieTmdbIdService.tmdbIds(listOf(99L)) } returns emptyMap()

        // when
        val exception = assertThrows<MovieException> { facade.watchProviders(99L) }

        // then
        assertThat(exception.errorCode).isEqualTo(ErrorCodeEnum.MOVIE_NOT_FOUND)
    }

    private companion object {
        val PROVIDERS = WatchProviders(
            link = "https://www.themoviedb.org/movie/155/watch?locale=KR",
            flatrate = listOf(WatchProvider(8L, "Netflix", "/n.jpg")),
            rent = emptyList(),
            buy = emptyList(),
        )
    }
}
