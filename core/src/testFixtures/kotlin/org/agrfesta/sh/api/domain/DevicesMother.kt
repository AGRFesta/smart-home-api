package org.agrfesta.sh.api.domain

import org.agrfesta.sh.api.core.application.ports.outbounds.devices.DeviceDriver
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.DevicePrototype
import org.agrfesta.sh.api.core.domain.devices.Device
import org.agrfesta.sh.api.core.domain.devices.DeviceAggregate
import org.agrfesta.sh.api.core.domain.devices.DeviceAreaAssignment
import org.agrfesta.sh.api.core.domain.devices.ProviderDeviceData
import org.agrfesta.sh.api.core.domain.devices.DeviceModel
import org.agrfesta.sh.api.core.domain.devices.DeviceStatus
import org.agrfesta.sh.api.core.domain.devices.Provider
import org.agrfesta.test.mothers.aRandomUniqueString
import java.time.Instant
import java.util.*
import kotlin.reflect.KClass
import org.agrfesta.sh.api.core.domain.devices.DeviceFeature

fun aDevice(
    uuid: UUID = UUID.randomUUID(),
    providerId: String = aRandomUniqueString(),
    provider: Provider = Provider.SWITCHBOT,
    status: DeviceStatus = DeviceStatus.PAIRED,
    name: String = aRandomUniqueString(),
    model: DeviceModel = DeviceModel(aRandomUniqueString())
) = Device(uuid, status, providerId, provider, name, model)

// Thin aliases kept for readability at call sites; roles are now derived from [model] via the catalog.
fun aSensor(
    uuid: UUID = UUID.randomUUID(),
    providerId: String = aRandomUniqueString(),
    provider: Provider = Provider.SWITCHBOT,
    status: DeviceStatus = DeviceStatus.PAIRED,
    name: String = aRandomUniqueString(),
    model: DeviceModel = DeviceModel(aRandomUniqueString())
) = aDevice(uuid, providerId, provider, status, name, model)

fun anActuator(
    uuid: UUID = UUID.randomUUID(),
    providerId: String = aRandomUniqueString(),
    provider: Provider = Provider.SWITCHBOT,
    status: DeviceStatus = DeviceStatus.PAIRED,
    name: String = aRandomUniqueString(),
    model: DeviceModel = DeviceModel(aRandomUniqueString())
) = aDevice(uuid, providerId, provider, status, name, model)

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
    model = data.model
)

fun aDeviceAggregate(
    uuid: UUID = UUID.randomUUID(),
    status: DeviceStatus = DeviceStatus.PAIRED,
    providerId: String = aRandomUniqueString(),
    provider: Provider = Provider.SWITCHBOT,
    name: String = aRandomUniqueString(),
    model: DeviceModel = DeviceModel(aRandomUniqueString()),
    createdOn: Instant = Instant.now(),
    updatedOn: Instant? = null,
    assignments: List<DeviceAreaAssignment> = emptyList(),
    batteryLevel: Int? = null
) = DeviceAggregate(
    uuid, status, providerId, provider, name, model, createdOn, updatedOn, assignments, batteryLevel
)

fun aDevicePrototype(
    model: DeviceModel = DeviceModel(aRandomUniqueString()),
    provider: Provider = Provider.SWITCHBOT,
    driverType: KClass<out DeviceDriver> = DeviceDriver::class,
    roles: Set<DeviceFeature> = emptySet()
): DevicePrototype = object : DevicePrototype {
    override val model = model
    override val provider = provider
    override val driverType = driverType
    override val roles = roles
}

fun aProviderDeviceData(
    providerId: String = aRandomUniqueString(),
    provider: Provider = Provider.SWITCHBOT,
    name: String = aRandomUniqueString(),
    model: DeviceModel = DeviceModel(aRandomUniqueString())
) = ProviderDeviceData(providerId, provider, name, model)

// Thin aliases kept for readability at call sites; roles are now derived from [model] via the catalog.
fun aSensorProviderData(
    providerId: String = aRandomUniqueString(),
    provider: Provider = Provider.SWITCHBOT,
    name: String = aRandomUniqueString(),
    model: DeviceModel = DeviceModel(aRandomUniqueString())
) = ProviderDeviceData(providerId, provider, name, model)

fun anActuatorProviderData(
    providerId: String = aRandomUniqueString(),
    provider: Provider = Provider.SWITCHBOT,
    name: String = aRandomUniqueString(),
    model: DeviceModel = DeviceModel(aRandomUniqueString())
) = ProviderDeviceData(providerId, provider, name, model)
