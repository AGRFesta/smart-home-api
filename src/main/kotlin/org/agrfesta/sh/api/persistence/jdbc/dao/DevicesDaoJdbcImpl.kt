package org.agrfesta.sh.api.persistence.jdbc.dao

import arrow.core.Either
import org.agrfesta.sh.api.domain.devices.Device
import org.agrfesta.sh.api.domain.devices.DeviceDataValue
import org.agrfesta.sh.api.domain.devices.DeviceStatus
import org.agrfesta.sh.api.persistence.DevicesDao
import org.agrfesta.sh.api.persistence.GetDeviceFailure
import org.agrfesta.sh.api.persistence.PersistenceFailure
import org.agrfesta.sh.api.persistence.PersistenceSuccess
import org.agrfesta.sh.api.persistence.jdbc.repositories.DevicesJdbcRepository
import org.springframework.stereotype.Service
import java.util.*

@Service
class DevicesDaoJdbcImpl(
    private val devicesJdbcRepository: DevicesJdbcRepository
): DevicesDao {
    override fun getDeviceById(uuid: UUID): Either<GetDeviceFailure, Device> =
        devicesJdbcRepository.getDeviceById(uuid).map { it.asDevice() }

    override fun getAll(): Either<PersistenceFailure, Collection<Device>> =
        devicesJdbcRepository.getAll().map {
            it.map { entity -> entity.asDevice() }
        }

    override fun create(device: DeviceDataValue, initialStatus: DeviceStatus): Either<PersistenceFailure, UUID>  =
        devicesJdbcRepository.persist(device, initialStatus)

    override fun update(device: Device): Either<PersistenceFailure, PersistenceSuccess> =
        devicesJdbcRepository.update(device)
}
