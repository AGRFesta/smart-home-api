package org.agrfesta.sh.api.persistence.jdbc.dao

import arrow.core.Either
import org.agrfesta.sh.api.domain.devices.Device
import org.agrfesta.sh.api.persistence.DevicesDao
import org.agrfesta.sh.api.persistence.GetDeviceFailure
import org.agrfesta.sh.api.persistence.jdbc.repositories.DevicesJdbcRepository
import org.springframework.stereotype.Service
import java.util.*

@Service
class DevicesDaoJdbcImpl(
    private val devicesJdbcRepository: DevicesJdbcRepository
): DevicesDao {
    override fun getDeviceById(uuid: UUID): Either<GetDeviceFailure, Device> =
        devicesJdbcRepository.getDeviceById(uuid).map { it.asDevice() }

    override fun getAll(): Collection<Device> = devicesJdbcRepository.getAll().map { it.asDevice() }

    override fun create(device: Device) = devicesJdbcRepository.persist(device)

    override fun update(device: Device) = devicesJdbcRepository.update(device)
}
