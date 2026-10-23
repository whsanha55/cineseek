package com.whsanha55.cineseek.collect.job

import com.whsanha55.cineseek.external.tmdb.client.TmdbClient
import com.whsanha55.cineseek.movie.entity.PersonEntity
import com.whsanha55.cineseek.movie.repository.MovieCastRepository
import com.whsanha55.cineseek.movie.repository.PersonRepository
import com.whsanha55.cineseek.movie.service.PersonTranslator
import com.whsanha55.cineseek.movie.vo.TmdbPersonInfo
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.Optional

/** PersonDetailsJob — 상세 저장(404·실패 건너뜀, 필모 미확인 유지)과 번역 묶음 호출을 MockK 목으로 검증한다 */
class PersonDetailsJobTest {

    private val movieCastRepository = mockk<MovieCastRepository>()
    private val personRepository = mockk<PersonRepository>()
    private val tmdbClient = mockk<TmdbClient>()
    private val personTranslator = mockk<PersonTranslator>()
    private val job = PersonDetailsJob(movieCastRepository, personRepository, tmdbClient, personTranslator)

    @Test
    fun `출연 N편 이상 인물의 TMDB 상세를 저장하고 404와 실패는 건너뛴다`() {
        // given
        every { movieCastRepository.findPersonIdsWithoutDetails(5L) } returns listOf(1L, 2L, 3L)
        every { tmdbClient.fetchPerson(1L) } returns
            TmdbPersonInfo(personId = 1L, name = "송강호", profilePath = null, birthday = LocalDate.of(1967, 1, 17))
        every { tmdbClient.fetchPerson(2L) } returns null
        every { tmdbClient.fetchPerson(3L) } throws RuntimeException("timeout")
        every { personRepository.findById(1L) } returns Optional.empty()
        val saved = mutableListOf<PersonEntity>()
        every { personRepository.save(capture(saved)) } answers { firstArg() }

        // when
        val count = job.fetchDetails(minWorks = 5L)

        // then
        assertThat(count).isEqualTo(1)
        val person = saved.single()
        assertThat(person.personId).isEqualTo(1L)
        assertThat(person.birthday).isEqualTo(LocalDate.of(1967, 1, 17))
        assertThat(person.filmoCheckedAt).isNull()
    }

    @Test
    fun `번역이 남은 인물 전체를 200명 묶음으로 번역한다`() {
        // given
        val ids = (1L..450L).toList()
        every { personTranslator.version() } returns "v1"
        every { personRepository.findIdsRequiringTranslation("v1", any()) } returns ids
        every { personTranslator.translate(any()) } answers { firstArg<List<Long>>().size }

        // when
        val translated = job.translateAll()

        // then
        assertThat(translated).isEqualTo(450)
        verify(exactly = 1) { personTranslator.translate(ids.subList(0, 200)) }
        verify(exactly = 1) { personTranslator.translate(ids.subList(200, 400)) }
        verify(exactly = 1) { personTranslator.translate(ids.subList(400, 450)) }
    }
}
