package com.whsanha55.cineseek.movie

import com.ninjasquad.springmockk.MockkBean
import com.whsanha55.cineseek.global.exception.ErrorCodeEnum
import com.whsanha55.cineseek.movie.enums.ExploreSortEnum
import com.whsanha55.cineseek.movie.enums.FilmographySortEnum
import com.whsanha55.cineseek.movie.enums.PersonFilmoStateEnum
import com.whsanha55.cineseek.movie.exception.MovieException
import com.whsanha55.cineseek.movie.service.MovieQueryService
import com.whsanha55.cineseek.movie.vo.CastMember
import com.whsanha55.cineseek.movie.vo.FilmographyItem
import com.whsanha55.cineseek.movie.vo.FilmographyPage
import com.whsanha55.cineseek.movie.vo.FilmographyPerson
import com.whsanha55.cineseek.movie.vo.GenreItem
import com.whsanha55.cineseek.movie.vo.MovieCard
import com.whsanha55.cineseek.movie.vo.MovieDetail
import com.whsanha55.cineseek.movie.vo.MoviePage
import com.whsanha55.cineseek.movie.vo.PersonItem
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.time.Instant

@WebMvcTest(MovieController::class)
class MovieControllerTest {

    @Autowired lateinit var mockMvc: MockMvc

    @MockkBean lateinit var movieQueryService: MovieQueryService

    @Test
    fun `상세는 줄거리·감독·출연진 배역을 응답한다`() {
        // given
        every { movieQueryService.detail(1L) } returns MovieDetail(
            card = card(),
            runtime = 144,
            overview = "화성에 홀로 남은 우주비행사",
            directors = listOf(PersonItem(21684L, "리들리 스콧", emptyList())),
            cast = listOf(CastMember(5678L, "맷 데이먼", "마크 와트니")),
        )

        // when
        val result = mockMvc.get("/cineseek/movies/1")

        // then
        result.andExpect {
            status { isOk() }
            jsonPath("$.title") { value("마션") }
            jsonPath("$.runtime") { value(144) }
            jsonPath("$.directors[0].personId") { value(21684) }
            jsonPath("$.cast[0].character") { value("마크 와트니") }
        }
    }

