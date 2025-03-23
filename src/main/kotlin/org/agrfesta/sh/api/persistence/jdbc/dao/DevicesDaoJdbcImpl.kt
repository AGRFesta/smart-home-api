package org.agrfesta.sh.api.persistence.jdbc.dao

import java.util.*
import org.agrfesta.sh.api.domain.devices.Device
import org.agrfesta.sh.api.domain.devices.DeviceDataValue
import org.agrfesta.sh.api.domain.devices.DeviceStatus
import org.agrfesta.sh.api.persistence.DevicesDao
import org.agrfesta.sh.api.persistence.jdbc.repositories.DevicesJdbcRepository
import org.springframework.stereotype.Service

@Service
class DevicesDaoJdbcImpl(
    private val devicesJdbcRepository: DevicesJdbcRepository
): DevicesDao {
    override fun getDeviceById(uuid: UUID): Device =
        devicesJdbcRepository.getDeviceById(uuid).asDevice()

    override fun getAll(): Collection<Device> = devicesJdbcRepository.getAll().map { entity -> entity.asDevice() }

    override fun create(device: DeviceDataValue, initialStatus: DeviceStatus): UUID =
        devicesJdbcRepository.persist(device, initialStatus)

    override fun update(device: Device) =
        devicesJdbcRepository.update(device)
}
