package org.agrfesta.sh.api

import org.agrfesta.sh.api.providers.netatmo.NetatmoConfiguration
import org.agrfesta.sh.api.providers.switchbot.SwitchBotConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling

@Configuration
@EnableConfigurationProperties(
    SwitchBotConfiguration::class,
    NetatmoConfiguration::class
)
@EnableScheduling
class AppConfig
