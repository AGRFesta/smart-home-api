package org.agrfesta.sh.api.domain

import java.util.*
import org.agrfesta.sh.api.core.application.readmodels.areas.AreaView
import org.agrfesta.sh.api.core.application.readmodels.areas.AreaWithDevicesView
import org.agrfesta.sh.api.core.domain.devices.Device
import org.agrfesta.test.mothers.aRandomUniqueString

fun anAreaView(
    uuid: UUID = UUID.randomUUID(),
    name: String = aRandomUniqueString(),
    isIndoor: Boolean = true
) = AreaView(uuid, name, isIndoor)

fun anAreaWithDevicesView(
    uuid: UUID = UUID.randomUUID(),
    name: String = aRandomUniqueString(),
    sensors: Collection<Device> = emptyList(),
    actuators: Collection<Device> = emptyList(),
    isIndoor: Boolean = true
) = AreaWithDevicesView(
    uuid = uuid,
    name = name,
    sensors = sensors,
    actuators = actuators,
    isIndoor = isIndoor
)
