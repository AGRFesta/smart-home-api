package org.agrfesta.sh.api.domain

import org.agrfesta.sh.api.domain.devices.Device
import org.agrfesta.sh.api.domain.devices.DeviceDataValue
import org.agrfesta.sh.api.domain.devices.DeviceFeature
import org.agrfesta.sh.api.domain.devices.DeviceStatus
import org.agrfesta.sh.api.domain.devices.Provider
import org.agrfesta.test.mothers.aRandomUniqueString
import java.util.*

fun aDevice(
    uuid: UUID = UUID.randomUUID(),
    providerId: String = aRandomUniqueString(),
    provider: Provider = Provider.SWITCHBOT,
    status: DeviceStatus = DeviceStatus.PAIRED,
    name: String = aRandomUniqueString(),
    features: Set<DeviceFeature> = emptySet()
) = Device(uuid, status, providerId, provider, name, features)

fun aSensor(
    uuid: UUID = UUID.randomUUID(),
    providerId: String = aRandomUniqueString(),
    provider: Provider = Provider.SWITCHBOT,
    status: DeviceStatus = DeviceStatus.PAIRED,
    name: String = aRandomUniqueString(),
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
    providerId: String = aRandomUniqueString(),
    provider: Provider = Provider.SWITCHBOT,
    name: String = aRandomUniqueString(),
    features: Set<DeviceFeature> = emptySet()
) = DeviceDataValue(providerId, provider, name, features)
