package com.whsanha55.cineseek.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Table
import java.io.Serializable

/**
 * 영화-감독 조인 — Qdrant payload 필터 대상. person_id는 TMDB person id.
 * 부모 참조·cascade 없음 — 조회·삭제는 Repository에서 movieId로 명시적으로
 */
@Entity
@IdClass(MovieDirectorId::class)
@Table(name = "movie_director")
class MovieDirector(
	@Id
	val movieId: Long,

	@Id
	val personId: Long,

	@Column(nullable = false)
	var name: String,
)

/**
 * movie_director 복합키 — 엔티티와 달리 data class + Serializable (JPA 복합키 요구)
 */
data class MovieDirectorId(
	val movieId: Long = 0,
	val personId: Long = 0,
) : Serializable
