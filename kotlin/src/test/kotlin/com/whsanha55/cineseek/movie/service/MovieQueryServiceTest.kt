package com.whsanha55.cineseek.movie.service

import com.whsanha55.cineseek.global.config.ClockConfig
import com.whsanha55.cineseek.global.config.JpaConfig
import com.whsanha55.cineseek.global.exception.ErrorCodeEnum
import com.whsanha55.cineseek.movie.entity.GenreEntity
import com.whsanha55.cineseek.movie.entity.MovieCastEntity
import com.whsanha55.cineseek.movie.entity.MovieDirectorEntity
import com.whsanha55.cineseek.movie.entity.MovieEntity
import com.whsanha55.cineseek.movie.enums.ExploreSortEnum
import com.whsanha55.cineseek.movie.exception.MovieException
import com.whsanha55.cineseek.movie.repository.GenreRepository
import com.whsanha55.cineseek.movie.repository.MovieCastRepository
import com.whsanha55.cineseek.movie.repository.MovieDirectorRepository
import com.whsanha55.cineseek.movie.repository.MovieRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Import
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.math.BigDecimal
import java.time.LocalDate

/** 카드 조립·탐색 정렬·상세·인물 검색이 PG(SoT)와 맞물리는지 — 실제 PG 컨테이너로 검증 */
@DataJpaTest
@Import(JpaConfig::class, ClockConfig::class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class MovieQueryServiceTest {

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:17")
    }

    @Autowired lateinit var movieRepository: MovieRepository

    @Autowired lateinit var genreRepository: GenreRepository

    @Autowired lateinit var movieDirectorRepository: MovieDirectorRepository

    @Autowired lateinit var movieCastRepository: MovieCastRepository

    private lateinit var service: MovieQueryService
    private var darkKnight = 0L
    private var matrix = 0L
    private var laLaLand = 0L

    @BeforeEach
    fun setUp() {
        service = MovieQueryService(movieRepository, movieDirectorRepository, movieCastRepository, genreRepository)
        seed()
    }

    @Test
    fun `cards — id 목록을 장르 포함 카드로 만들고 originalTitle 없음은 null`() {
        // when
        val cards = service.cards(listOf(matrix, darkKnight, 999_999L))

        // then
        assertThat(cards.keys).containsExactlyInAnyOrder(matrix, darkKnight)
        assertThat(cards.getValue(matrix).originalTitle).isNull()
        assertThat(cards.getValue(darkKnight).genres.map { it.nameKo }).containsExactly("액션")
    }

    @Test
    fun `explore rating 정렬 — 최소 투표 수 미만은 제외한다`() {
        // when
        val first = service.explore(null, ExploreSortEnum.RATING, offset = 0, limit = 1)
        val all = service.explore(null, ExploreSortEnum.RATING, offset = 0, limit = 10)

        // then — 매트릭스(9.0, 999표)는 MIN_VOTES(1000) 미만이라 빠진다
        assertThat(first.items.map { it.movieId }).containsExactly(darkKnight)
        assertThat(first.hasNext).isTrue()
        assertThat(all.items.map { it.movieId }).containsExactly(darkKnight, laLaLand)
    }

    @Test
    fun `explore genreId 필터와 vote_count 정렬`() {
        // when
        val byGenre = service.explore(28L, ExploreSortEnum.RELEASE, offset = 0, limit = 10)
        val byVotes = service.explore(null, ExploreSortEnum.VOTE_COUNT, offset = 0, limit = 10)

        // then
        assertThat(byGenre.items.map { it.movieId }).containsExactly(darkKnight, matrix)
        assertThat(byVotes.items.map { it.voteCount }).containsExactly(3000, 1000, 999)
    }

    @Test
    fun `explore 인물 필터 — 여러 작품에 참여한 감독도 조회된다`() {
        // when
        val byDirector = service.explore(null, ExploreSortEnum.RELEASE, offset = 0, limit = 10, directorId = 525L)
        val byCast = service.explore(null, ExploreSortEnum.RELEASE, offset = 0, limit = 10, castId = 3895L)

        // then
        assertThat(byDirector.items.map { it.movieId }).containsExactly(darkKnight, matrix)
        assertThat(byCast.items.map { it.movieId }).containsExactly(darkKnight)
    }

    @Test
    fun `detail — 출연진은 castOrder 상위 5명, 감독 포함`() {
        // when
        val detail = service.detail(darkKnight)

        // then
        assertThat(detail.overview).isEqualTo("배트맨이 조커를 막는 이야기")
        assertThat(detail.directors.map { it.name }).containsExactly("크리스토퍼 놀란")
        assertThat(detail.cast).hasSize(5)
        assertThat(detail.cast.map { it.character }.take(2)).containsExactly("브루스 웨인", "조연 1")
    }

    @Test
    fun `detail — 없는 영화는 MOVIE_NOT_FOUND`() {
        // when
        val e = assertThrows<MovieException> { service.detail(999_999L) }

        // then
        assertThat(e.errorCode).isEqualTo(ErrorCodeEnum.MOVIE_NOT_FOUND)
    }

    @Test
    fun `people — 접두어 검색 + knownFor, role 구분`() {
        // when
        val directors = service.people("크리스", "director")
        val cast = service.people("크리스", "cast")
        val person = service.person(525L, "director")

        // then
        assertThat(directors.map { it.name }).containsExactly("크리스토퍼 놀란")
        assertThat(directors.single().knownFor).containsExactly("다크 나이트", "매트릭스")
        assertThat(cast.map { it.name }).containsExactly("크리스찬 베일")
        assertThat(person.name).isEqualTo("크리스토퍼 놀란")
        assertThrows<MovieException> { service.person(999_999L, "director") }
    }

    /** 매트릭스(originalTitle 없음, 투표 999) · 다크나이트(8.5/3000표) · 라라랜드(8.0/1000표). 놀란은 두 작품 */
    private fun seed() {
        val action = genreRepository.save(GenreEntity(genreId = 28L, name = "Action", nameKo = "액션"))
        val romance = genreRepository.save(GenreEntity(genreId = 10749L, name = "Romance", nameKo = "로맨스"))

        matrix = save(
            MovieEntity(
                tmdbId = 603L,
                title = "매트릭스",
                overview = "가상현실 세계의 이야기",
                releaseDate = LocalDate.of(1999, 3, 31),
                releaseYear = 1999,
                voteAverage = BigDecimal("9.0"),
                voteCount = 999,
            ),
            action,
        )
        darkKnight = save(
            MovieEntity(
                tmdbId = 155L,
                title = "다크 나이트",
                originalTitle = "The Dark Knight",
                overview = "배트맨이 조커를 막는 이야기",
                releaseDate = LocalDate.of(2008, 7, 16),
                releaseYear = 2008,
                voteAverage = BigDecimal("8.5"),
                voteCount = 3000,
                runtime = 152,
            ),
            action,
        )
        laLaLand = save(
            MovieEntity(
                tmdbId = 313369L,
                title = "라라랜드",
                overview = "재즈 피아니스트와 배우의 사랑",
                releaseDate = LocalDate.of(2016, 12, 7),
                releaseYear = 2016,
                voteAverage = BigDecimal("8.0"),
                voteCount = 1000,
            ),
            romance,
        )

        seedPeople()
    }

    private fun seedPeople() {
        movieDirectorRepository.save(MovieDirectorEntity(movieId = darkKnight, personId = 525L, name = "크리스토퍼 놀란"))
        movieDirectorRepository.save(MovieDirectorEntity(movieId = matrix, personId = 525L, name = "크리스토퍼 놀란"))
        movieCastRepository.save(
            MovieCastEntity(
                movieId = darkKnight,
                personId = 3895L,
                name = "크리스찬 베일",
                character = "브루스 웨인",
                castOrder = 0,
            ),
        )
        (1..5).forEach { i ->
            movieCastRepository.save(
                MovieCastEntity(
                    movieId = darkKnight,
                    personId = 1000L + i,
                    name = "출연자 $i",
                    character = "조연 $i",
                    castOrder = i,
                ),
            )
        }
    }

    private fun save(movie: MovieEntity, genre: GenreEntity): Long {
        movie.replaceGenres(listOf(genre))
        return requireNotNull(movieRepository.save(movie).movieId)
    }
}
