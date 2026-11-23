package org.agrfesta.sh.api.core.application.usecases.devices

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import org.agrfesta.sh.api.core.application.ports.inbounds.devices.RefreshDevicesUseCase
import org.agrfesta.sh.api.core.application.ports.outbounds.RandomGenerator
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.DevicesProvider
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.DevicesRepository
import org.agrfesta.sh.api.core.domain.devices.Device
import org.agrfesta.sh.api.core.domain.devices.DeviceStatus
import org.agrfesta.sh.api.core.domain.devices.ProviderDeviceData
import org.agrfesta.sh.api.core.domain.devices.RefreshDevicesResult
import org.agrfesta.sh.api.core.domain.failures.RefreshDevicesError
import org.agrfesta.sh.api.core.domain.failures.RefreshDevicesFailure
import org.agrfesta.sh.api.utils.LoggerDelegate
import org.springframework.stereotype.Service
import kotlin.coroutines.cancellation.CancellationException

@Service
class RefreshDevicesService(
    private val devicesRepository: DevicesRepository,
    private val devicesProviders: Set<DevicesProvider>,
    private val randomGenerator: RandomGenerator
) : RefreshDevicesUseCase {
    private val logger by LoggerDelegate()

    override fun execute(): Either<RefreshDevicesFailure, RefreshDevicesResult> {
        val devices = devicesRepository.getAll()
            .mapLeft { RefreshDevicesError }
            .getOrElse { return it.left() }
        val providerDevices = devicesProviders.flatMap { it.getAllDevicesOrEmpty() }
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

    /**
     * Fetches the devices of a provider, or none when the provider fails, so that its devices are detached.
     * An exception from the provider is a failure too, so it cannot stop the synchronization of the other
     * providers. An interruption or a cancellation is not a provider failure and is rethrown.
     */
    @Suppress("TooGenericExceptionCaught")
    private fun DevicesProvider.getAllDevicesOrEmpty(): Collection<ProviderDeviceData> =
        try {
            getAllDevices().getOrElse {
                logger.warn("Devices provider failed, its devices will be detached: $it")
                emptyList()
            }
        } catch (e: InterruptedException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error("Devices provider threw instead of returning a failure, its devices will be detached", e)
            emptyList()
        }
}
