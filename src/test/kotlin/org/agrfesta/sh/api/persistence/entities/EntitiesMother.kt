package org.agrfesta.sh.api.persistence.entities

import org.agrfesta.sh.api.domain.devices.DeviceFeature
import org.agrfesta.sh.api.domain.devices.DeviceStatus
import org.agrfesta.sh.api.domain.devices.Provider
import org.agrfesta.test.mothers.aRandomUniqueString
import java.time.Instant
import java.util.*

fun aRoomEntity(
    uuid: UUID = UUID.randomUUID(),
    name: String = aRandomUniqueString(),
    createdOn: Instant = Instant.now(),
    updatedOn: Instant? = null
) = RoomEntity(uuid, name, createdOn, updatedOn)

fun aDeviceEntity(
    uuid: UUID = UUID.randomUUID(),
    name: String = aRandomUniqueString(),
    provider: Provider = Provider.entries.toTypedArray().random(),
    status: DeviceStatus = DeviceStatus.entries.toTypedArray().random(),
    providerId: String = aRandomUniqueString(),
    features: Set<DeviceFeature> = emptySet(),
    createdOn: Instant = Instant.now(),
    updatedOn: Instant? = null
) = DeviceEntity(uuid, name, provider, status, providerId, features.map { it.name }.toTypedArray(), createdOn, updatedOn)

fun anAssociationEntity(
    uuid: UUID = UUID.randomUUID(),
    room: RoomEntity = aRoomEntity(),
    device: DeviceEntity = aDeviceEntity(),
    connectedOn: Instant = Instant.now(),
    disconnectedOn: Instant? = null
) = AssociationEntity(uuid, room, device, connectedOn, disconnectedOn)
