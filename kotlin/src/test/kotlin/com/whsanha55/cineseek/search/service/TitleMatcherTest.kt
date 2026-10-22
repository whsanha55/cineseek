package com.whsanha55.cineseek.search.service

import com.whsanha55.cineseek.movie.repository.MovieRepository
import com.whsanha55.cineseek.movie.vo.MovieTitle
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class TitleMatcherTest {

    private val movieRepository = mockk<MovieRepository>()
    private val clock = MutableClock(Instant.parse("2026-10-22T00:00:00Z"))
    private val matcher = TitleMatcher(movieRepository, clock)

    @Test
    fun `제목 전체 일치 — 공백과 문장부호는 무시하고 대소문자를 가리지 않는다`() {
        // given
        titles(MovieTitle(1, "메이즈 러너: 데스 큐어", "Maze Runner: The Death Cure"))

        // when, then
        assertThat(matcher.match("메이즈 러너 데스 큐어")).containsExactly(1L)
        assertThat(matcher.match("maze runner the death cure")).containsExactly(1L)
    }

    @Test
    fun `부제를 뗀 본제목이나 원제로도 일치한다`() {
        // given
        titles(MovieTitle(1, "레버넌트: 죽음에서 돌아온 자", "The Revenant"), MovieTitle(2, "스타게이트", "Stargate"))

        // when, then
        assertThat(matcher.match("레버넌트")).containsExactly(1L)
        assertThat(matcher.match("Stargate")).containsExactly(2L)
    }

    @Test
    fun `제목 단어의 절반을 넘게 덮는 앞부분 일치는 일치로 본다`() {
        // given
        titles(MovieTitle(1, "남자가 사랑할 때", null))

        // when, then
        assertThat(matcher.match("남자가 사랑할")).containsExactly(1L)
        assertThat(matcher.match("남자가 사랑")).containsExactly(1L)
    }

    @Test
    fun `제목 단어의 절반 이하만 덮으면 일치가 아니다`() {
        // given
        titles(MovieTitle(1, "오만과 편견 그리고 좀비", null), MovieTitle(2, "라스트 크리스마스", null))

        // when, then
        assertThat(matcher.match("좀비")).isEmpty()
        assertThat(matcher.match("크리스마스")).isEmpty()
    }

    @Test
    fun `쿼리 단어가 제목에 없으면 일치가 아니다`() {
        // given
        titles(MovieTitle(1, "무서운 영화", "Scary Movie"))

        // when, then
        assertThat(matcher.match("무서워서 잠 못 드는 영화")).isEmpty()
        assertThat(matcher.match("공포 영화")).isEmpty()
    }

    @Test
    fun `일치가 20편을 넘으면 일반어로 보고 빈 목록을 돌려준다`() {
        // given
        titles(*(1L..21L).map { MovieTitle(it, "사랑 $it", null) }.toTypedArray())

        // when, then
        assertThat(matcher.match("사랑")).isEmpty()
    }

    @Test
    fun `제목 목록은 10분 동안 캐시하고 지나면 다시 읽는다`() {
        // given
        titles(MovieTitle(1, "실낙원", null))
        matcher.match("실낙원")
        titles(MovieTitle(1, "실낙원", null), MovieTitle(2, "베일러", null))

        // when
        val cached = matcher.match("베일러")
        clock.advance(Duration.ofMinutes(10))
        val reloaded = matcher.match("베일러")

        // then
        assertThat(cached).isEmpty()
        assertThat(reloaded).containsExactly(2L)
        verify(exactly = 2) { movieRepository.findAllTitles() }
    }

    @Test
    fun `제목 목록을 읽지 못하면 이전 목록으로 매칭한다`() {
        // given
        titles(MovieTitle(1, "실낙원", null))
        matcher.match("실낙원")
        every { movieRepository.findAllTitles() } throws IllegalStateException("db down")
        clock.advance(Duration.ofMinutes(10))

        // when, then
        assertThat(matcher.match("실낙원")).containsExactly(1L)
    }

    private fun titles(vararg titles: MovieTitle) {
        every { movieRepository.findAllTitles() } returns titles.toList()
    }

    private class MutableClock(private var now: Instant) : Clock() {
        fun advance(duration: Duration) {
            now = now.plus(duration)
        }

        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock = this

        override fun instant(): Instant = now
    }
}
