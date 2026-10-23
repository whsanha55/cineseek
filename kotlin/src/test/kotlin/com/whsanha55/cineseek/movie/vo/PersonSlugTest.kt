package com.whsanha55.cineseek.movie.vo

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PersonSlugTest {

    @Test
    fun `영어 이름을 소문자와 하이픈으로 바꾼다`() {
        assertThat(PersonSlug.from("Tom Cruise")).isEqualTo("tom-cruise")
        assertThat(PersonSlug.from("Song Kang-ho")).isEqualTo("song-kang-ho")
        assertThat(PersonSlug.from("Robert Downey Jr.")).isEqualTo("robert-downey-jr")
    }

    @Test
    fun `악센트는 떼고 라틴 문자로 남긴다`() {
        assertThat(PersonSlug.from("Zoë Saldaña")).isEqualTo("zoe-saldana")
    }

    @Test
    fun `라틴 문자가 남지 않거나 이름이 없으면 null`() {
        assertThat(PersonSlug.from("李安")).isNull()
        assertThat(PersonSlug.from(null)).isNull()
    }

    @Test
    fun `60자를 넘으면 자르고 끝의 하이픈을 지운다`() {
        val slug = PersonSlug.from("a".repeat(59) + " bcd")

        assertThat(slug).isEqualTo("a".repeat(59))
    }
}
