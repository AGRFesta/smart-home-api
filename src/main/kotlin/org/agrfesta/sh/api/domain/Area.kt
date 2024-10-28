package org.agrfesta.sh.api.domain

import org.agrfesta.sh.api.domain.devices.Device
import java.util.UUID

data class Area(
    val uuid: UUID,
    val name: String,
    val devices: Collection<Device> = emptyList(),
    val isIndoor: Boolean
)
