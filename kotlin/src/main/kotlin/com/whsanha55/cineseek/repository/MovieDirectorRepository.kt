package com.whsanha55.cineseek.repository

import com.whsanha55.cineseek.domain.MovieDirector
import com.whsanha55.cineseek.domain.MovieDirectorId
import org.springframework.data.jpa.repository.JpaRepository

interface MovieDirectorRepository : JpaRepository<MovieDirector, MovieDirectorId> {

	/** 색인 시 자식 조회 — 부모 참조·컬렉션 없이 movieId로 명시적으로 */
	fun findAllByMovieId(movieId: Long): List<MovieDirector>

	/** upsert 시 기존 자식을 지운다 — cascade 없음, 삭제는 항상 이렇게 명시적으로 */
	fun deleteAllByMovieId(movieId: Long)
}
