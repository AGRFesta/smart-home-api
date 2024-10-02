package org.agrfesta.sh.api.persistence.jdbc.entities

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
    features: MutableSet<DeviceFeature> = mutableSetOf(),
    createdOn: Instant = Instant.now(),
    updatedOn: Instant? = null
) = DeviceEntity(uuid, providerId, provider, name, status, features, createdOn, updatedOn)

fun anAssociationEntity(
    uuid: UUID = UUID.randomUUID(),
    roomUuid: UUID = UUID.randomUUID(),
    deviceUuid: UUID = UUID.randomUUID(),
    connectedOn: Instant = Instant.now(),
    disconnectedOn: Instant? = null
) = AssociationEntity(uuid = uuid, roomUuid = roomUuid, deviceUuid = deviceUuid, connectedOn = connectedOn,
    disconnectedOn = disconnectedOn)
