package org.agrfesta.sh.domain

import java.util.UUID

interface Device {
    val uuid: UUID
    val providerId: String
    val provider: Provider
    val name: String
}
