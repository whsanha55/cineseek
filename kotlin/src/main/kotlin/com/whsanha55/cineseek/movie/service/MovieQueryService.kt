package com.whsanha55.cineseek.movie.service

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
import com.whsanha55.cineseek.movie.vo.CastMember
import com.whsanha55.cineseek.movie.vo.GenreItem
import com.whsanha55.cineseek.movie.vo.MovieCard
import com.whsanha55.cineseek.movie.vo.MovieDetail
import com.whsanha55.cineseek.movie.vo.MoviePage
import com.whsanha55.cineseek.movie.vo.PersonItem
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import java.math.BigDecimal

/**
 * 읽기 전용 영화 조회 — 상세·탐색·장르·인물. 검색·유사 결과의 카드 조립(cards)도 여기서 한다.
 * 표시 필드는 항상 PG(SoT)에서 만든다
 */
@Service
class MovieQueryService(
    private val movieRepository: MovieRepository,
    private val movieDirectorRepository: MovieDirectorRepository,
    private val movieCastRepository: MovieCastRepository,
    private val genreRepository: GenreRepository,
) {

    /** id 목록 → 카드 (genres 즉시 로딩). 순서는 호출자가 map 조회로 유지한다 */
    fun cards(ids: List<Long>): Map<Long, MovieCard> = if (ids.isEmpty()) {
        emptyMap()
    } else {
        movieRepository.findAllById(ids).associate { requireNotNull(it.movieId) to it.toCard() }
    }

    fun detail(movieId: Long): MovieDetail {
        val movie = movieRepository.findAllById(listOf(movieId)).firstOrNull()
            ?: throw MovieException(ErrorCodeEnum.MOVIE_NOT_FOUND)
        val directors = movieDirectorRepository.findAllByMovieId(movieId)
            .map { PersonItem(it.personId, it.name, emptyList()) }
        val cast = movieCastRepository.findAllByMovieId(movieId)
            .sortedBy { it.castOrder }
            .take(CAST_DETAIL_LIMIT)
            .map { CastMember(it.personId, it.name, it.character) }
        return MovieDetail(movie.toCard(), movie.runtime, movie.overview, directors, cast)
    }

    /**
     * 탐색 목록 — offset은 limit의 배수(page * limit)로 들어온다.
     *
     * 평점순 정렬은 투표 몇 표짜리 작품이 상위에 오지 않도록 [MIN_VOTES_FOR_RATING] 암시 하한을 건다.
     * 호출자가 [voteCountMin]을 명시하면 명시값이 우선하고 암시 하한은 적용하지 않는다 —
     * 명시값과 암시 하한을 둘 다 걸면 "투표 50~500" 숨은 명작 조건이 `1000 이상 AND 500 이하`로
     * 충돌해 항상 빈 결과가 되기 때문이다. [voteCountMin]이 null이면 기존대로 암시 하한을 적용한다
     */
    fun explore(
        genreId: Long?,
        sort: ExploreSortEnum,
        offset: Int,
        limit: Int,
        directorId: Long? = null,
        castId: Long? = null,
        ratingMin: Double? = null,
        voteCountMin: Int? = null,
        voteCountMax: Int? = null,
    ): MoviePage {
        val minVoteCount = voteCountMin ?: if (sort == ExploreSortEnum.RATING) MIN_VOTES_FOR_RATING else null
        val page = movieRepository.findAll(
            exploreSpec(genreId, minVoteCount, voteCountMax, directorId, castId, ratingMin),
            PageRequest.of(offset / limit, limit, sortOrder(sort)),
        )
        val ids = page.content.map { requireNotNull(it.movieId) }
        val cards = cards(ids)
        return MoviePage(
            items = ids.mapNotNull { cards[it] },
            limit = limit,
            offset = offset,
            hasNext = page.hasNext(),
        )
    }

    fun genres(): List<GenreItem> = genreRepository.findAll(Sort.by("genreId")).map { it.toItem() }

    /** 사람 자동완성 (접두어 일치, 상위 10) */
    fun people(prefix: String, role: String): List<PersonItem> {
        val q = prefix.trim()
        if (q.isEmpty()) {
            return emptyList()
        }
        val page = PageRequest.of(0, PEOPLE_LIMIT)
        val names = if (role == ROLE_DIRECTOR) {
            movieDirectorRepository.searchByPrefix(q, page)
        } else {
            movieCastRepository.searchByPrefix(q, page)
        }
        return names.map { PersonItem(it.personId, it.name, knownFor(it.personId, role)) }
    }

    /** 새로고침 후 칩 라벨 복원용 */
    fun person(personId: Long, role: String): PersonItem {
        val name = if (role == ROLE_DIRECTOR) {
            movieDirectorRepository.findFirstByPersonId(personId)?.name
        } else {
            movieCastRepository.findFirstByPersonId(personId)?.name
        } ?: throw MovieException(ErrorCodeEnum.PERSON_NOT_FOUND)
        return PersonItem(personId, name, knownFor(personId, role))
    }

    private fun knownFor(personId: Long, role: String): List<String> {
        val page = PageRequest.of(0, KNOWN_FOR_LIMIT)
        return if (role == ROLE_DIRECTOR) {
            movieDirectorRepository.findTopMovieTitles(personId, page)
        } else {
            movieCastRepository.findTopMovieTitles(personId, page)
        }
    }

    private fun sortOrder(sort: ExploreSortEnum): Sort = when (sort) {
        ExploreSortEnum.RATING -> Sort.by(
            Sort.Order.desc("voteAverage").nullsLast(),
            Sort.Order.desc("voteCount").nullsLast(),
        )
        ExploreSortEnum.RELEASE -> Sort.by(Sort.Order.desc("releaseDate").nullsLast())
        ExploreSortEnum.VOTE_COUNT -> Sort.by(Sort.Order.desc("voteCount").nullsLast())
    }

    /** 인물 필터는 부모 참조 없이 movieId 서브쿼리로 비교한다 (컨벤션: 자식은 movieId로 명시적으로) */
    private fun exploreSpec(
        genreId: Long?,
        minVoteCount: Int?,
        maxVoteCount: Int?,
        directorId: Long?,
        castId: Long?,
        ratingMin: Double?,
    ) = Specification<MovieEntity> { root, query, cb ->
        val predicates = buildList {
            genreId?.let { add(cb.equal(root.join<MovieEntity, GenreEntity>("genres").get<Long>("genreId"), it)) }
            minVoteCount?.let { add(cb.greaterThanOrEqualTo(root.get("voteCount"), it)) }
            maxVoteCount?.let { add(cb.lessThanOrEqualTo(root.get("voteCount"), it)) }
            ratingMin?.let { add(cb.greaterThanOrEqualTo(root.get("voteAverage"), BigDecimal.valueOf(it))) }
            directorId?.let { pid ->
                val sub = query.subquery(Long::class.java)
                val d = sub.from(MovieDirectorEntity::class.java)
                sub.select(d.get("movieId")).where(cb.equal(d.get<Long>("personId"), pid))
                add(root.get<Long>("movieId").`in`(sub))
            }
            castId?.let { pid ->
                val sub = query.subquery(Long::class.java)
                val c = sub.from(MovieCastEntity::class.java)
                sub.select(c.get("movieId")).where(cb.equal(c.get<Long>("personId"), pid))
                add(root.get<Long>("movieId").`in`(sub))
            }
        }
        cb.and(*predicates.toTypedArray())
    }

    private fun MovieEntity.toCard() = MovieCard(
        movieId = requireNotNull(movieId),
        title = title,
        originalTitle = originalTitle,
        releaseYear = releaseYear,
        rating = voteAverage?.toDouble(),
        voteCount = voteCount,
        posterPath = posterPath,
        genres = genres.sortedBy { it.genreId }.map { it.toItem() },
    )

    private fun GenreEntity.toItem() = GenreItem(genreId, name) // ponytail-audit #1

    companion object {
        private const val ROLE_DIRECTOR = "director"
        private const val CAST_DETAIL_LIMIT = 5
        private const val PEOPLE_LIMIT = 10
        private const val KNOWN_FOR_LIMIT = 2
        private const val MIN_VOTES_FOR_RATING = 1000 // 평점순에서 투표 몇 표짜리 영화가 상위에 오지 않게
    }
}
