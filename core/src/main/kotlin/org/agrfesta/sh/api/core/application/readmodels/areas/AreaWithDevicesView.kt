package org.agrfesta.sh.api.core.application.readmodels.areas

import org.agrfesta.sh.api.core.domain.devices.Device
import java.util.UUID

data class AreaWithDevicesView(
    val uuid: UUID,
    val name: String,
    val sensors: Collection<Device> = emptyList(),
    val actuators: Collection<Device> = emptyList(),
    val isIndoor: Boolean
)
