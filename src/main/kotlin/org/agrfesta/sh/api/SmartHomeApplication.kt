package org.agrfesta.sh.api

import org.agrfesta.sh.api.providers.netatmo.NetatmoConfiguration
import org.agrfesta.sh.api.providers.switchbot.SwitchBotConfiguration
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableConfigurationProperties(
    SwitchBotConfiguration::class,
    NetatmoConfiguration::class)
@EnableScheduling
class SmartHomeApplication

fun main(args: Array<String>) {
    runApplication<SmartHomeApplication>(*args)
}
