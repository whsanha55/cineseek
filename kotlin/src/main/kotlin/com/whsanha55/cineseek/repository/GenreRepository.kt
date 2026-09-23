package com.whsanha55.cineseek.repository

import com.whsanha55.cineseek.domain.Genre
import org.springframework.data.jpa.repository.JpaRepository

interface GenreRepository : JpaRepository<Genre, Long>
