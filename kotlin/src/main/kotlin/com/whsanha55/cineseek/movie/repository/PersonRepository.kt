package com.whsanha55.cineseek.movie.repository

import com.whsanha55.cineseek.movie.entity.PersonEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant

interface PersonRepository : JpaRepository<PersonEntity, Long> {

    /** 필모그래피 만료 조회 — [threshold] 이전에 마지막으로 확인한 인물 (한 번도 확인하지 않은 NULL은 제외) */
    fun findByFilmoCheckedAtBefore(threshold: Instant): List<PersonEntity>
}
