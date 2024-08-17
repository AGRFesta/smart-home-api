package org.agrfesta.sh.api.persistence

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import org.agrfesta.sh.api.domain.devices.Device
import org.agrfesta.sh.api.persistence.entities.DeviceEntity
import org.agrfesta.sh.api.persistence.repositories.DevicesRepository
import org.agrfesta.sh.api.utils.RandomGenerator
import org.agrfesta.sh.api.utils.TimeService
import org.springframework.stereotype.Service
import java.util.*
import kotlin.jvm.optionals.getOrNull

interface DevicesDao {
    fun getDeviceById(uuid: UUID): Either<GetDeviceFailure, Device>
    fun getAll(): Collection<Device>

    /**
     * Persists a new [Device].
     *
     * @param device data.
     * @return the [UUID] of the persisted [device].
     */
    fun create(device: Device): UUID

    fun update(device: Device)
}

sealed interface GetDeviceFailure: AssociationFailure

data object DeviceNotFound: GetDeviceFailure

@Service
class DevicesDaoImpl(
    private val devicesRepository: DevicesRepository,
    private val timeService: TimeService,
    private val randomGenerator: RandomGenerator
): DevicesDao {

    override fun getDeviceById(uuid: UUID): Either<GetDeviceFailure, Device> = try {
        devicesRepository.findById(uuid)
            .map { it.toDevice() }
            .getOrNull()
            ?.right()
            ?: DeviceNotFound.left()
    } catch (e: Exception) {
        PersistenceFailure(e).left()
    }

    override fun getAll(): Collection<Device> = devicesRepository.findAll().map { it.toDevice() }

    override fun create(device: Device): UUID {
        val entity = DeviceEntity(
            uuid = randomGenerator.uuid(),
            name = device.name,
            provider = device.provider,
            providerId = device.providerId,
            status = device.status,
            createdOn = timeService.now()
        )
        return devicesRepository.save(entity).uuid
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
