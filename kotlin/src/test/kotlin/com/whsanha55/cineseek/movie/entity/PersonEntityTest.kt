package com.whsanha55.cineseek.movie.entity

import com.whsanha55.cineseek.movie.vo.TmdbPersonInfo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** PersonEntity — TMDB 재수집과 번역 반영 규칙 (순수 단위 테스트) */
class PersonEntityTest {

    private val info = TmdbPersonInfo(
        personId = 500L,
        name = "톰 크루즈",
        profilePath = "/tom.jpg",
        placeOfBirth = "Syracuse, New York, USA",
        biographyEn = "Tom Cruise is an American actor.",
    )

    private fun translated() = PersonEntity(personId = 500L, name = "톰 크루즈").apply {
        updateFrom(info)
        applyTranslation(placeOfBirthKo = "미국 뉴욕주 시러큐스", biographyKo = "톰 크루즈는 미국의 배우다.")
    }

    @Test
    fun `번역은 비어 있던 항목만 채우고 소개는 기계 번역으로 표시한다`() {
        // when
        val person = translated()

        // then
        assertThat(person.placeOfBirthKo).isEqualTo("미국 뉴욕주 시러큐스")
        assertThat(person.biographyKo).isEqualTo("톰 크루즈는 미국의 배우다.")
        assertThat(person.biographyKoTranslated).isTrue()
    }

    @Test
    fun `원문이 그대로면 재수집해도 번역을 유지한다`() {
        // given
        val person = translated()

        // when
        person.updateFrom(info.copy(profilePath = "/new.jpg"))

        // then
        assertThat(person.profilePath).isEqualTo("/new.jpg")
        assertThat(person.placeOfBirthKo).isEqualTo("미국 뉴욕주 시러큐스")
        assertThat(person.biographyKo).isEqualTo("톰 크루즈는 미국의 배우다.")
        assertThat(person.biographyKoTranslated).isTrue()
    }

    @Test
    fun `원문이 바뀌면 번역과 실패 기록을 지워 다시 번역하게 한다`() {
        // given
        val person = translated().apply { markTranslationFailed("model/person-1234") }

        // when
        person.updateFrom(info.copy(biographyEn = "Tom Cruise is an American actor and producer."))

        // then
        assertThat(person.placeOfBirthKo).isNull()
        assertThat(person.biographyKo).isNull()
        assertThat(person.biographyKoTranslated).isFalse()
        assertThat(person.translationFailedModel).isNull()
    }

    @Test
    fun `TMDB에 한국어 소개가 생기면 번역 대신 그것을 쓴다`() {
        // given
        val person = translated()

        // when
        person.updateFrom(info.copy(biographyKo = "TMDB 한국어 소개"))

        // then
        assertThat(person.biographyKo).isEqualTo("TMDB 한국어 소개")
        assertThat(person.biographyKoTranslated).isFalse()
    }

    @Test
    fun `TMDB 한국어 소개가 있으면 번역 결과로 덮어쓰지 않는다`() {
        // given
        val person = PersonEntity(personId = 500L, name = "톰 크루즈").apply {
            updateFrom(info.copy(biographyKo = "TMDB 한국어 소개"))
        }

        // when
        person.applyTranslation(placeOfBirthKo = "미국 뉴욕주 시러큐스", biographyKo = "번역된 소개")

        // then
        assertThat(person.biographyKo).isEqualTo("TMDB 한국어 소개")
        assertThat(person.biographyKoTranslated).isFalse()
    }
}
