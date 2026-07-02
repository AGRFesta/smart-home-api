package org.agrfesta.sh.api.persistence.jdbc.entities

import org.agrfesta.sh.api.core.domain.devices.DeviceModel
import org.agrfesta.sh.api.core.domain.devices.DeviceStatus
import org.agrfesta.sh.api.core.domain.devices.Provider
import org.agrfesta.sh.api.core.domain.devices.SensorDataType
import org.agrfesta.test.mothers.aRandomHumidity
import org.agrfesta.test.mothers.aRandomTemperature
import org.agrfesta.test.mothers.aRandomUniqueString
import java.math.BigDecimal
import java.time.Instant
import java.util.*

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
    createdOn: Instant = Instant.now(),
    updatedOn: Instant? = null,
    model: DeviceModel = DeviceModel(aRandomUniqueString())
) = DeviceEntity(uuid, providerId, provider, name, status, createdOn, updatedOn, model)

// Thin aliases kept for readability at call sites; roles are now derived from [model] via the catalog.
fun aSensorEntity(
    uuid: UUID = UUID.randomUUID(),
    name: String = aRandomUniqueString(),
    provider: Provider = Provider.entries.toTypedArray().random(),
    status: DeviceStatus = DeviceStatus.entries.toTypedArray().random(),
    providerId: String = aRandomUniqueString(),
    createdOn: Instant = Instant.now(),
    updatedOn: Instant? = null,
    model: DeviceModel = DeviceModel(aRandomUniqueString())
) = aDeviceEntity(uuid, name, provider, status, providerId, createdOn, updatedOn, model)

fun anActuatorEntity(
    uuid: UUID = UUID.randomUUID(),
    name: String = aRandomUniqueString(),
    provider: Provider = Provider.entries.toTypedArray().random(),
    status: DeviceStatus = DeviceStatus.entries.toTypedArray().random(),
    providerId: String = aRandomUniqueString(),
    createdOn: Instant = Instant.now(),
    updatedOn: Instant? = null,
    model: DeviceModel = DeviceModel(aRandomUniqueString())
) = aDeviceEntity(uuid, name, provider, status, providerId, createdOn, updatedOn, model)

fun aSensorAssignmentEntity(
    uuid: UUID = UUID.randomUUID(),
    areaUuid: UUID = UUID.randomUUID(),
    deviceUuid: UUID = UUID.randomUUID(),
    connectedOn: Instant = Instant.now(),
    disconnectedOn: Instant? = null
) = SensorAssignmentEntity(
    uuid = uuid,
    areaUuid = areaUuid,
    sensorUuid = deviceUuid,
    connectedOn = connectedOn,
    disconnectedOn = disconnectedOn
)

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
        SensorDataType.TEMPERATURE -> aRandomTemperature().value
        SensorDataType.HUMIDITY -> aRandomHumidity().value
    }
) = SensorHistoryDataEntity(sensor.uuid, time, type, value)
