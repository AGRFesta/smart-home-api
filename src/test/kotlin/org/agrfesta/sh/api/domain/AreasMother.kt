package org.agrfesta.sh.api.domain

import java.util.*
import org.agrfesta.sh.api.domain.areas.AreaDto
import org.agrfesta.sh.api.domain.areas.AreaDtoWithDevices
import org.agrfesta.sh.api.domain.devices.DeviceDto
import org.agrfesta.test.mothers.aRandomUniqueString

fun anAreaDto(
    uuid: UUID = UUID.randomUUID(),
    name: String = aRandomUniqueString(),
    isIndoor: Boolean = true
) = AreaDto(uuid, name, isIndoor)

fun anAreaDtoWithDevices(
    uuid: UUID = UUID.randomUUID(),
    name: String = aRandomUniqueString(),
    sensors: Collection<DeviceDto> = emptyList(),
    actuators: Collection<DeviceDto> = emptyList(),
    isIndoor: Boolean = true
) = AreaDtoWithDevices(
    uuid = uuid,
    name = name,
    sensors = sensors,
    actuators = actuators,
    isIndoor = isIndoor
)
