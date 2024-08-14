package org.agrfesta.sh.api.persistence

import org.agrfesta.sh.api.domain.devices.Device
import org.agrfesta.sh.api.persistence.entities.DeviceEntity
import org.agrfesta.sh.api.persistence.repositories.DevicesRepository
import org.agrfesta.sh.api.utils.TimeService
import org.springframework.stereotype.Service

interface DevicesDao {
    fun getAll(): Collection<Device>
    fun save(device: Device): Device
    fun update(device: Device)
}

@Service
class DevicesDaoImpl(
    private val devicesRepository: DevicesRepository,
    private val timeService: TimeService
): DevicesDao {
    override fun getAll(): Collection<Device> = devicesRepository.findAll().map { it.toDevice() }
    override fun save(device: Device): Device {
        val entity = DeviceEntity(
            name = device.name,
            provider = device.provider,
            providerId = device.providerId,
            status = device.status,
            createdOn = timeService.now()
        )
        return devicesRepository.save(entity).toDevice()
    }
    override fun update(device: Device) {
        devicesRepository.findByProviderAndProviderId(device.provider, device.providerId)?.let {
            it.name = device.name
            it.status = device.status
            it.updatedOn = timeService.now()
            devicesRepository.save(it)
        }
    }
}
