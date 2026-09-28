package com.whsanha55.cineseek.global.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.auditing.DateTimeProvider
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import java.time.Clock
import java.time.Instant
import java.util.Optional

@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
@Configuration
class JpaConfig {

    @Bean
    fun auditingDateTimeProvider(clock: Clock) = DateTimeProvider { Optional.of(Instant.now(clock)) }
}
