package com.whsanha55.cineseek.collect.entity

import com.whsanha55.cineseek.collect.enums.CollectTaskStatusEnum
import com.whsanha55.cineseek.collect.enums.CollectTaskTypeEnum
import com.whsanha55.cineseek.global.base.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant

/**
 * 수집 작업 큐 행 — (task_type, payload_key)로 멱등. 워커가 동시에 못 잡게
 * lease(locked_at/locked_by)로 점유하고, checkpoint(JSON)에 마지막 완료 지점을 남겨
 * 재시작 시 이어서 한다
 */
@Entity
@Table(
    name = "collect_task",
    uniqueConstraints = [UniqueConstraint(columnNames = ["task_type", "payload_key"])],
)
class CollectTaskEntity(
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    val taskType: CollectTaskTypeEnum,

    @Column(nullable = false)
    val payloadKey: String,

    @Column(nullable = false)
    val priority: Int = 0,
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var taskId: Long? = null
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: CollectTaskStatusEnum = CollectTaskStatusEnum.PENDING
        protected set

    @Column(nullable = false)
    var attempts: Int = 0
        protected set

    var nextRetryAt: Instant? = null
        protected set

    var lastError: String? = null
        protected set

    var checkpoint: String? = null
        protected set

    var lockedAt: Instant? = null
        protected set

    var lockedBy: String? = null
        protected set

    /** 클레임 — lease를 잡고 RUNNING으로 전환한다 */
    fun lease(now: Instant, by: String) {
        status = CollectTaskStatusEnum.RUNNING
        lockedAt = now
        lockedBy = by
    }

    /** 작업 완료 — lease와 재시도 예약, 마지막 오류를 지운다 */
    fun markDone() {
        status = CollectTaskStatusEnum.DONE
        nextRetryAt = null
        lastError = null
        lockedAt = null
        lockedBy = null
    }

    /** 작업 실패 — 오류를 기록하고 lease를 해제한다 (재시도 예약은 scheduleRetry가 따로) */
    fun markFailed(error: String) {
        status = CollectTaskStatusEnum.FAILED
        lastError = error
        lockedAt = null
        lockedBy = null
    }

    /** 재시도 예약 — PENDING으로 돌려 보내고 시도 수를 올린다 */
    fun scheduleRetry(nextRetryAt: Instant) {
        status = CollectTaskStatusEnum.PENDING
        attempts += 1
        this.nextRetryAt = nextRetryAt
        lockedAt = null
        lockedBy = null
    }

    /** 중간 저장 후 복귀 — lease만 풀고 즉시 재클레임 가능한 PENDING으로 돌린다. 재시도가 아니므로 시도 수는 그대로 */
    fun release() {
        status = CollectTaskStatusEnum.PENDING
        nextRetryAt = null
        lockedAt = null
        lockedBy = null
    }

    /** 진행 체크포인트 저장 — JSON 문자열 그대로 (예: {"page":12}) */
    fun saveCheckpoint(checkpoint: String) {
        this.checkpoint = checkpoint
    }
}
