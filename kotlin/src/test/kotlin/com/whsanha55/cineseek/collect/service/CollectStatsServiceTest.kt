package com.whsanha55.cineseek.collect.service

import com.whsanha55.cineseek.collect.config.CollectProperties
import com.whsanha55.cineseek.movie.entity.PersonEntity
import com.whsanha55.cineseek.movie.enums.PersonFilmoStateEnum
import com.whsanha55.cineseek.movie.repository.MovieCastRepository
import com.whsanha55.cineseek.movie.repository.PersonRepository
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

/** CollectStatsService — 시드 확보율이 필모 수집 시점 값이 아니라 현재 movie_cast 수를 쓰는지 검증한다 */
class CollectStatsServiceTest {

    private val movieCastRepository = mockk<MovieCastRepository>(relaxed = true)
    private val personRepository = mockk<PersonRepository>(relaxed = true)

    private val service = CollectStatsService(
        collectTaskRepository = mockk(relaxed = true),
        movieRepository = mockk(relaxed = true),
        movieCastRepository = movieCastRepository,
        personRepository = personRepository,
        indexer = mockk(relaxed = true),
        properties = CollectProperties(seedPersonIds = listOf(1L, 2L)),
    )

    @Test
    fun `시드 저장 수는 movie_cast 현재 수 — 수집 전 시드는 null`() {
        val person = PersonEntity(personId = 1L, name = "배우").apply {
            applyFilmography(
                checkedAt = Instant.EPOCH,
                externalCount = 60,
                storedCount = 10,
                state = PersonFilmoStateEnum.COLLECTING,
            )
        }
        every { personRepository.findAllById(listOf(1L, 2L)) } returns listOf(person)
        every { movieCastRepository.countByPersonId(1L) } returns 55L

        val coverage = service.snapshot().seedCoverage

        assertThat(coverage.map { it.storedCount }).containsExactly(55, null)
        assertThat(coverage.map { it.externalCount }).containsExactly(60, null)
    }
}
