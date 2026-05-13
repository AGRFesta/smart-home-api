package org.agrfesta.sh.api.providers.switchbot

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "providers.switchbot")
data class SwitchBotConfiguration(
    val baseUrl: String,
    val token: String,
    val secret: String
)
