package com.whsanha55.cineseek.movie.repository

import com.whsanha55.cineseek.movie.entity.MovieEntity
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query

interface MovieRepository :
    JpaRepository<MovieEntity, Long>,
    JpaSpecificationExecutor<MovieEntity> {

    fun findByTmdbId(tmdbId: Long): MovieEntity?

    fun findAllByTmdbIdIn(tmdbIds: Collection<Long>): List<MovieEntity>

    /** 색인 시 genres 즉시 로딩 (트랜잭션 밖 payload 조립용) */
    @EntityGraph(attributePaths = ["genres"])
    override fun findAll(): List<MovieEntity>

    /** 카드 조립용 — id 목록으로 genres까지 한 번에 */
    @EntityGraph(attributePaths = ["genres"])
    override fun findAllById(ids: Iterable<Long>): List<MovieEntity>

    /** 분위기 태그가 필요한 영화 — overview 있고 현재 태그 버전과 다른 것 (IS NULL과 비교 혼합이라 파생 쿼리 불가) */
    @Query(
        "SELECT m FROM MovieEntity m WHERE m.overview IS NOT NULL " +
            "AND (m.moodModel IS NULL OR m.moodModel <> :version)",
    )
    fun findRequiringMoodTag(version: String): List<MovieEntity>
}
