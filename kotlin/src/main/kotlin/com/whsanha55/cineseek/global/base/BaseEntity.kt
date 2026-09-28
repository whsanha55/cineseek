package com.whsanha55.cineseek.global.base

import jakarta.persistence.Column
import jakarta.persistence.EntityListeners
import jakarta.persistence.MappedSuperclass
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant

/** 생성·수정 시각 — JpaConfig의 Clock 기반 DateTimeProvider가 채운다 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener::class)
open class BaseEntity {

    @CreatedDate
    @Column(nullable = false, updatable = false)
    var createdAt: Instant? = null
        protected set

    @LastModifiedDate
    @Column(nullable = false)
    var updatedAt: Instant? = null
        protected set
}
