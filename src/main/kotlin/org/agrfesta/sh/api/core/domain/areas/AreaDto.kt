package org.agrfesta.sh.api.core.domain.areas

import org.agrfesta.sh.api.core.domain.devices.DeviceDto
import java.util.UUID

data class AreaDto(
    val uuid: UUID,
    val name: String,
    val isIndoor: Boolean
)

data class AreaDtoWithDevices(
    val uuid: UUID,
    val name: String,
    val sensors: Collection<DeviceDto> = emptyList(),
    val actuators: Collection<DeviceDto> = emptyList(),
    val isIndoor: Boolean
)
