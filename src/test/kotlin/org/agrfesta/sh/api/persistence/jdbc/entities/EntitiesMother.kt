package org.agrfesta.sh.api.persistence.jdbc.entities

import org.agrfesta.sh.api.domain.devices.DeviceFeature
import org.agrfesta.sh.api.domain.devices.DeviceFeature.SENSOR
import org.agrfesta.sh.api.domain.devices.DeviceStatus
import org.agrfesta.sh.api.domain.devices.Provider
import org.agrfesta.sh.api.domain.devices.SensorDataType
import org.agrfesta.test.mothers.aRandomHumidity
import org.agrfesta.test.mothers.aRandomTemperature
import org.agrfesta.test.mothers.aRandomUniqueString
import java.math.BigDecimal
import java.time.Instant
import java.util.*
import org.agrfesta.sh.api.domain.devices.DeviceFeature.ACTUATOR

fun anAreaEntity(
    uuid: UUID = UUID.randomUUID(),
    name: String = aRandomUniqueString(),
    isIndoor: Boolean = true,
    createdOn: Instant = Instant.now(),
    updatedOn: Instant? = null
) = AreaEntity(uuid, name, isIndoor, createdOn, updatedOn)

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

fun aSensorEntity(
    uuid: UUID = UUID.randomUUID(),
    name: String = aRandomUniqueString(),
    provider: Provider = Provider.entries.toTypedArray().random(),
    status: DeviceStatus = DeviceStatus.entries.toTypedArray().random(),
    providerId: String = aRandomUniqueString(),
    features: MutableSet<DeviceFeature> = mutableSetOf(SENSOR),
    createdOn: Instant = Instant.now(),
    updatedOn: Instant? = null
) = aDeviceEntity(uuid, name, provider, status, providerId, features, createdOn, updatedOn)

fun anActuatorEntity(
    uuid: UUID = UUID.randomUUID(),
    name: String = aRandomUniqueString(),
    provider: Provider = Provider.entries.toTypedArray().random(),
    status: DeviceStatus = DeviceStatus.entries.toTypedArray().random(),
    providerId: String = aRandomUniqueString(),
    features: MutableSet<DeviceFeature> = mutableSetOf(ACTUATOR),
    createdOn: Instant = Instant.now(),
    updatedOn: Instant? = null
) = aDeviceEntity(uuid, name, provider, status, providerId, features, createdOn, updatedOn)

fun aSensorAssignmentEntity(
    uuid: UUID = UUID.randomUUID(),
    areaUuid: UUID = UUID.randomUUID(),
    deviceUuid: UUID = UUID.randomUUID(),
    connectedOn: Instant = Instant.now(),
    disconnectedOn: Instant? = null
) = SensorAssignmentEntity(uuid = uuid, areaUuid = areaUuid, sensorUuid = deviceUuid, connectedOn = connectedOn,
    disconnectedOn = disconnectedOn)

fun anActuatorAssignmentEntity(
    uuid: UUID = UUID.randomUUID(),
    areaUuid: UUID = UUID.randomUUID(),
    deviceUuid: UUID = UUID.randomUUID()
) = ActuatorAssignmentEntity(uuid = uuid, areaUuid = areaUuid, actuatorUuid = deviceUuid)

fun aSensorHistoryDataEntity(
    sensor: DeviceEntity = aSensorEntity(),
    time: Instant = Instant.now(),
    type: SensorDataType = SensorDataType.entries.toTypedArray().random(),
    value: BigDecimal = when (type) {
        SensorDataType.TEMPERATURE -> aRandomTemperature()
        SensorDataType.HUMIDITY -> aRandomHumidity().value
    }
) = SensorHistoryDataEntity(sensor.uuid, time, type, value)
