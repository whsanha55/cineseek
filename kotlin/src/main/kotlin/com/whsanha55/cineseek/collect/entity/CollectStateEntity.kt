package com.whsanha55.cineseek.collect.entity

import com.whsanha55.cineseek.collect.enums.CollectModeEnum
import com.whsanha55.cineseek.global.base.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * 수집 모드·운영 상태 — id=1 단일 행만 존재한다 (V3 마이그레이션이 미리 넣어 둔다).
 * bootstrapped_at은 초기 enqueue 완료 표시(1회 가드), last_run_at/last_summary는 마지막 틱 기록
 */
@Entity
@Table(name = "collect_state")
class CollectStateEntity(
    @Id
    @Column(nullable = false)
    val id: Int = 1,
) : BaseEntity() {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var mode: CollectModeEnum = CollectModeEnum.BOOTSTRAP
        protected set

    var modeChangedAt: Instant? = null
        protected set

    var bootstrappedAt: Instant? = null
        protected set

    var lastRunAt: Instant? = null
        protected set

    var lastSummary: String? = null
        protected set

    /** 모드 전환 — 전환 시각을 함께 남긴다 */
    fun transitionTo(mode: CollectModeEnum, now: Instant) {
        this.mode = mode
        modeChangedAt = now
    }

    /** bootstrap 초기 enqueue 완료 표시 — 1회 가드 */
    fun markBootstrapped(now: Instant) {
        bootstrappedAt = now
    }

    /** 마지막 틱 기록 — 실행 시각과 결과 요약(키=값) */
    fun recordTick(at: Instant, summary: String) {
        lastRunAt = at
        lastSummary = summary
    }

    companion object {
        /** 단일 행 PK — V3 마이그레이션이 미리 넣어 둔다 */
        const val SINGLETON_ID = 1
    }
}
