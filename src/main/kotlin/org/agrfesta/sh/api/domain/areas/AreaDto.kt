package org.agrfesta.sh.api.domain.areas

import org.agrfesta.sh.api.domain.devices.DeviceDto
import java.util.UUID

data class AreaDto(
    val uuid: UUID,
    val name: String,
    val isIndoor: Boolean
)

data class AreaDtoWithDevices(
    val uuid: UUID,
    val name: String,
    val devices: Collection<DeviceDto> = emptyList(),
    val isIndoor: Boolean
)
