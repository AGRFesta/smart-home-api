package org.agrfesta.sh.api.domain

import java.util.UUID

data class Device (
    val uuid: UUID = UUID.randomUUID(),
    val providerId: String,
    val provider: Provider,
    val name: String
)

data class ProviderDevice (
    val id: String,
    val provider: Provider,
    val name: String
) {
    fun toDevice() = Device(
        providerId = id,
        provider = provider,
        name = name
    )
}
