package org.agrfesta.sh.api.domain

import org.agrfesta.sh.api.domain.devices.Device
import org.agrfesta.sh.api.domain.devices.DeviceDataValue
import org.agrfesta.sh.api.domain.devices.DeviceFeature
import org.agrfesta.sh.api.domain.devices.DeviceStatus
import org.agrfesta.sh.api.domain.devices.Provider
import java.util.*

fun aDevice(
    uuid: UUID = UUID.randomUUID(),
    providerId: String = UUID.randomUUID().toString(),
    provider: Provider = Provider.SWITCHBOT,
    status: DeviceStatus = DeviceStatus.PAIRED,
    name: String = UUID.randomUUID().toString(),
    features: Set<DeviceFeature> = emptySet()
) = Device(uuid, status, providerId, provider, name, features)

fun aSensor(
    uuid: UUID = UUID.randomUUID(),
    providerId: String = UUID.randomUUID().toString(),
    provider: Provider = Provider.SWITCHBOT,
    status: DeviceStatus = DeviceStatus.PAIRED,
    name: String = UUID.randomUUID().toString(),
    additionalFeatures: Set<DeviceFeature> = emptySet()
) = Device(uuid, status, providerId, provider, name, additionalFeatures + DeviceFeature.SENSOR)

fun aDevice(
    data: DeviceDataValue,
    uuid: UUID = UUID.randomUUID(),
    status: DeviceStatus = DeviceStatus.PAIRED
) = Device(
    uuid = uuid,
    status = status,
    providerId = data.providerId,
    provider = data.provider,
    name = data.name,
    features = data.features
)

fun aDeviceDataValue(
    providerId: String = UUID.randomUUID().toString(),
    provider: Provider = Provider.SWITCHBOT,
    name: String = UUID.randomUUID().toString(),
    features: Set<DeviceFeature> = emptySet()
) = DeviceDataValue(providerId, provider, name, features)
