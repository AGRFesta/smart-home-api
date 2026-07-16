package org.agrfesta.sh.api.providers.hon

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "providers.hon")
data class HonConfiguration(
    val enabled: Boolean = false,
    val email: String,
    val password: String,
    val authApiUrl: String = "https://account2.hon-smarthome.com",
    val apiUrl: String = "https://api-iot.he.services",
)
