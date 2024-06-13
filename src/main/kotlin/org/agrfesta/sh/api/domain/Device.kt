package org.agrfesta.sh.api.domain

import java.util.UUID

data class Device (
    val uuid: UUID,
    val providerId: String,
    val provider: Provider,
    val name: String
)
