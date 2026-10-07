package com.whsanha55.cineseek.movie.repository

import com.whsanha55.cineseek.movie.entity.MovieCastEntity
import com.whsanha55.cineseek.movie.entity.MovieCastId
import com.whsanha55.cineseek.movie.vo.PersonNameView
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface MovieCastRepository : JpaRepository<MovieCastEntity, MovieCastId> {

    /** 색인 시 자식 조회 — 부모 참조·컬렉션 없이 movieId로 명시적으로 */
    fun findAllByMovieId(movieId: Long): List<MovieCastEntity>

    /** upsert 시 기존 자식을 지운다 — cascade 없음, 삭제는 항상 이렇게 명시적으로 */
    fun deleteAllByMovieId(movieId: Long)

    /** 배우 자동완성 — 접두어 일치, 이름 정렬. 작품 수만큼 행이 있어 distinct */
    @Query(
        """
        SELECT DISTINCT c.personId AS personId, c.name AS name
        FROM MovieCastEntity c
        WHERE LOWER(c.name) LIKE LOWER(CONCAT(:prefix, '%'))
        ORDER BY c.name
        """,
    )
    fun searchByPrefix(prefix: String, pageable: Pageable): List<PersonNameView>

    /** knownFor — 해당 배우의 최근 작품 제목 (부모 참조 없이 movieId로 entity join) */
    @Query(
        """
        SELECT m.title
        FROM MovieEntity m
        JOIN MovieCastEntity c ON c.movieId = m.movieId
        WHERE c.personId = :personId
        ORDER BY m.releaseYear DESC NULLS LAST
        """,
    )
    fun findTopMovieTitles(personId: Long, pageable: Pageable): List<String>

    /** 칩 라벨 복원 — 같은 사람의 아무 대표 행 하나 */
    fun findFirstByPersonId(personId: Long): MovieCastEntity?
}
