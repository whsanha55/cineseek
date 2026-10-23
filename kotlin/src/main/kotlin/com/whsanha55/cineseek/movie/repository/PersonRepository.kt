package com.whsanha55.cineseek.movie.repository

import com.whsanha55.cineseek.movie.entity.PersonEntity
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.Instant

interface PersonRepository : JpaRepository<PersonEntity, Long> {

    /** 필모그래피 만료 조회 — [threshold] 이전에 마지막으로 확인한 인물 (한 번도 확인하지 않은 NULL은 제외) */
    fun findByFilmoCheckedAtBefore(threshold: Instant): List<PersonEntity>

    /**
     * 번역 대상 — 출생지 번역이 비었거나, 한국어 소개 없이 영어 소개만 있는 인물을 최근 갱신순으로.
     * 이 번역 [version]에서 영구 실패한 인물은 뺀다
     */
    @Query(
        """
        SELECT p.personId
        FROM PersonEntity p
        WHERE (
                (
                    p.placeOfBirth IS NOT NULL
                    AND p.placeOfBirthKo IS NULL
                )
                OR (
                    p.biographyKo IS NULL
                    AND p.biographyEn IS NOT NULL
                )
            )
            AND (
                p.translationFailedModel IS NULL
                OR p.translationFailedModel <> :version
            )
        ORDER BY p.updatedAt DESC
        """,
    )
    fun findIdsRequiringTranslation(version: String, pageable: Pageable): List<Long>
}
