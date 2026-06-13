package org.agrfesta.sh.api.persistence.jdbc.adapters

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.DevicesRepository
import org.agrfesta.sh.api.core.domain.devices.Device
import org.agrfesta.sh.api.core.domain.devices.DeviceFeature
import org.agrfesta.sh.api.core.domain.devices.DeviceStatus
import org.agrfesta.sh.api.core.domain.devices.Provider
import org.agrfesta.sh.api.core.domain.devices.ProviderDeviceData
import org.agrfesta.sh.api.core.domain.failures.DeviceCreationFailure
import org.agrfesta.sh.api.core.domain.failures.DeviceFetchFailure
import org.agrfesta.sh.api.core.domain.failures.DeviceNotFound
import org.agrfesta.sh.api.core.domain.failures.DeviceRepositoryError
import org.agrfesta.sh.api.core.domain.failures.DeviceUpdateFailure
import org.agrfesta.sh.api.core.domain.failures.GetDevicesFailure
import org.agrfesta.sh.api.persistence.jdbc.repositories.DevicesJdbcRepository
import org.agrfesta.sh.api.utils.LoggerDelegate
import org.springframework.dao.DataAccessException
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class DevicesJdbcAdapter(
    private val devicesRepo: DevicesJdbcRepository
) : DevicesRepository {

    private val logger by LoggerDelegate()

    override fun getDeviceById(deviceId: UUID): Either<DeviceFetchFailure, Device> = try {
        devicesRepo.findDeviceById(deviceId)?.toDevice()?.right()
            ?: DeviceNotFound(missingDeviceId = deviceId).left()
    } catch (e: DataAccessException) {
        logger.error("Unexpected persistence error fetching device '$deviceId'", e)
        DeviceRepositoryError.left()
    }

    override fun getAll(): Either<DeviceRepositoryError, Collection<Device>> = try {
        devicesRepo.getAll().map { it.toDevice() }.right()
    } catch (e: DataAccessException) {
        logger.error("Unexpected persistence error fetching all devices", e)
        DeviceRepositoryError.left()
    }

    override fun getDevices(
        provider: Provider?,
        status: DeviceStatus?,
        feature: DeviceFeature?
    ): Either<GetDevicesFailure, Collection<Device>> = try {
        devicesRepo.findDevices(provider, status, feature).map { it.toDevice() }.right()
    } catch (e: DataAccessException) {
        logger.error("Unexpected persistence error fetching devices", e)
        DeviceRepositoryError.left()
    }

    override fun create(
        id: UUID,
        device: ProviderDeviceData,
        initialStatus: DeviceStatus
    ): Either<DeviceCreationFailure, Unit> =
        try {
            devicesRepo.persist(id, device, initialStatus).right()
        } catch (e: DataAccessException) {
            logger.error("Unexpected persistence error creating device '$id'", e)
            DeviceRepositoryError.left()
        }

    override fun update(device: Device): Either<DeviceUpdateFailure, Unit> = try {
        devicesRepo.findDeviceById(device.uuid)?.let {
            devicesRepo.update(device).right()
        } ?: DeviceNotFound(missingDeviceId = device.uuid).left()
    } catch (e: DataAccessException) {
        logger.error("Unexpected persistence error updating device '${device.uuid}'", e)
        DeviceRepositoryError.left()
    }
}
