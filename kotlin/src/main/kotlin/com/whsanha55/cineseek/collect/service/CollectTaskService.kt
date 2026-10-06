package com.whsanha55.cineseek.collect.service

import com.whsanha55.cineseek.collect.config.CollectProperties
import com.whsanha55.cineseek.collect.entity.CollectTaskEntity
import com.whsanha55.cineseek.collect.enums.CollectTaskStatusEnum.DONE
import com.whsanha55.cineseek.collect.enums.CollectTaskStatusEnum.FAILED
import com.whsanha55.cineseek.collect.enums.CollectTaskStatusEnum.PENDING
import com.whsanha55.cineseek.collect.enums.CollectTaskStatusEnum.RUNNING
import com.whsanha55.cineseek.collect.enums.CollectTaskTypeEnum
import com.whsanha55.cineseek.collect.repository.CollectTaskRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.random.Random

private val log = KotlinLogging.logger {}

/**
 * 수집 작업 큐 서비스 — enqueue/claim/complete/fail로 큐를 운영한다.
 * 클레임 3단계(후보 조회 → 조건부 UPDATE → 재조회), requeue, 만료 lease 회수는
 * CollectTaskRepository의 쿼리를 그대로 재사용한다
 */
@Service
class CollectTaskService(
    private val collectTaskRepository: CollectTaskRepository,
    private val properties: CollectProperties,
    private val clock: Clock,
) {

    /** 이 인스턴스의 클레임 주체 — lease 진단용(locked_by, 스키마 상한 64자) */
    private val workerId =
        "${(System.getenv("HOSTNAME") ?: "local").take(HOST_LIMIT)}-${UUID.randomUUID().toString().take(8)}"

    /**
     * 작업 등록 — (task_type, payload_key) 멱등. 이미 있으면 false.
     * @Transactional을 의도적으로 붙이지 않는다 — INSERT를 리포지토리 자체 트랜잭션에 맡겨
     * UNIQUE 충돌 시 그 트랜잭션만 롤백되고, 충돌 예외를 잡아 등록을 무시할 수 있게 한다
     */
    fun enqueue(type: CollectTaskTypeEnum, payloadKey: String, priority: Int = 0): Boolean {
        if (collectTaskRepository.findByTaskTypeAndPayloadKey(type, payloadKey) != null) {
            return false
        }
        return try {
            collectTaskRepository.saveAndFlush(
                CollectTaskEntity(taskType = type, payloadKey = payloadKey, priority = priority),
            )
            true
        } catch (e: DataIntegrityViolationException) {
            log.warn(e) { "enqueue 충돌 — 이미 등록된 작업이다. type=$type, payloadKey=$payloadKey" }
            false
        }
    }

    /** 일일 증분용 — DONE 작업을 attempts/checkpoint 초기화와 함께 PENDING으로 되돌린다. 해당 작업이 없으면 false */
    @Transactional
    fun requeue(type: CollectTaskTypeEnum, payloadKey: String): Boolean =
        collectTaskRepository.requeueDone(type, payloadKey, PENDING, DONE) > 0

    /** 클레임 — 예약 시각이 도래한 PENDING을 priority 순으로 최대 [limit]개 잡아 RUNNING(lease)으로 만든다 */
    @Transactional
    fun claim(limit: Int): List<CollectTaskEntity> {
        val now = Instant.now(clock)
        val ids = collectTaskRepository.findClaimableIds(PENDING, now, PageRequest.ofSize(limit))
        if (ids.isEmpty()) {
            return emptyList()
        }
        collectTaskRepository.claimTasks(ids, PENDING, RUNNING, now, workerId)
        return collectTaskRepository.findAllByTaskIdInAndStatus(ids, RUNNING)
            .filter { it.lockedBy == workerId } // 다른 워커가 먼저 잡은 것은 제외
    }

    /** 작업 완료 — [checkpointJson]이 있으면 마지막 지점을 남기고, lease와 재시도 예약·오류를 지운다 */
    @Transactional
    fun complete(task: CollectTaskEntity, checkpointJson: String? = null) {
        checkpointJson?.let(task::saveCheckpoint)
        task.markDone()
        collectTaskRepository.save(task)
    }

    /**
     * 작업 실패 — [retryable]이면 지수 백오프로 재시도를 예약한다. 재시도 횟수(attempts)가
     * 상한에 도달했거나 retryable이 아니면 FAILED(영구)로 확정한다. [checkpointJson]이 있으면 재시도가 이어서 하게 남긴다
     */
    @Transactional
    fun fail(task: CollectTaskEntity, error: String, retryable: Boolean, checkpointJson: String? = null) {
        checkpointJson?.let(task::saveCheckpoint)
        task.markFailed(error) // 오류 기록과 lease 해제
        if (retryable && task.attempts < properties.retryMaxAttempts) {
            // 상태만 PENDING으로 되돌려 예약 — 오류 기록은 남는다
            task.scheduleRetry(
                Instant.now(clock).plus(backoffDelay(properties.retryBase, task.attempts, Random.Default)),
            )
        }
        collectTaskRepository.save(task)
    }

    /**
     * 중간 저장 — maxRuntime 도달 등으로 작업을 놓을 때. 체크포인트만 남기고 즉시 재클레임 가능한
     * PENDING으로 돌린다 (재시도가 아니므로 시도 수를 올리지 않는다)
     */
    @Transactional
    fun release(task: CollectTaskEntity, checkpointJson: String? = null) {
        checkpointJson?.let(task::saveCheckpoint)
        task.release()
        collectTaskRepository.save(task)
    }

    /** 만료 lease 회수 — leaseTimeout 넘게 RUNNING인 작업을 죽은 워커로 보고 PENDING으로 되돌린다 */
    @Transactional
    fun recoverExpiredLeases(): Int {
        val threshold = Instant.now(clock).minus(properties.leaseTimeout)
        val released = collectTaskRepository.releaseExpiredLeases(PENDING, RUNNING, threshold)
        if (released > 0) {
            log.warn { "만료 lease 회수. count=$released, threshold=$threshold" }
        }
        return released
    }

    /**
     * 일일 증분용 — 재시도를 소진해 FAILED가 된 작업(attempts가 상한 이상)을 초기화해 PENDING으로 되돌린다.
     * 404처럼 즉시 영구 확정된 실패(attempts 미달)는 되살리지 않는다
     */
    @Transactional
    fun resetRetryExhausted(): Int = collectTaskRepository.resetRetryExhausted(
        pending = PENDING,
        failed = FAILED,
        maxAttempts = properties.retryMaxAttempts,
    )

    /** 잔여 작업 존재 여부 — PENDING이나 RUNNING이 하나라도 남았으면 true (BOOTSTRAP→DAILY 전환 조건, FAILED 제외) */
    fun hasRemainingWork(): Boolean = collectTaskRepository.existsByStatusIn(listOf(PENDING, RUNNING))

    /** 해당 작업의 수집이 대기(PENDING) 중이거나 진행(RUNNING) 중인지 — API의 collecting 필드용. DONE·FAILED면 false */
    fun hasPendingWork(type: CollectTaskTypeEnum, payloadKey: String): Boolean =
        collectTaskRepository.findByTaskTypeAndPayloadKey(type, payloadKey)
            ?.let { it.status == PENDING || it.status == RUNNING }
            ?: false

    companion object {
        private const val HOST_LIMIT = 48 // locked_by 64자 상한에서 UUID 접미사(9자)를 제외한 안전 길이

        private const val SHIFT_LIMIT = 30 // 2^30 초과 분할 방지 — 재시도 상한(기본 8)보다 크다

        /**
         * 지수 백오프 — [retryBase] * 2^[attempts]에 [0, retryBase) 지터를 더한다.
         * 순수 함수 — [random]을 주입해 결정적으로 검증한다
         */
        fun backoffDelay(retryBase: Duration, attempts: Int, random: Random): Duration {
            val base = retryBase.multipliedBy(1L shl attempts.coerceAtMost(SHIFT_LIMIT))
            val jitterMs = random.nextLong(retryBase.toMillis().coerceAtLeast(1))
            return base.plusMillis(jitterMs)
        }
    }
}
