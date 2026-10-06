package com.whsanha55.cineseek.collect.repository

import com.whsanha55.cineseek.collect.entity.CollectTaskEntity
import com.whsanha55.cineseek.collect.enums.CollectTaskStatusEnum
import com.whsanha55.cineseek.collect.enums.CollectTaskTypeEnum
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant

interface CollectTaskRepository : JpaRepository<CollectTaskEntity, Long> {

    fun findByTaskTypeAndPayloadKey(taskType: CollectTaskTypeEnum, payloadKey: String): CollectTaskEntity?

    /** 잔여 작업 존재 여부 — PENDING이나 RUNNING이 남았으면 true */
    fun existsByStatusIn(statuses: Collection<CollectTaskStatusEnum>): Boolean

    fun countByTaskTypeAndStatus(taskType: CollectTaskTypeEnum, status: CollectTaskStatusEnum): Long

    fun countByStatus(status: CollectTaskStatusEnum): Long

    /** 일일 증분용 — 재시도를 소진한 FAILED(attempts가 상한 이상)를 초기화와 함께 PENDING으로 되돌린다. 영구 확정(attempts 미달)은 그대로 */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        "UPDATE CollectTaskEntity t SET t.status = :pending, t.attempts = 0, " +
            "t.nextRetryAt = NULL, t.checkpoint = NULL WHERE t.status = :failed AND t.attempts >= :maxAttempts",
    )
    fun resetRetryExhausted(pending: CollectTaskStatusEnum, failed: CollectTaskStatusEnum, maxAttempts: Int): Int

    fun findAllByTaskIdInAndStatus(taskIds: Collection<Long>, status: CollectTaskStatusEnum): List<CollectTaskEntity>

    /** 클레임 1단계 — 예약 시각이 도래한 PENDING 후보 id를 priority DESC, taskId ASC로 조회 */
    @Query(
        "SELECT t.taskId FROM CollectTaskEntity t " +
            "WHERE t.status = :status AND (t.nextRetryAt IS NULL OR t.nextRetryAt <= :now) " +
            "ORDER BY t.priority DESC, t.taskId ASC",
    )
    fun findClaimableIds(status: CollectTaskStatusEnum, now: Instant, pageable: Pageable): List<Long>

    /** 클레임 2단계 — 후보 중 아직 PENDING인 것만 RUNNING+lease로 바꾼다 (동시 클레임에 안전) */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        "UPDATE CollectTaskEntity t SET t.status = :running, t.lockedAt = :now, " +
            "t.lockedBy = :worker WHERE t.taskId IN :taskIds AND t.status = :pending",
    )
    fun claimTasks(
        taskIds: Collection<Long>,
        pending: CollectTaskStatusEnum,
        running: CollectTaskStatusEnum,
        now: Instant,
        worker: String,
    ): Int

    /** 만료 lease 회수 — threshold 이전에 잡은 RUNNING을 PENDING으로 되돌린다 */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        "UPDATE CollectTaskEntity t SET t.status = :pending, t.lockedAt = NULL " +
            "WHERE t.status = :running AND t.lockedAt < :threshold",
    )
    fun releaseExpiredLeases(pending: CollectTaskStatusEnum, running: CollectTaskStatusEnum, threshold: Instant): Int

    /** 일일 증분용 requeue — DONE 작업을 attempts/checkpoint 초기화와 함께 PENDING으로 되돌린다 */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        "UPDATE CollectTaskEntity t SET t.status = :pending, t.attempts = 0, t.checkpoint = NULL " +
            "WHERE t.taskType = :taskType AND t.payloadKey = :payloadKey AND t.status = :done",
    )
    fun requeueDone(
        taskType: CollectTaskTypeEnum,
        payloadKey: String,
        pending: CollectTaskStatusEnum,
        done: CollectTaskStatusEnum,
    ): Int
}
