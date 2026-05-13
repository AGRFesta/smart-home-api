package org.agrfesta.sh.api.domain

import java.util.*
import org.agrfesta.sh.api.core.domain.areas.AreaDto
import org.agrfesta.sh.api.core.domain.areas.AreaDtoWithDevices
import org.agrfesta.sh.api.core.domain.devices.Device
import org.agrfesta.test.mothers.aRandomUniqueString

fun anAreaDto(
    uuid: UUID = UUID.randomUUID(),
    name: String = aRandomUniqueString(),
    isIndoor: Boolean = true
) = AreaDto(uuid, name, isIndoor)

fun anAreaDtoWithDevices(
    uuid: UUID = UUID.randomUUID(),
    name: String = aRandomUniqueString(),
    sensors: Collection<Device> = emptyList(),
    actuators: Collection<Device> = emptyList(),
    isIndoor: Boolean = true
) = AreaDtoWithDevices(
    uuid = uuid,
    name = name,
    sensors = sensors,
    actuators = actuators,
    isIndoor = isIndoor
)
