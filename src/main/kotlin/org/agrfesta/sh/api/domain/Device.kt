package org.agrfesta.sh.api.domain

data class Device (
    val providerId: String,
    val provider: Provider,
    val name: String,
    val status: DeviceStatus
)
