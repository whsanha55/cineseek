package com.whsanha55.cineseek.movie.repository

import com.whsanha55.cineseek.movie.entity.MovieDirectorEntity
import com.whsanha55.cineseek.movie.entity.MovieDirectorId
import com.whsanha55.cineseek.movie.vo.PersonNameView
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface MovieDirectorRepository : JpaRepository<MovieDirectorEntity, MovieDirectorId> {

    /** 색인 시 자식 조회 — 부모 참조·컬렉션 없이 movieId로 명시적으로 */
    fun findAllByMovieId(movieId: Long): List<MovieDirectorEntity>

    /** upsert 시 기존 자식을 지운다 — cascade 없음, 삭제는 항상 이렇게 명시적으로 */
    fun deleteAllByMovieId(movieId: Long)

    /** 감독 자동완성 — 접두어 일치, 이름 정렬. 작품 수만큼 행이 있어 distinct */
    @Query(
        """
        SELECT DISTINCT d.personId AS personId, d.name AS name
        FROM MovieDirectorEntity d
        WHERE LOWER(d.name) LIKE LOWER(CONCAT(:prefix, '%'))
        ORDER BY d.name
        """,
    )
    fun searchByPrefix(prefix: String, pageable: Pageable): List<PersonNameView>

    /** knownFor — 해당 감독의 최근 작품 제목 (부모 참조 없이 movieId로 entity join) */
    @Query(
        """
        SELECT m.title
        FROM MovieEntity m
        JOIN MovieDirectorEntity d ON d.movieId = m.movieId
        WHERE d.personId = :personId
        ORDER BY m.releaseYear DESC NULLS LAST
        """,
    )
    fun findTopMovieTitles(personId: Long, pageable: Pageable): List<String>

    /** 칩 라벨 복원 — 같은 사람의 아무 대표 행 하나 */
    fun findFirstByPersonId(personId: Long): MovieDirectorEntity?
}
