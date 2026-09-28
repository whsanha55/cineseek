package com.whsanha55.cineseek.movie.repository

import com.whsanha55.cineseek.movie.entity.Genre
import org.springframework.data.jpa.repository.JpaRepository

interface GenreRepository : JpaRepository<Genre, Long>
