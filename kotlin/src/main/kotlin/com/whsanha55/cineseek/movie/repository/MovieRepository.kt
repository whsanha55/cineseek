package com.whsanha55.cineseek.movie.repository

import com.whsanha55.cineseek.movie.domain.Movie
import org.springframework.data.jpa.repository.JpaRepository

interface MovieRepository : JpaRepository<Movie, Long> {

	fun findByTmdbId(tmdbId: Long): Movie?
}
