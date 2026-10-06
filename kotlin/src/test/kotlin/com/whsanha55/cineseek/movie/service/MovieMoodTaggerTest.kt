package com.whsanha55.cineseek.movie.service

import com.whsanha55.cineseek.external.llm.client.LlmClient
import com.whsanha55.cineseek.external.llm.config.LlmProperties
import com.whsanha55.cineseek.global.exception.ExternalApiException
import com.whsanha55.cineseek.movie.entity.GenreEntity
import com.whsanha55.cineseek.movie.entity.MovieEntity
import com.whsanha55.cineseek.movie.repository.MovieRepository
import io.mockk.Called
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** 분위기 태깅 배치 단위 테스트 — repository/llmClient만 MockK, ObjectMapper는 진짜 인스턴스 */
class MovieMoodTaggerTest {

    private val llmClient = mockk<LlmClient>()
    private val movieRepository = mockk<MovieRepository>()
    private val clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC)
    private lateinit var tagger: MovieMoodTagger

    private lateinit var matrix: MovieEntity

    @BeforeEach
    fun setUp() {
        tagger = MovieMoodTagger(
            llmClient,
            movieRepository,
            LlmProperties(apiKey = "test-key"),
            jacksonObjectMapper(),
            clock,
        )
        matrix = MovieEntity(
            tmdbId = 603L,
            title = "매트릭스",
            originalTitle = "The Matrix",
            overview = "가상현실 속에서 인류의 마지막 희망이 싸운다",
        ).apply { genres += GenreEntity(genreId = 28L, name = "액션") }
    }

    @Test
    fun `tagOne —프롬프트에 제목 원제 장르 줄거리를 담는다`() {
        // given
        every { movieRepository.findAllById(listOf(1L)) } returns listOf(matrix)
        every { movieRepository.save(matrix) } returns matrix
        val prompt = slot<String>()
        every { llmClient.chat(capture(prompt)) } returns """{"tags":["긴장감 있는"],"description":"차갑게 빛나는 SF"}"""

        // when
        tagger.tagOne(1L)

        // then
        assertThat(prompt.captured)
            .contains("매트릭스")
            .contains("The Matrix")
            .contains("액션")
            .contains("가상현실 속에서 인류의 마지막 희망이 싸운다")
    }

    @Test
    fun `tagOne —마크다운 펜스로 감싼 JSON도 파싱해 저장한다`() {
        // given
        every { movieRepository.findAllById(listOf(1L)) } returns listOf(matrix)
        every { movieRepository.save(matrix) } returns matrix
        every { llmClient.chat(any()) } returns "```json\n{\"tags\":[\"잔잔한\",\"서정적\"],\"description\":\"아련한 영화\"}\n```"

        // when
        tagger.tagOne(1L)

        // then
        verify(exactly = 1) { movieRepository.save(matrix) }
        assertThat(matrix.moodTags).isEqualTo("잔잔한, 서정적")
        assertThat(matrix.moodDesc).isEqualTo("아련한 영화")
        assertThat(matrix.moodModel).matches("gpt-4o-mini/mood-[0-9a-f]{8}")
        assertThat(matrix.moodTaggedAt).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"))
    }

    @Test
    fun `tagOne —태그가 비어 있으면 예외를 던지고 저장하지 않는다`() {
        // given
        every { movieRepository.findAllById(listOf(1L)) } returns listOf(matrix)
        every { llmClient.chat(any()) } returns """{"tags":[],"description":"없음"}"""

        // when
        assertThrows<IllegalStateException> { tagger.tagOne(1L) }

        // then
        verify(exactly = 0) { movieRepository.save(any()) }
    }

    @Test
    fun `tagOne —줄거리가 없으면 스킵한다`() {
        // given
        val noOverview = MovieEntity(tmdbId = 1L, title = "줄거리 없음", overview = null)
        every { movieRepository.findAllById(listOf(2L)) } returns listOf(noOverview)

        // when
        tagger.tagOne(2L)

        // then
        verify { llmClient wasNot Called }
        verify(exactly = 0) { movieRepository.save(any()) }
    }

    @Test
    fun `tagAll —API 키가 비어 있으면 0을 반환하고 아무것도 호출하지 않는다`() {
        // given
        val keylessTagger = MovieMoodTagger(
            llmClient,
            movieRepository,
            LlmProperties(apiKey = ""),
            jacksonObjectMapper(),
            clock,
        )

        // when
        val succeeded = keylessTagger.tagAll()

        // then
        assertThat(succeeded).isZero()
        verify { movieRepository wasNot Called }
        verify { llmClient wasNot Called }
    }

    @Test
    fun `tagAll —한 건이 실패해도 나머지를 태깅해 성공 수를 반환한다`() {
        // given
        val id1 = mockk<MovieEntity>()
        every { id1.movieId } returns 1L
        val id2 = mockk<MovieEntity>()
        every { id2.movieId } returns 2L
        every { movieRepository.findRequiringMoodTag(any()) } returns listOf(id1, id2)
        val bleak = MovieEntity(tmdbId = 155L, title = "다크 나이트", overview = "고담의 어둠")
        every { movieRepository.findAllById(listOf(1L)) } returns listOf(matrix)
        every { movieRepository.findAllById(listOf(2L)) } returns listOf(bleak)
        every { movieRepository.save(matrix) } returns matrix
        every { llmClient.chat(any()) } answers {
            if (firstArg<String>().contains("매트릭스")) {
                """{"tags":["긴장감 있는","어두운"],"description":"무거운 영화"}"""
            } else {
                throw ExternalApiException("llm")
            }
        }

        // when
        val succeeded = tagger.tagAll()

        // then
        assertThat(succeeded).isEqualTo(1)
        verify(exactly = 1) { movieRepository.save(matrix) }
        verify(exactly = 0) { movieRepository.save(bleak) }
    }

    @Test
    fun `tagAll limit — 대상을 limit만큼만 가져간다`() {
        // given
        val ids = (1L..3L).map { id ->
            mockk<MovieEntity>().also { every { it.movieId } returns id }
        }
        every { movieRepository.findRequiringMoodTag(any()) } returns ids
        every { movieRepository.findAllById(any()) } returns emptyList() // 태깅 재료 없음 → 조기 스킵

        // when
        tagger.tagAll(limit = 2)

        // then — 3번째 영화는 상한에 걸려 다루지 않는다
        verify(exactly = 1) { movieRepository.findAllById(listOf(1L)) }
        verify(exactly = 1) { movieRepository.findAllById(listOf(2L)) }
        verify(exactly = 0) { movieRepository.findAllById(listOf(3L)) }
        verify { llmClient wasNot Called }
    }

    @Test
    fun `version — 모델명과 프롬프트 해시 8자리를 담는다`() {
        // when
        val version = tagger.version()

        // then
        assertThat(version).matches("gpt-4o-mini/mood-[0-9a-f]{8}")
    }
}
