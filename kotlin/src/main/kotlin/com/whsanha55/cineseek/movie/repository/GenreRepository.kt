package com.whsanha55.cineseek.movie.repository

import com.whsanha55.cineseek.movie.domain.Genre
import org.springframework.data.jpa.repository.JpaRepository

interface GenreRepository : JpaRepository<Genre, Long>
