package com.whsanha55.cineseek.movie.service

import com.whsanha55.cineseek.global.config.ClockConfig
import com.whsanha55.cineseek.global.config.JpaConfig
import com.whsanha55.cineseek.global.exception.ErrorCodeEnum
import com.whsanha55.cineseek.movie.entity.GenreEntity
import com.whsanha55.cineseek.movie.entity.MovieCastEntity
import com.whsanha55.cineseek.movie.entity.MovieDirectorEntity
import com.whsanha55.cineseek.movie.entity.MovieEntity
import com.whsanha55.cineseek.movie.entity.PersonEntity
import com.whsanha55.cineseek.movie.enums.ExploreSortEnum
import com.whsanha55.cineseek.movie.enums.FilmographySortEnum
import com.whsanha55.cineseek.movie.enums.PersonFilmoStateEnum
import com.whsanha55.cineseek.movie.exception.MovieException
import com.whsanha55.cineseek.movie.repository.GenreRepository
import com.whsanha55.cineseek.movie.repository.MovieCastRepository
import com.whsanha55.cineseek.movie.repository.MovieDirectorRepository
import com.whsanha55.cineseek.movie.repository.MovieRepository
import com.whsanha55.cineseek.movie.repository.PersonRepository
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
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** 카드 조립·탐색 정렬·상세·인물·필모그래피 검색이 PG(SoT)와 맞물리는지 — 실제 PG 컨테이너로 검증 */
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

        private const val ACTOR_ID = 7777L
        private const val ACTOR_NAME = "테스트 배우"
        private val now = Instant.parse("2026-10-01T00:00:00Z")
        private val fixedClock = Clock.fixed(now, ZoneOffset.UTC)
    }

    @Autowired lateinit var movieRepository: MovieRepository

    @Autowired lateinit var genreRepository: GenreRepository

    @Autowired lateinit var movieDirectorRepository: MovieDirectorRepository

    @Autowired lateinit var movieCastRepository: MovieCastRepository

    @Autowired lateinit var personRepository: PersonRepository

    private lateinit var service: MovieQueryService
    private lateinit var refreshRequester: FakeFilmographyRefreshRequester
    private var darkKnight = 0L
    private var matrix = 0L
    private var laLaLand = 0L
    private var debut = 0L

    @BeforeEach
    fun setUp() {
        refreshRequester = FakeFilmographyRefreshRequester()
        service = MovieQueryService(
            movieRepository,
            movieDirectorRepository,
            movieCastRepository,
            genreRepository,
            personRepository,
            refreshRequester,
            fixedClock,
        )
        seed()
    }

    @Test
    fun `cards — id 목록을 장르 포함 카드로 만들고 originalTitle 없음은 null`() {
        // when
        val cards = service.cards(listOf(matrix, darkKnight, 999_999L))

        // then
        assertThat(cards.keys).containsExactlyInAnyOrder(matrix, darkKnight)
        assertThat(cards.getValue(matrix).originalTitle).isNull()
        assertThat(cards.getValue(darkKnight).genres.map { it.name }).containsExactly("액션")
    }

    @Test
    fun `explore rating 정렬 — 최소 투표 수 미만은 제외한다`() {
        // when
        val first = service.explore(null, ExploreSortEnum.RATING, page = 0, limit = 1)
        val all = service.explore(null, ExploreSortEnum.RATING, page = 0, limit = 10)

        // then — 매트릭스(9.0, 999표)는 MIN_VOTES(1000) 미만이라 빠진다
        assertThat(first.items.map { it.movieId }).containsExactly(darkKnight)
        assertThat(first.hasNext).isTrue()
        assertThat(all.items.map { it.movieId }).containsExactly(darkKnight, laLaLand)
    }

    @Test
    fun `explore genreId 필터와 vote_count 정렬`() {
        // when
        val byGenre = service.explore(28L, ExploreSortEnum.RELEASE, page = 0, limit = 10)
        val byVotes = service.explore(null, ExploreSortEnum.VOTE_COUNT, page = 0, limit = 10)

        // then
        assertThat(byGenre.items.map { it.movieId }).containsExactly(darkKnight, matrix)
        assertThat(byVotes.items.map { it.voteCount }).containsExactly(3000, 1000, 999)
    }

    @Test
    fun `explore 인물 필터 — 여러 작품에 참여한 감독도 조회된다`() {
        // when
        val byDirector = service.explore(null, ExploreSortEnum.RELEASE, page = 0, limit = 10, directorId = 525L)
        val byCast = service.explore(null, ExploreSortEnum.RELEASE, page = 0, limit = 10, castId = 3895L)

        // then
        assertThat(byDirector.items.map { it.movieId }).containsExactly(darkKnight, matrix)
        assertThat(byCast.items.map { it.movieId }).containsExactly(darkKnight)
    }

    @Test
    fun `explore 숨은 명작 — 평점·투표 수 범위를 명시하면 암시 하한 1000 대신 명시값을 쓴다`() {
        // given — 투표 200표 고평점(숨은 명작)과 2000표 고평점 대작
        val action = genreRepository.findById(28L).orElseThrow()
        val hiddenGem = save(
            MovieEntity(
                tmdbId = 761_053L,
                title = "숨은 명작",
                overview = "표는 적지만 평가가 좋은 영화",
                releaseDate = LocalDate.of(1995, 10, 20),
                releaseYear = 1995,
                voteAverage = BigDecimal("7.8"),
                voteCount = 200,
            ),
            action,
        )
        save(
            MovieEntity(
                tmdbId = 696_374L,
                title = "흥행 대작",
                overview = "표가 많고 평가도 좋은 영화",
                releaseDate = LocalDate.of(2023, 7, 19),
                releaseYear = 2023,
                voteAverage = BigDecimal("8.2"),
                voteCount = 2000,
            ),
            action,
        )

        // when
        val page = service.explore(
            null,
            ExploreSortEnum.RATING,
            page = 0,
            limit = 10,
            ratingMin = 7.5,
            voteCountMin = 50,
            voteCountMax = 500,
        )

        // then — 명시 하한 50이 암시 하한 1000을 대체하고, 2000표 대작과 999~3000표 기존 fixture는 상한 500에 걸려 빠진다
        assertThat(page.items.map { it.movieId }).containsExactly(hiddenGem)
    }

    @Test
    fun `explore voteCountMin 없이 ratingMin만 쓰면 소수 투표 고평점 작품이 섞인다`() {
        // when
        val byRelease = service.explore(null, ExploreSortEnum.RELEASE, page = 0, limit = 10, ratingMin = 7.5)
        val byRating = service.explore(null, ExploreSortEnum.RATING, page = 0, limit = 10, ratingMin = 7.5)

        // then — 평점순이 아니면 암시 하한이 없어 매트릭스(9.0, 999표)가 그대로 섞이고, 평점순은 기존대로 암시 하한 1000을 유지한다
        assertThat(byRelease.items.map { it.movieId }).containsExactly(laLaLand, darkKnight, matrix)
        assertThat(byRating.items.map { it.movieId }).containsExactly(darkKnight, laLaLand)
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

    @Test
    fun `filmography release 정렬 — 최신 개봉순 기본, 캐릭터 매핑, 투표 수가 적어도 포함`() {
        // given
        seedActorFilmography()

        // when
        val page = service.filmography(ACTOR_ID, FilmographySortEnum.RELEASE, page = 0, limit = 10)

        // then — 데뷔 무명작(5표)도 탐색의 암시 하한(1000)과 무관하게 포함된다
        assertThat(page.items.map { it.card.movieId }).containsExactly(laLaLand, darkKnight, matrix, debut)
        assertThat(page.items.map { it.character }).containsExactly("오디션 배우", "조커", "네오", "단역")
        assertThat(page.person.totalWorks).isEqualTo(4)
        assertThat(page.hasNext).isFalse()
    }

    @Test
    fun `filmography rating 정렬 — 투표 수 하한 없이 평점순`() {
        // given
        seedActorFilmography()

        // when
        val page = service.filmography(ACTOR_ID, FilmographySortEnum.RATING, page = 0, limit = 10)

        // then — 데뷔 무명작(9.5, 5표)이 1위. 탐색이었다면 MIN_VOTES(1000)로 빠졌을 것이다
        assertThat(page.items.map { it.card.movieId }).containsExactly(debut, matrix, darkKnight, laLaLand)
    }

    @Test
    fun `filmography 페이지네이션 — page·limit·hasNext`() {
        // given
        seedActorFilmography()

        // when
        val first = service.filmography(ACTOR_ID, FilmographySortEnum.RELEASE, page = 0, limit = 2)
        val second = service.filmography(ACTOR_ID, FilmographySortEnum.RELEASE, page = 1, limit = 2)

        // then
        assertThat(first.items.map { it.card.movieId }).containsExactly(laLaLand, darkKnight)
        assertThat(first.limit).isEqualTo(2)
        assertThat(first.offset).isZero()
        assertThat(first.hasNext).isTrue()
        assertThat(second.items.map { it.card.movieId }).containsExactly(matrix, debut)
        assertThat(second.offset).isEqualTo(2)
        assertThat(second.hasNext).isFalse()
    }

    @Test
    fun `filmography excludeMovieId — 현재 영화를 제외해도 totalWorks는 전체 수`() {
        // given
        seedActorFilmography()

        // when
        val page = service.filmography(
            ACTOR_ID,
            FilmographySortEnum.RELEASE,
            page = 0,
            limit = 10,
            excludeMovieId = laLaLand,
        )

        // then
        assertThat(page.items.map { it.card.movieId }).containsExactly(darkKnight, matrix, debut)
        assertThat(page.person.totalWorks).isEqualTo(4)
    }

    @Test
    fun `filmography — 동일 영화가 중복 없이 한 번만 나온다 (movie_cast PK 보장)`() {
        // given
        seedActorFilmography()

        // when
        val page = service.filmography(ACTOR_ID, FilmographySortEnum.RELEASE, page = 0, limit = 50)

        // then
        val movieIds = page.items.map { it.card.movieId }
        assertThat(movieIds.distinct()).containsExactlyElementsOf(movieIds)
        assertThat(movieIds).hasSize(page.person.totalWorks.toInt())
    }

    @Test
    fun `filmography — person 마스터가 없으면 movie_cast 이름으로 대체하고 갱신을 요청한다`() {
        // given
        seedActorFilmography()

        // when
        val page = service.filmography(ACTOR_ID, FilmographySortEnum.RELEASE, page = 0, limit = 10)

        // then — filmo_checked_at가 없으므로(=미확인) 우선 갱신 요청
        assertThat(page.person.name).isEqualTo(ACTOR_NAME)
        assertThat(page.person.profilePath).isNull()
        assertThat(page.person.filmoState).isEqualTo(PersonFilmoStateEnum.NONE)
        assertThat(page.person.filmoCheckedAt).isNull()
        assertThat(refreshRequester.requested).containsExactly(ACTOR_ID)
        assertThat(page.collecting).isFalse()
    }

    @Test
    fun `filmography — person 마스터가 있고 확인이 최근(TTL 이내)이면 갱신을 요청하지 않는다`() {
        // given
        seedActorFilmography()
        saveActor(checkedAt = now.minus(Duration.ofDays(29)), profilePath = "/actor.jpg")
        refreshRequester.pending.add(ACTOR_ID)

        // when
        val page = service.filmography(ACTOR_ID, FilmographySortEnum.RELEASE, page = 0, limit = 10)

        // then
        assertThat(page.person.name).isEqualTo(ACTOR_NAME)
        assertThat(page.person.profilePath).isEqualTo("/actor.jpg")
        assertThat(page.person.filmoState).isEqualTo(PersonFilmoStateEnum.COMPLETE)
        assertThat(page.person.filmoCheckedAt).isEqualTo(now.minus(Duration.ofDays(29)))
        assertThat(refreshRequester.requested).isEmpty()
        assertThat(page.collecting).isTrue()
    }

    @Test
    fun `filmography — 확인이 30일을 넘으면 갱신을 요청한다`() {
        // given
        seedActorFilmography()
        saveActor(checkedAt = now.minus(Duration.ofDays(31)))

        // when
        service.filmography(ACTOR_ID, FilmographySortEnum.RELEASE, page = 0, limit = 10)

        // then
        assertThat(refreshRequester.requested).containsExactly(ACTOR_ID)
    }

    @Test
    fun `filmography — 어느 테이블에도 없는 사람은 PERSON_NOT_FOUND, 갱신도 요청하지 않는다`() {
        // when
        val e = assertThrows<MovieException> {
            service.filmography(999_999L, FilmographySortEnum.RELEASE, page = 0, limit = 10)
        }

        // then
        assertThat(e.errorCode).isEqualTo(ErrorCodeEnum.PERSON_NOT_FOUND)
        assertThat(refreshRequester.requested).isEmpty()
    }

    /** 매트릭스(originalTitle 없음, 투표 999) · 다크나이트(8.5/3000표) · 라라랜드(8.0/1000표). 놀란은 두 작품 */
    private fun seed() {
        val action = genreRepository.save(GenreEntity(genreId = 28L, name = "액션"))
        val romance = genreRepository.save(GenreEntity(genreId = 10749L, name = "로맨스"))

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

    /**
     * 필모그래피 픽스처 — 배우 7777이 기존 3편 + 데뷔 무명작(9.5점, 5표)에 출연.
     * 데뷔작만 개별 시드하는 이유는 전역 seed에 넣으면 기존 탐색 테스트(vote_count 정렬 등)가 깨지기 때문
     */
    private fun seedActorFilmography() {
        movieCastRepository.save(
            MovieCastEntity(
                movieId = laLaLand,
                personId = ACTOR_ID,
                name = ACTOR_NAME,
                character = "오디션 배우",
                castOrder = 3,
            ),
        )
        movieCastRepository.save(
            MovieCastEntity(
                movieId = darkKnight,
                personId = ACTOR_ID,
                name = ACTOR_NAME,
                character = "조커",
                castOrder = 6,
            ),
        )
        movieCastRepository.save(
            MovieCastEntity(
                movieId = matrix,
                personId = ACTOR_ID,
                name = ACTOR_NAME,
                character = "네오",
                castOrder = 7,
            ),
        )
        val drama = genreRepository.save(GenreEntity(genreId = 18L, name = "Drama"))
        debut = save(
            MovieEntity(
                tmdbId = 999_001L,
                title = "데뷔 무명작",
                overview = "표는 5표뿐인 데뷔 작품",
                releaseDate = LocalDate.of(1990, 5, 1),
                releaseYear = 1990,
                voteAverage = BigDecimal("9.5"),
                voteCount = 5,
            ),
            drama,
        )
        movieCastRepository.save(
            MovieCastEntity(movieId = debut, personId = ACTOR_ID, name = ACTOR_NAME, character = "단역", castOrder = 0),
        )
    }

    /** person 마스터 저장 — checkedAt이 null이면 확인 이력 없는 상태(NONE) 그대로 둔다 */
    private fun saveActor(checkedAt: Instant?, profilePath: String? = null) {
        personRepository.save(
            PersonEntity(personId = ACTOR_ID, name = ACTOR_NAME, profilePath = profilePath).apply {
                checkedAt?.let {
                    applyFilmography(
                        checkedAt = it,
                        externalCount = 4,
                        storedCount = 4,
                        state = PersonFilmoStateEnum.COMPLETE,
                    )
                }
            },
        )
    }
}

/** 포트 가짜 — 갱신 요청과 대기 중 작업만 기록한다 (실제 구현은 collect 도메인에 있다) */
private class FakeFilmographyRefreshRequester : FilmographyRefreshRequester {
    val requested = mutableSetOf<Long>()
    val pending = mutableSetOf<Long>()

    override fun requestRefresh(personId: Long) {
        requested += personId
    }

    override fun hasPendingWork(personId: Long) = personId in pending
}
