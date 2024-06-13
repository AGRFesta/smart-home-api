package org.agrfesta.sh.api

import org.agrfesta.sh.api.providers.switchbot.SwitchBotConfiguration
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableConfigurationProperties(SwitchBotConfiguration::class)
class CreativeAssistantApplication

fun main(args: Array<String>) {
    runApplication<CreativeAssistantApplication>(*args)
}
