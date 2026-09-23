package com.whsanha55.cineseek.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Table
import java.io.Serializable

/**
 * 영화-출연진 조인 — Qdrant payload 필터 대상. cast_order는 주연 우선 정렬.
 * character는 PG 예약어라 자동 변환이 아닌 특이사항 케이스로 @Column(name)을 명시한다.
 * 부모 참조·cascade 없음 — 조회·삭제는 Repository에서 movieId로 명시적으로
 */
@Entity
@IdClass(MovieCastId::class)
@Table(name = "movie_cast")
class MovieCast(
	@Id
	val movieId: Long,

	@Id
	val personId: Long,

	@Column(nullable = false)
	var name: String,

	@Column(name = "character")
	var character: String? = null,

	var castOrder: Int? = null,
)

/**
 * movie_cast 복합키 — 엔티티와 달리 data class + Serializable (JPA 복합키 요구)
 */
data class MovieCastId(
	val movieId: Long = 0,
	val personId: Long = 0,
) : Serializable
