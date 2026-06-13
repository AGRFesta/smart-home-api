package org.agrfesta.sh.api.domain

import org.agrfesta.sh.api.core.domain.devices.Device
import org.agrfesta.sh.api.core.domain.devices.DeviceAggregate
import org.agrfesta.sh.api.core.domain.devices.DeviceAreaAssignment
import org.agrfesta.sh.api.core.domain.devices.ProviderDeviceData
import org.agrfesta.sh.api.core.domain.devices.DeviceFeature.SENSOR
import org.agrfesta.sh.api.core.domain.devices.DeviceFeature.ACTUATOR
import org.agrfesta.sh.api.core.domain.devices.DeviceStatus
import org.agrfesta.sh.api.core.domain.devices.Provider
import org.agrfesta.test.mothers.aRandomUniqueString
import java.time.Instant
import java.util.*
import org.agrfesta.sh.api.core.domain.devices.DeviceFeature

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
) = Device(uuid, status, providerId, provider, name, additionalFeatures + SENSOR)

fun anActuator(
    uuid: UUID = UUID.randomUUID(),
    providerId: String = aRandomUniqueString(),
    provider: Provider = Provider.SWITCHBOT,
    status: DeviceStatus = DeviceStatus.PAIRED,
    name: String = aRandomUniqueString(),
    additionalFeatures: Set<DeviceFeature> = emptySet()
) = Device(uuid, status, providerId, provider, name, additionalFeatures + ACTUATOR)

fun aDevice(
    data: ProviderDeviceData,
    uuid: UUID = UUID.randomUUID(),
    status: DeviceStatus = DeviceStatus.PAIRED
) = Device(
    uuid = uuid,
    status = status,
    deviceProviderId = data.deviceProviderId,
    provider = data.provider,
    name = data.name,
    features = data.features
)

fun aDeviceAggregate(
    uuid: UUID = UUID.randomUUID(),
    status: DeviceStatus = DeviceStatus.PAIRED,
    providerId: String = aRandomUniqueString(),
    provider: Provider = Provider.SWITCHBOT,
    name: String = aRandomUniqueString(),
    features: Set<DeviceFeature> = emptySet(),
    createdOn: Instant = Instant.now(),
    updatedOn: Instant? = null,
    assignments: List<DeviceAreaAssignment> = emptyList()
) = DeviceAggregate(uuid, status, providerId, provider, name, features, createdOn, updatedOn, assignments)

fun aProviderDeviceData(
    providerId: String = aRandomUniqueString(),
    provider: Provider = Provider.SWITCHBOT,
    name: String = aRandomUniqueString(),
    features: Set<DeviceFeature> = emptySet()
) = ProviderDeviceData(providerId, provider, name, features)

fun aSensorProviderData(
    providerId: String = aRandomUniqueString(),
    provider: Provider = Provider.SWITCHBOT,
    name: String = aRandomUniqueString(),
    additionalFeatures: Set<DeviceFeature> = emptySet()
) = ProviderDeviceData(providerId, provider, name, additionalFeatures + SENSOR)

fun anActuatorProviderData(
    providerId: String = aRandomUniqueString(),
    provider: Provider = Provider.SWITCHBOT,
    name: String = aRandomUniqueString(),
    additionalFeatures: Set<DeviceFeature> = emptySet()
) = ProviderDeviceData(providerId, provider, name, additionalFeatures + ACTUATOR)
