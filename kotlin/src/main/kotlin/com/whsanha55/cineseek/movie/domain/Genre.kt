package com.whsanha55.cineseek.movie.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

/** TMDB 장르 마스터 — genre_id는 TMDB id를 그대로 쓴다 (자동 생성 아님) */
@Entity
@Table(name = "genre")
class Genre(
	@Id
	val genreId: Long,

	@Column(nullable = false)
	var name: String,

	var nameKo: String? = null,
)
