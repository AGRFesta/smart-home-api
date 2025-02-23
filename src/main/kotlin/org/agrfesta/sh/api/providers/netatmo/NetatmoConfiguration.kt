package org.agrfesta.sh.api.providers.netatmo

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "providers.netatmo")
data class NetatmoConfiguration(
    val clientId: String,
    val clientSecret: String,
    val baseUrl: String,
    val homeId: String
)
