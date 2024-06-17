package org.agrfesta.sh.api.persistence

import org.agrfesta.sh.api.domain.Device
import org.agrfesta.sh.api.persistence.entities.DeviceEntity
import org.agrfesta.sh.api.persistence.repositories.DevicesRepository
import org.springframework.stereotype.Service
import java.time.Instant

interface DevicesDao {
    fun getAll(): Collection<Device>
    fun save(device: Device)
    fun update(device: Device)
}

@Service
class DevicesDaoImpl(
    private val devicesRepository: DevicesRepository
): DevicesDao {
    override fun getAll(): Collection<Device> = devicesRepository.findAll().map { it.toDevice() }
    override fun save(device: Device) {
        val entity = DeviceEntity(
            uuid = device.uuid,
            name = device.name,
            provider = device.provider,
            providerId = device.providerId,
            createdOn = Instant.now()
        )
        devicesRepository.save(entity)
    }
    override fun update(device: Device) {
//        val entity = devicesRepository.findById(device.uuid).get()
//
//        devicesRepository.save(entity)
    }
}
