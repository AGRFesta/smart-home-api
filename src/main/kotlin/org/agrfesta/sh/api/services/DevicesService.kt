package org.agrfesta.sh.api.services

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import java.util.*
import org.agrfesta.sh.api.domain.devices.Device
import org.agrfesta.sh.api.domain.devices.DeviceDataValue
import org.agrfesta.sh.api.domain.devices.DeviceStatus
import org.agrfesta.sh.api.domain.failures.PersistenceFailure
import org.agrfesta.sh.api.persistence.DevicesDao
import org.springframework.stereotype.Service

@Service
class DevicesService(
    private val devicesDao: DevicesDao
) {

    fun createDevice(
        device: DeviceDataValue,
        initialStatus: DeviceStatus = DeviceStatus.PAIRED
    ): Either<PersistenceFailure, UUID> = try {
        devicesDao.create(device, initialStatus).right()
    } catch (e: Exception) {
        PersistenceFailure(e).left()
    }

    fun update(device: Device): Either<PersistenceFailure, Unit> = try {
        devicesDao.update(device).right()
    } catch (e: Exception) {
        PersistenceFailure(e).left()
    }

    fun refresh(providersDevices: Collection<DeviceDataValue>, devices: Collection<Device>): DevicesRefreshResult {
        return DevicesRefreshResult(
            newDevices = providersDevices
                .filter { devices.find(it.providerId) == null },
            updatedDevices = providersDevices
                .mapNotNull { devices.find(it.providerId)?.copy(name = it.name, status = DeviceStatus.PAIRED) },
            detachedDevices = devices
                .filter { providersDevices.find(it.providerId) == null }
                .map { it.copy(status = DeviceStatus.DETACHED) }
        )
    }

    fun getAll(): Either<PersistenceFailure, Collection<Device>> = try {
        devicesDao.getAll().right()
    } catch (e: Exception) {
        PersistenceFailure(e).left()
    }

    private fun Collection<Device>.find(providerId: String): Device? = firstOrNull { it.providerId == providerId }
    private fun Collection<DeviceDataValue>.find(providerId: String): DeviceDataValue? =
        firstOrNull { it.providerId == providerId }

}

data class DevicesRefreshResult(
    val newDevices: Collection<DeviceDataValue> = emptyList(),
    val updatedDevices: Collection<Device> = emptyList(),
    val detachedDevices: Collection<Device> = emptyList()
)
