package com.whsanha55.cineseek.movie.service

import com.whsanha55.cineseek.external.llm.client.LlmClient
import com.whsanha55.cineseek.external.llm.client.LlmContentFilteredException
import com.whsanha55.cineseek.external.llm.config.LlmProperties
import com.whsanha55.cineseek.movie.entity.PersonEntity
import com.whsanha55.cineseek.movie.repository.PersonRepository
import com.whsanha55.cineseek.movie.vo.TmdbPersonInfo
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.util.Optional

/** 인물 번역 단위 테스트 — repository/llmClient만 MockK, ObjectMapper는 진짜 인스턴스 */
class PersonTranslatorTest {

    private val llmClient = mockk<LlmClient>()
    private val personRepository = mockk<PersonRepository>()
    private val translator = PersonTranslator(
        llmClient,
        personRepository,
        LlmProperties(apiKey = "test-key"),
        jacksonObjectMapper(),
    )

    private fun stubPerson(info: TmdbPersonInfo, mutate: (PersonEntity) -> Unit = {}): PersonEntity {
        val person = PersonEntity(personId = info.personId, name = info.name).apply {
            updateFrom(info)
            mutate(this)
        }
        every { personRepository.findById(info.personId) } returns Optional.of(person)
        every { personRepository.save(person) } returns person
        return person
    }

    private val songKangHo = TmdbPersonInfo(
        personId = 20738L,
        name = "송강호",
        profilePath = null,
        placeOfBirth = "Gimhae, South Gyeongsang, South Korea",
        biographyEn = "Song Kang-ho is a South Korean actor.",
    )

    @Test
    fun `출생지와 영어 소개를 번역해 저장하고 소개는 기계 번역으로 표시한다`() {
        // given
        val person = stubPerson(songKangHo)
        val prompt = slot<String>()
        every { llmClient.chat(capture(prompt)) } returns
            """```json
            {"placeOfBirth":"대한민국 경상남도 김해","biography":"송강호는 대한민국의 배우다."}
            ```"""

        // when
        val translated = translator.translateOne(20738L)

        // then
        assertThat(translated).isTrue()
        assertThat(prompt.captured).contains("Gimhae, South Gyeongsang, South Korea").contains("Song Kang-ho is")
        assertThat(person.placeOfBirthKo).isEqualTo("대한민국 경상남도 김해")
        assertThat(person.biographyKo).isEqualTo("송강호는 대한민국의 배우다.")
        assertThat(person.biographyKoTranslated).isTrue()
        assertThat(person.biographyEn).isEqualTo("Song Kang-ho is a South Korean actor.")
    }

    @Test
    fun `TMDB 한국어 소개가 있으면 출생지만 묻는다`() {
        // given
        val person = stubPerson(songKangHo.copy(biographyKo = "TMDB 한국어 소개"))
        val prompt = slot<String>()
        every { llmClient.chat(capture(prompt)) } returns """{"placeOfBirth":"대한민국 경상남도 김해","biography":null}"""

        // when
        translator.translateOne(20738L)

        // then
        assertThat(prompt.captured).doesNotContain("Song Kang-ho is")
        assertThat(person.biographyKo).isEqualTo("TMDB 한국어 소개")
        assertThat(person.biographyKoTranslated).isFalse()
    }

    @Test
    fun `번역할 항목이 없으면 LLM을 부르지 않는다`() {
        // given
        stubPerson(songKangHo) { it.applyTranslation(placeOfBirthKo = "대한민국 경상남도 김해", biographyKo = "번역") }

        // when
        val translated = translator.translateOne(20738L)

        // then
        assertThat(translated).isFalse()
        verify(exactly = 0) { llmClient.chat(any()) }
    }

    @Test
    fun `콘텐츠 필터는 현재 버전으로 영구 실패를 기록하고 같은 버전에서는 다시 묻지 않는다`() {
        // given
        val person = stubPerson(songKangHo)
        every { llmClient.chat(any()) } throws LlmContentFilteredException(RuntimeException("1301"))

        // when
        val first = translator.translateOne(20738L)
        val second = translator.translateOne(20738L)

        // then
        assertThat(first).isFalse()
        assertThat(second).isFalse()
        assertThat(person.translationFailedModel).isEqualTo(translator.version())
        assertThat(person.biographyKo).isNull()
        verify(exactly = 1) { llmClient.chat(any()) }
    }

    @Test
    fun `요청한 항목이 모두 비어 돌아오면 영구 실패로 기록한다`() {
        // given
        val person = stubPerson(songKangHo)
        every { llmClient.chat(any()) } returns """{"placeOfBirth":"","biography":null}"""

        // when
        val translated = translator.translateOne(20738L)

        // then
        assertThat(translated).isFalse()
        assertThat(person.translationFailedModel).isEqualTo(translator.version())
    }

    @Test
    fun `API 키가 비어 있으면 아무것도 하지 않는다`() {
        // given
        val noKey = PersonTranslator(llmClient, personRepository, LlmProperties(apiKey = ""), jacksonObjectMapper())

        // when
        val translated = noKey.translateAll(10)

        // then
        assertThat(translated).isZero()
        verify(exactly = 0) { personRepository.findIdsRequiringTranslation(any(), any()) }
    }
}
