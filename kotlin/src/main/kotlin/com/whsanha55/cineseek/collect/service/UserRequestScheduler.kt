package com.whsanha55.cineseek.collect.service

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 사용자 요청 스케줄러 — 인물 페이지 조회가 등록한 필모 수집을 정기 틱(CollectScheduler)과 별개로 짧은 주기로 처리한다.
 * 정기 틱은 사용자 요청을 잡지 않으므로 CollectScheduler와 같은 조건(cineseek.collect.enabled=true)으로 함께 등록된다.
 * 정기 틱이 도는 동안에도 돌 수 있게 spring.task.scheduling.pool.size를 2 이상으로 둔다
 */
@Component
@ConditionalOnProperty(prefix = "cineseek.collect", name = ["enabled"], havingValue = "true")
class UserRequestScheduler(private val pipeline: CollectPipeline) {

    /** 중복 실행 가드 — 정기 틱과 따로 둔다. 같은 가드를 쓰면 정기 틱이 도는 동안(최대 maxRuntime) 막힌다 */
    private val ticking = AtomicBoolean(false)

    @Scheduled(fixedDelayString = "\${cineseek.collect.user-request-interval}")
    fun tick() {
        if (!ticking.compareAndSet(false, true)) return
        try {
            pipeline.runUserRequests()
        } finally {
            ticking.set(false)
        }
    }
}