    @Test
    fun `없는 영화는 404 MOVIE_NOT_FOUND로 응답한다`() {
        // given
        every { movieQueryService.detail(99L) } throws MovieException(ErrorCodeEnum.MOVIE_NOT_FOUND)

        // when
        val result = mockMvc.get("/cineseek/movies/99")

        // then
        result.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("MOVIE_NOT_FOUND") }
        }
    }

    @Test
    fun `탐색은 page를 서비스에 넘기고 카드에는 score가 없다`() {
        // given
        every { movieQueryService.explore(28L, ExploreSortEnum.VOTE_COUNT, 1, 20, null, null) } returns
            MoviePage(items = listOf(card()), limit = 20, offset = 20, hasNext = false)

        // when
        val result = mockMvc.get("/cineseek/movies") {
            param("genreId", "28")
            param("sort", "vote_count")
            param("page", "1")
        }

        // then
        result.andExpect {
            status { isOk() }
            jsonPath("$.items[0].movieId") { value(1) }
            jsonPath("$.items[0].score") { doesNotExist() }
            jsonPath("$.page.offset") { value(20) }
        }
    }

    @Test
    fun `탐색은 평점·투표 수 필터를 서비스에 전달한다`() {
        // given
        every {
            movieQueryService.explore(null, ExploreSortEnum.RATING, 0, 20, null, null, 7.5, 50, 500)
        } returns MoviePage(items = listOf(card()), limit = 20, offset = 0, hasNext = false)

        // when
        val result = mockMvc.get("/cineseek/movies") {
            param("ratingMin", "7.5")
            param("voteCountMin", "50")
            param("voteCountMax", "500")
        }

        // then
        result.andExpect {
            status { isOk() }
            jsonPath("$.items[0].movieId") { value(1) }
            jsonPath("$.page.offset") { value(0) }
        }
    }

    @Test
    fun `잘못된 sort는 400으로 응답한다`() {
        // when
        val result = mockMvc.get("/cineseek/movies") { param("sort", "popular") }

        // then
        result.andExpect {
            status { isBadRequest() }
            jsonPath("$.errors[0].field") { value("sort") }
        }
    }

    @Test
    fun `장르 목록을 응답한다`() {
        // given
        every { movieQueryService.genres() } returns listOf(GenreItem(28L, "액션"))

        // when
        val result = mockMvc.get("/cineseek/genres")

        // then
        result.andExpect {
            status { isOk() }
            jsonPath("$.items[0].id") { value(28) }
            jsonPath("$.items[0].name") { value("액션") }
        }
    }

    @Test
    fun `인물 자동완성은 personId·role·knownFor를 응답한다`() {
        // given
        every { movieQueryService.people("봉", "director") } returns
            listOf(PersonItem(21684L, "봉준호", listOf("기생충", "살인의 추억")))

        // when
        val result = mockMvc.get("/cineseek/people") {
            param("q", "봉")
            param("role", "director")
        }

        // then
        result.andExpect {
            status { isOk() }
            jsonPath("$.items[0].personId") { value(21684) }
            jsonPath("$.items[0].role") { value("director") }
            jsonPath("$.items[0].knownFor[0]") { value("기생충") }
        }
    }

    @Test
    fun `잘못된 role은 400으로 응답한다`() {
        // when
        val result = mockMvc.get("/cineseek/people") {
            param("q", "봉")
            param("role", "producer")
        }

        // then
        result.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `인물 단건 조회 — 칩 라벨 복원`() {
        // given
        every { movieQueryService.person(3895L, "cast") } returns PersonItem(3895L, "크리스찬 베일", listOf("다크 나이트"))

        // when
        val result = mockMvc.get("/cineseek/people/3895") { param("role", "cast") }

        // then
        result.andExpect {
            status { isOk() }
            jsonPath("$.name") { value("크리스찬 베일") }
            jsonPath("$.role") { value("cast") }
        }
    }

    @Test
    fun `필모그래피 — 사람 요약·배역·collecting을 응답한다`() {
        // given
        every {
            movieQueryService.filmography(3895L, FilmographySortEnum.RELEASE, 0, 20, null)
        } returns FilmographyPage(
            person = FilmographyPerson(
                personId = 3895L,
                name = "크리스찬 베일",
                profilePath = "/bale.jpg",
                filmoState = PersonFilmoStateEnum.COMPLETE,
                filmoCheckedAt = Instant.parse("2026-10-01T00:00:00Z"),
                totalWorks = 47,
            ),
            items = listOf(FilmographyItem(card(), "브루스 웨인")),
            limit = 20,
            offset = 0,
            hasNext = false,
            collecting = true,
        )

        // when
        val result = mockMvc.get("/cineseek/people/3895/filmography")

        // then
        result.andExpect {
            status { isOk() }
            jsonPath("$.person.personId") { value(3895) }
            jsonPath("$.person.name") { value("크리스찬 베일") }
            jsonPath("$.person.profilePath") { value("/bale.jpg") }
            jsonPath("$.person.filmoState") { value("COMPLETE") }
            jsonPath("$.person.totalWorks") { value(47) }
            jsonPath("$.items[0].movieId") { value(1) }
            jsonPath("$.items[0].character") { value("브루스 웨인") }
            jsonPath("$.items[0].genres[0].name") { value("SF") }
            jsonPath("$.page.offset") { value(0) }
            jsonPath("$.page.hasNext") { value(false) }
            jsonPath("$.collecting") { value(true) }
        }
    }

    @Test
    fun `필모그래피 — sort·page·limit·excludeMovieId를 서비스에 전달한다`() {
        // given
        every { movieQueryService.filmography(3895L, FilmographySortEnum.RATING, 1, 5, 155L) } returns filmographyPage()

        // when
        val result = mockMvc.get("/cineseek/people/3895/filmography") {
            param("sort", "rating")
            param("page", "1")
            param("limit", "5")
            param("excludeMovieId", "155")
        }

        // then
        result.andExpect { status { isOk() } }
        verify { movieQueryService.filmography(3895L, FilmographySortEnum.RATING, 1, 5, 155L) }
    }

    @Test
    fun `필모그래피 — 없는 사람은 404 PERSON_NOT_FOUND로 응답한다`() {
        // given
        every { movieQueryService.filmography(99L, FilmographySortEnum.RELEASE, 0, 20, null) } throws
            MovieException(ErrorCodeEnum.PERSON_NOT_FOUND)

        // when
        val result = mockMvc.get("/cineseek/people/99/filmography")

        // then
        result.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("PERSON_NOT_FOUND") }
        }
    }

    @Test
    fun `필모그래피 — 잘못된 sort는 400으로 응답한다`() {
        // when
        val result = mockMvc.get("/cineseek/people/3895/filmography") { param("sort", "popular") }

        // then
        result.andExpect {
            status { isBadRequest() }
            jsonPath("$.errors[0].field") { value("sort") }
        }
    }

    private fun filmographyPage() = FilmographyPage(
        person = FilmographyPerson(personId = 3895L, name = "크리스찬 베일", totalWorks = 1),
        items = listOf(FilmographyItem(card(), "브루스 웨인")),
        limit = 5,
        offset = 5,
        hasNext = false,
        collecting = false,
    )

    private fun card() = MovieCard(
        movieId = 1L,
        title = "마션",
        originalTitle = "The Martian",
        releaseYear = 2015,
        rating = 8.0,
        voteCount = 21000,
        posterPath = "/example.jpg",
        genres = listOf(GenreItem(878L, "SF")),
    )
}
