package com.whsanha55.cineseek.movie.repository

import com.whsanha55.cineseek.movie.entity.MovieEntity
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository

interface MovieRepository : JpaRepository<MovieEntity, Long> {

    fun findByTmdbId(tmdbId: Long): MovieEntity?

    /** 색인 시 genres 즉시 로딩 (트랜잭션 밖 payload 조립용) */
    @EntityGraph(attributePaths = ["genres"])
    override fun findAll(): List<MovieEntity>
}
