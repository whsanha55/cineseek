package com.whsanha55.cineseek.repository

import com.whsanha55.cineseek.domain.MovieCast
import com.whsanha55.cineseek.domain.MovieCastId
import org.springframework.data.jpa.repository.JpaRepository

interface MovieCastRepository : JpaRepository<MovieCast, MovieCastId> {

	/** 색인 시 자식 조회 — 부모 참조·컬렉션 없이 movieId로 명시적으로 */
	fun findAllByMovieId(movieId: Long): List<MovieCast>

	/** upsert 시 기존 자식을 지운다 — cascade 없음, 삭제는 항상 이렇게 명시적으로 */
	fun deleteAllByMovieId(movieId: Long)
}
