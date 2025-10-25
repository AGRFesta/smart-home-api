package org.agrfesta.sh.api.domain

import org.agrfesta.sh.api.domain.devices.DeviceDto
import java.util.UUID

data class Area(
    val uuid: UUID,
    val name: String,
    val isIndoor: Boolean
)

data class AreaWithDevices(
    val uuid: UUID,
    val name: String,
    val devices: Collection<DeviceDto> = emptyList(),
    val isIndoor: Boolean
)
