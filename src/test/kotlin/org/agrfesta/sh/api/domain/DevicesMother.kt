package org.agrfesta.sh.api.domain

import org.agrfesta.sh.api.domain.devices.DeviceDto
import org.agrfesta.sh.api.domain.devices.DeviceDataValue
import org.agrfesta.sh.api.domain.devices.DeviceFeature.SENSOR
import org.agrfesta.sh.api.domain.devices.DeviceFeature.ACTUATOR
import org.agrfesta.sh.api.domain.devices.DeviceStatus
import org.agrfesta.sh.api.domain.devices.Provider
import org.agrfesta.test.mothers.aRandomUniqueString
import java.util.*
import org.agrfesta.sh.api.domain.devices.DeviceFeature

fun aDevice(
    uuid: UUID = UUID.randomUUID(),
    providerId: String = aRandomUniqueString(),
    provider: Provider = Provider.SWITCHBOT,
    status: DeviceStatus = DeviceStatus.PAIRED,
    name: String = aRandomUniqueString(),
    features: Set<DeviceFeature> = emptySet()
) = DeviceDto(uuid, status, providerId, provider, name, features)

fun aSensor(
    uuid: UUID = UUID.randomUUID(),
    providerId: String = aRandomUniqueString(),
    provider: Provider = Provider.SWITCHBOT,
    status: DeviceStatus = DeviceStatus.PAIRED,
    name: String = aRandomUniqueString(),
    additionalFeatures: Set<DeviceFeature> = emptySet()
) = DeviceDto(uuid, status, providerId, provider, name, additionalFeatures + SENSOR)

fun aDevice(
    data: DeviceDataValue,
    uuid: UUID = UUID.randomUUID(),
    status: DeviceStatus = DeviceStatus.PAIRED
) = DeviceDto(
    uuid = uuid,
    status = status,
    deviceProviderId = data.deviceProviderId,
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

fun aSensorDataValue(
    providerId: String = aRandomUniqueString(),
    provider: Provider = Provider.SWITCHBOT,
    name: String = aRandomUniqueString(),
    additionalFeatures: Set<DeviceFeature> = emptySet()
) = DeviceDataValue(providerId, provider, name, additionalFeatures + SENSOR)

fun anActuatorDataValue(
    providerId: String = aRandomUniqueString(),
    provider: Provider = Provider.SWITCHBOT,
    name: String = aRandomUniqueString(),
    additionalFeatures: Set<DeviceFeature> = emptySet()
) = DeviceDataValue(providerId, provider, name, additionalFeatures + ACTUATOR)

