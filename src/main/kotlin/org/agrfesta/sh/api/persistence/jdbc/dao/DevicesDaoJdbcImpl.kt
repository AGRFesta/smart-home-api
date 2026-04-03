package org.agrfesta.sh.api.persistence.jdbc.dao

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import java.util.UUID
import org.agrfesta.sh.api.domain.devices.DeviceDataValue
import org.agrfesta.sh.api.domain.devices.DeviceDto
import org.agrfesta.sh.api.domain.devices.DeviceStatus
import org.agrfesta.sh.api.domain.failures.DeviceCreationFailure
import org.agrfesta.sh.api.domain.failures.DeviceFetchFailure
import org.agrfesta.sh.api.domain.failures.DeviceNotFound
import org.agrfesta.sh.api.domain.failures.DeviceUpdateFailure
import org.agrfesta.sh.api.domain.failures.PersistenceFailure
import org.agrfesta.sh.api.persistence.DevicesDao
import org.agrfesta.sh.api.persistence.jdbc.repositories.DevicesJdbcRepository
import org.springframework.dao.DataAccessException
import org.springframework.stereotype.Service

@Service
class DevicesDaoJdbcImpl(
    private val devicesRepo: DevicesJdbcRepository
): DevicesDao {

    override fun getDeviceById(deviceId: UUID): Either<DeviceFetchFailure, DeviceDto> = try {
        devicesRepo.findDeviceById(deviceId)?.asDevice()?.right()
            ?: DeviceNotFound(missingDeviceId = deviceId).left()
    } catch (e: DataAccessException) {
        PersistenceFailure(e).left()
    }

    override fun getAll(): Either<PersistenceFailure, Collection<DeviceDto>> = try {
        devicesRepo.getAll().map { it.asDevice() }.right()
    } catch (e: DataAccessException) {
        PersistenceFailure(e).left()
    }

    override fun create(device: DeviceDataValue, initialStatus: DeviceStatus): Either<DeviceCreationFailure, UUID> =
        try {
            devicesRepo.persist(device, initialStatus).right()
        } catch (e: DataAccessException) {
            PersistenceFailure(e).left()
        }

    override fun update(device: DeviceDto): Either<DeviceUpdateFailure, Unit> = try {
        devicesRepo.findDeviceById(device.uuid)?.let {
            devicesRepo.update(device).right()
        } ?: DeviceNotFound(missingDeviceId = device.uuid).left()
    } catch (e: DataAccessException) {
        PersistenceFailure(e).left()
    }

}
