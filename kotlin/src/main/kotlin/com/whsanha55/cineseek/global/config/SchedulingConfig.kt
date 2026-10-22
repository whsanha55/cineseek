package com.whsanha55.cineseek.global.config

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling

/**
 * @Scheduled 활성화 — CollectScheduler가 조건부 빈(cineseek.collect.enabled)이라도 이 설정이 있어야 주기가 잡힌다.
 * 배치(cineseek.job)로 띄운 프로세스에서는 끈다 — 상주 api와 수집 틱·분위기 태깅을 겹쳐 돌리지 않게.
 * job 기본값이 빈 문자열이라 @ConditionalOnProperty로는 "비어 있음"을 가를 수 없어 SpEL로 판단한다
 */
@Configuration
@ConditionalOnExpression("'\${cineseek.job:}'.isEmpty()")
@EnableScheduling
class SchedulingConfig
