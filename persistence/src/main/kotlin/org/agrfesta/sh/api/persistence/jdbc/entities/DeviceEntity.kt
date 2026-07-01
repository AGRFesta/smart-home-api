package org.agrfesta.sh.api.persistence.jdbc.entities

import org.agrfesta.sh.api.core.domain.devices.Device
import org.agrfesta.sh.api.core.domain.devices.DeviceFeature
import org.agrfesta.sh.api.core.domain.devices.DeviceModel
import org.agrfesta.sh.api.core.domain.devices.DeviceStatus
import org.agrfesta.sh.api.core.domain.devices.Provider
import java.time.Instant
import java.util.UUID

@Suppress("LongParameterList")
class DeviceEntity(
    val uuid: UUID,
    val providerId: String,
    val provider: Provider,
    var name: String,
    var status: DeviceStatus,
    val features: MutableSet<DeviceFeature>,
    val createdOn: Instant,
    var updatedOn: Instant? = null,
    val model: DeviceModel? = null
) {
    fun toDevice() = Device(uuid, status, providerId, provider, name, features, model)
}
