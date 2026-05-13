package org.agrfesta.sh.api.core.domain.areas

import org.agrfesta.sh.api.core.domain.devices.Device
import java.util.UUID

data class AreaDto(
    val uuid: UUID,
    val name: String,
    val isIndoor: Boolean
)

data class AreaDtoWithDevices(
    val uuid: UUID,
    val name: String,
    val sensors: Collection<Device> = emptyList(),
    val actuators: Collection<Device> = emptyList(),
    val isIndoor: Boolean
)
