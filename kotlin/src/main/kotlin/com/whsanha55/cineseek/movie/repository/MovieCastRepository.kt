package com.whsanha55.cineseek.movie.repository

import com.whsanha55.cineseek.movie.entity.MovieCastEntity
import com.whsanha55.cineseek.movie.entity.MovieCastId
import com.whsanha55.cineseek.movie.entity.MovieEntity
import com.whsanha55.cineseek.movie.vo.PersonNameView
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface MovieCastRepository : JpaRepository<MovieCastEntity, MovieCastId> {

    /** 색인 시 자식 조회 — 부모 참조·컬렉션 없이 movieId로 명시적으로 */
    fun findAllByMovieId(movieId: Long): List<MovieCastEntity>

    /** upsert 시 기존 자식을 지운다 — cascade 없음, 삭제는 항상 이렇게 명시적으로 */
    fun deleteAllByMovieId(movieId: Long)

    /**
     * 배우 필모그래피 페이지 — 최신 개봉순(기본). movie_cast PK(movieId, personId)가
     * 동일 영화 중복을 방해한다. 정렬을 쿼리에 두는 이유는 nulls last 때문이다 (Pageable 정렬로는 못 쓴다).
     * 투표 수 하한 없음 — 출연작 전체를 최신순으로 보여준다
     */
    @Query(
        value = (
            "select m from MovieEntity m join MovieCastEntity c on c.movieId = m.movieId " +
                "where c.personId = :personId and (:excludeMovieId is null or m.movieId <> :excludeMovieId) " +
                "order by m.releaseDate desc nulls last"
            ),
        countQuery = (
            "select count(m) from MovieEntity m join MovieCastEntity c on c.movieId = m.movieId " +
                "where c.personId = :personId and (:excludeMovieId is null or m.movieId <> :excludeMovieId)"
            ),
    )
    fun findFilmographyOrderByReleaseDate(
        @Param("personId") personId: Long,
        @Param("excludeMovieId") excludeMovieId: Long?,
        pageable: Pageable,
    ): Page<MovieEntity>

    /** 배우 필모그래피 페이지 — 평점순, 동점은 투표 수. 투표 수 하한 없음 (탐색과 다른 점) */
    @Query(
        value = (
            "select m from MovieEntity m join MovieCastEntity c on c.movieId = m.movieId " +
                "where c.personId = :personId and (:excludeMovieId is null or m.movieId <> :excludeMovieId) " +
                "order by m.voteAverage desc nulls last, m.voteCount desc nulls last"
            ),
        countQuery = (
            "select count(m) from MovieEntity m join MovieCastEntity c on c.movieId = m.movieId " +
                "where c.personId = :personId and (:excludeMovieId is null or m.movieId <> :excludeMovieId)"
            ),
    )
    fun findFilmographyOrderByRating(
        @Param("personId") personId: Long,
        @Param("excludeMovieId") excludeMovieId: Long?,
        pageable: Pageable,
    ): Page<MovieEntity>

    /** 필모그래피 아이템의 배역 — 페이지에 나온 영화만 */
    fun findAllByPersonIdAndMovieIdIn(personId: Long, movieIds: Collection<Long>): List<MovieCastEntity>

    /** 총 작품 수 — personId별 movie_cast 행 수 */
    fun countByPersonId(personId: Long): Long

    /** 배우 자동완성 — 접두어 일치, 이름 정렬. 작품 수만큼 행이 있어 distinct */
    @Query(
        "select distinct c.personId as personId, c.name as name from MovieCastEntity c " +
            "where lower(c.name) like lower(concat(:prefix, '%')) order by c.name",
    )
    fun searchByPrefix(@Param("prefix") prefix: String, pageable: Pageable): List<PersonNameView>

    /** knownFor — 해당 배우의 최근 작품 제목 (부모 참조 없이 movieId로 entity join) */
    @Query(
        "select m.title from MovieEntity m join MovieCastEntity c on c.movieId = m.movieId " +
            "where c.personId = :personId order by m.releaseYear desc nulls last",
    )
    fun findTopMovieTitles(@Param("personId") personId: Long, pageable: Pageable): List<String>

    /** 칩 라벨 복원 — 같은 사람의 아무 대표 행 하나 */
    fun findFirstByPersonId(personId: Long): MovieCastEntity?
}
