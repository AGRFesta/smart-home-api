package org.agrfesta.sh.api.core.application.usecases

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import org.agrfesta.sh.api.core.application.ports.inbounds.RefreshDevicesUseCase
import org.agrfesta.sh.api.core.application.ports.outbounds.RandomGenerator
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.DevicesProvider
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.DevicesRepository
import org.agrfesta.sh.api.core.domain.devices.Device
import org.agrfesta.sh.api.core.domain.devices.DeviceStatus
import org.agrfesta.sh.api.core.domain.devices.RefreshDevicesResult
import org.agrfesta.sh.api.core.domain.failures.RefreshDevicesError
import org.agrfesta.sh.api.core.domain.failures.RefreshDevicesFailure
import org.springframework.stereotype.Service

@Service
class RefreshDevicesService(
    private val devicesRepository: DevicesRepository,
    private val devicesProviders: Set<DevicesProvider>,
    private val randomGenerator: RandomGenerator
) : RefreshDevicesUseCase {

    override fun execute(): Either<RefreshDevicesFailure, RefreshDevicesResult> {
        val devices = devicesRepository.getAll()
            .mapLeft { RefreshDevicesError }
            .getOrElse { return it.left() }
        val providerDevices = devicesProviders.flatMap { provider ->
            provider.getAllDevices().getOrElse { emptyList() }
        }
        val newDevices = providerDevices
            .filter { pd -> devices.none { it.deviceProviderId == pd.deviceProviderId && it.provider == pd.provider } }
            .mapNotNull { pd ->
                val uuid = randomGenerator.uuid()
                devicesRepository.create(uuid, pd, DeviceStatus.PAIRED).fold(
                    { null },
                    { Device(uuid = uuid, providerData = pd) }
                )
            }
        val updatedDevices = providerDevices.mapNotNull { pd ->
            devices.find { it.deviceProviderId == pd.deviceProviderId && it.provider == pd.provider }
                ?.copy(name = pd.name, status = DeviceStatus.PAIRED, model = pd.model)
        }
        updatedDevices.forEach { devicesRepository.update(it) }
        val detachedDevices = devices
            .filter { d ->
                providerDevices.none { it.deviceProviderId == d.deviceProviderId && it.provider == d.provider }
            }
            .map { it.copy(status = DeviceStatus.DETACHED) }
        detachedDevices.forEach { devicesRepository.update(it) }
        return RefreshDevicesResult(
            newDevices = newDevices,
            updatedDevices = updatedDevices,
            detachedDevices = detachedDevices
        ).right()
    }
}
