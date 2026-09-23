package com.whsanha55.cineseek

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class CineseekApplication

fun main(args: Array<String>) {
	runApplication<CineseekApplication>(*args)
}
