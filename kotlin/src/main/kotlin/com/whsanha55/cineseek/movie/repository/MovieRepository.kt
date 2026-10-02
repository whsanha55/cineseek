package com.whsanha55.cineseek.movie.repository

import com.whsanha55.cineseek.movie.entity.MovieEntity
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor

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
}
