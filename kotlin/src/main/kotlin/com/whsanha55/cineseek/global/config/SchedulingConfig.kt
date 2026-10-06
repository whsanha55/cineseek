package com.whsanha55.cineseek.global.config

import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling

/** @Scheduled 활성화 — CollectScheduler가 조건부 빈(cineseek.collect.enabled)이라도 이 설정이 있어야 주기가 잡힌다 */
@Configuration
@EnableScheduling
class SchedulingConfig
