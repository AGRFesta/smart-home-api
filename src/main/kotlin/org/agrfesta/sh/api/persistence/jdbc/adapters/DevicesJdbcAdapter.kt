package org.agrfesta.sh.api.persistence.jdbc.adapters

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import java.util.UUID
import org.agrfesta.sh.api.core.domain.devices.ProviderDeviceData
import org.agrfesta.sh.api.core.domain.devices.Device
import org.agrfesta.sh.api.core.domain.devices.DeviceStatus
import org.agrfesta.sh.api.core.domain.failures.DeviceCreationFailure
import org.agrfesta.sh.api.core.domain.failures.DeviceFetchFailure
import org.agrfesta.sh.api.core.domain.failures.DeviceNotFound
import org.agrfesta.sh.api.core.domain.failures.DeviceUpdateFailure
import org.agrfesta.sh.api.core.domain.failures.PersistenceFailure
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.DevicesRepository
import org.agrfesta.sh.api.persistence.jdbc.repositories.DevicesJdbcRepository
import org.springframework.dao.DataAccessException
import org.springframework.stereotype.Service

@Service
class DevicesJdbcAdapter(
    private val devicesRepo: DevicesJdbcRepository
): DevicesRepository {

    override fun getDeviceById(deviceId: UUID): Either<DeviceFetchFailure, Device> = try {
        devicesRepo.findDeviceById(deviceId)?.toDevice()?.right()
            ?: DeviceNotFound(missingDeviceId = deviceId).left()
    } catch (e: DataAccessException) {
        PersistenceFailure(e).left()
    }

    override fun getAll(): Either<PersistenceFailure, Collection<Device>> = try {
        devicesRepo.getAll().map { it.toDevice() }.right()
    } catch (e: DataAccessException) {
        PersistenceFailure(e).left()
    }

    override fun create(id: UUID, device: ProviderDeviceData, initialStatus: DeviceStatus): Either<DeviceCreationFailure, Unit> =
        try {
            devicesRepo.persist(id, device, initialStatus).right()
        } catch (e: DataAccessException) {
            PersistenceFailure(e).left()
        }

    override fun update(device: Device): Either<DeviceUpdateFailure, Unit> = try {
        devicesRepo.findDeviceById(device.uuid)?.let {
            devicesRepo.update(device).right()
        } ?: DeviceNotFound(missingDeviceId = device.uuid).left()
    } catch (e: DataAccessException) {
        PersistenceFailure(e).left()
    }

}
