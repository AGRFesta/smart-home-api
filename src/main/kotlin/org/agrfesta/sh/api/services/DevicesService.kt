package org.agrfesta.sh.api.services

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import java.util.*
import org.agrfesta.sh.api.domain.devices.DeviceDto
import org.agrfesta.sh.api.domain.devices.DeviceDataValue
import org.agrfesta.sh.api.domain.devices.DeviceStatus
import org.agrfesta.sh.api.domain.devices.Device
import org.agrfesta.sh.api.domain.devices.ProviderDevicesFactory
import org.agrfesta.sh.api.domain.failures.PersistenceFailure
import org.agrfesta.sh.api.persistence.DevicesDao
import org.springframework.stereotype.Service

@Service
class DevicesService(
    private val devicesDao: DevicesDao,
    providerDevicesFactories: Collection<ProviderDevicesFactory>
) {
    private val mappedDevicesFactories = providerDevicesFactories.associateBy { it.provider }

    fun createDevice(
        device: DeviceDataValue,
        initialStatus: DeviceStatus = DeviceStatus.PAIRED
    ): Either<PersistenceFailure, UUID> = try {
        devicesDao.create(device, initialStatus).right()
    } catch (e: Exception) {
        PersistenceFailure(e).left()
    }

    fun update(device: DeviceDto): Either<PersistenceFailure, Unit> = try {
        devicesDao.update(device).right()
    } catch (e: Exception) {
        PersistenceFailure(e).left()
    }

    fun refresh(providersDevices: Collection<DeviceDataValue>, devices: Collection<DeviceDto>): DevicesRefreshResult {
        return DevicesRefreshResult(
            newDevices = providersDevices
                .filter { devices.find(it.deviceProviderId) == null },
            updatedDevices = providersDevices
                .mapNotNull { devices.find(it.deviceProviderId)?.copy(name = it.name, status = DeviceStatus.PAIRED) },
            detachedDevices = devices
                .filter { providersDevices.find(it.deviceProviderId) == null }
                .map { it.copy(status = DeviceStatus.DETACHED) }
        )
    }

    fun getAllDto(): Either<PersistenceFailure, Collection<DeviceDto>> = try {
        devicesDao.getAll().right()
    } catch (e: Exception) {
        PersistenceFailure(e).left()
    }

    fun getAllDevices(): Either<PersistenceFailure, Collection<Device>> = try {
        devicesDao.getAll().map { dto ->
            val factory = mappedDevicesFactories[dto.provider]!!
            factory.createDevice(dto)
        }.right()
    } catch (e: Exception) {
        PersistenceFailure(e).left()
    }

    private fun Collection<DeviceDto>.find(providerId: String): DeviceDto? =
        firstOrNull { it.deviceProviderId == providerId }
    private fun Collection<DeviceDataValue>.find(providerId: String): DeviceDataValue? =
        firstOrNull { it.deviceProviderId == providerId }

}

data class DevicesRefreshResult(
    val newDevices: Collection<DeviceDataValue> = emptyList(),
    val updatedDevices: Collection<DeviceDto> = emptyList(),
    val detachedDevices: Collection<DeviceDto> = emptyList()
)
