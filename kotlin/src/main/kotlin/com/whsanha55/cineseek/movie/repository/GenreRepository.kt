package com.whsanha55.cineseek.movie.repository

import com.whsanha55.cineseek.movie.entity.GenreEntity
import org.springframework.data.jpa.repository.JpaRepository

interface GenreRepository : JpaRepository<GenreEntity, Long>
