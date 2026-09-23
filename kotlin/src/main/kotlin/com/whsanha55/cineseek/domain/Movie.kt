package com.whsanha55.cineseek.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.JoinTable
import jakarta.persistence.ManyToMany
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * 영화 메타 + 줄거리 — 진실의 원천(SoT). overview가 임베딩 입력이다 (NULL이면 색인 스킵)
 */
@Entity
@Table(name = "movie")
class Movie(
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	val movieId: Long = 0,

	@Column(nullable = false, unique = true)
	val tmdbId: Long,

	@Column(nullable = false)
	var title: String,

	var originalTitle: String? = null,
	var overview: String? = null,
	var releaseDate: LocalDate? = null,
	var releaseYear: Int? = null,
	var runtime: Int? = null,
	var voteAverage: BigDecimal? = null,
	var voteCount: Int? = null,
	var posterPath: String? = null,
	var backdropPath: String? = null,
	var originalLanguage: String? = null,
	var overviewUpdatedAt: Instant? = null,

	@ManyToMany
	@JoinTable(
		name = "movie_genre",
		joinColumns = [JoinColumn(name = "movie_id")],
		inverseJoinColumns = [JoinColumn(name = "genre_id")],
	)
	val genres: MutableSet<Genre> = mutableSetOf(),

	@CreationTimestamp
	@Column(nullable = false)
	var createdAt: Instant? = null,

	@UpdateTimestamp
	@Column(nullable = false)
	var updatedAt: Instant? = null,
)
