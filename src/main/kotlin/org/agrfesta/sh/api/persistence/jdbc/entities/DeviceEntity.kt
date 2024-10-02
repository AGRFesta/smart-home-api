package org.agrfesta.sh.api.persistence.jdbc.entities

import org.agrfesta.sh.api.domain.devices.Device
import org.agrfesta.sh.api.domain.devices.DeviceFeature
import org.agrfesta.sh.api.domain.devices.DeviceStatus
import org.agrfesta.sh.api.domain.devices.Provider
import java.time.Instant
import java.util.UUID

class DeviceEntity(
    val uuid: UUID,
    val providerId: String,
    val provider: Provider,
    var name: String,
    var status: DeviceStatus,
    val features: MutableSet<DeviceFeature>,
    val createdOn: Instant,
    var updatedOn: Instant? = null
) {
    fun asDevice() = Device(providerId, provider, name, status, features)
}
