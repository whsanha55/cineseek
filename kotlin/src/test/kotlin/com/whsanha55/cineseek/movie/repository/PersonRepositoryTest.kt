package com.whsanha55.cineseek.movie.repository

import com.whsanha55.cineseek.global.config.ClockConfig
import com.whsanha55.cineseek.global.config.JpaConfig
import com.whsanha55.cineseek.movie.entity.PersonEntity
import com.whsanha55.cineseek.movie.enums.PersonFilmoStateEnum
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Import
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant

/** PersonEntity — TMDB person id 자연 PK 저장·조회와 필모그래피 상태 갱신 검증 */
@DataJpaTest
@Import(JpaConfig::class, ClockConfig::class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class PersonRepositoryTest {

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:17")
    }

    @Autowired lateinit var personRepository: PersonRepository

    @Test
    fun `person 저장 — TMDB person id를 자연 PK로 그대로 쓴다`() {
        // given
        personRepository.save(PersonEntity(personId = 3895L, name = "크리스찬 베일", profilePath = "/abc.jpg"))

        // when
        val found = personRepository.findById(3895L).orElseThrow()

        // then
        assertThat(found.name).isEqualTo("크리스찬 베일")
        assertThat(found.profilePath).isEqualTo("/abc.jpg")
        assertThat(found.filmoState).isEqualTo(PersonFilmoStateEnum.NONE)
        assertThat(found.filmoCheckedAt).isNull()
        assertThat(found.createdAt).isNotNull()
    }

    @Test
    fun `applyFilmography가 필모그래피 상태를 갱신한다`() {
        // given
        val person = personRepository.save(PersonEntity(personId = 525L, name = "크리스토퍼 놀란"))
        val checkedAt = Instant.parse("2026-02-01T00:00:00Z")

        // when
        person.applyFilmography(checkedAt, externalCount = 120, storedCount = 95, state = PersonFilmoStateEnum.COMPLETE)
        personRepository.saveAndFlush(person)

        // then
        val reloaded = personRepository.findById(525L).orElseThrow()
        assertThat(reloaded.filmoCheckedAt).isEqualTo(checkedAt)
        assertThat(reloaded.filmoExternalCount).isEqualTo(120)
        assertThat(reloaded.filmoStoredCount).isEqualTo(95)
        assertThat(reloaded.filmoState).isEqualTo(PersonFilmoStateEnum.COMPLETE)
    }
}
