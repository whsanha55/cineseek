package com.whsanha55.cineseek.global.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

/** 현재 시각은 이 Clock으로 구한다 — 테스트에서 Clock.fixed로 바꿔 끼운다 */
@Configuration
class ClockConfig {

    @Bean
    fun clock(): Clock = Clock.systemUTC()
}
