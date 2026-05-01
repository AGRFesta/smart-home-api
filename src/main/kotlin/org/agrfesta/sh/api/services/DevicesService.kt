package org.agrfesta.sh.api.services

import arrow.core.Either
import org.agrfesta.sh.api.core.domain.devices.Device
import org.agrfesta.sh.api.core.domain.devices.DeviceDriver
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.ProviderDevicesFactory
import org.agrfesta.sh.api.core.domain.failures.PersistenceFailure
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.DevicesRepository
import org.agrfesta.sh.api.utils.LoggerDelegate
import org.springframework.stereotype.Service

/**
 * Service responsible for device retrieval within the smart home system.
 *
 * @property devicesRepository persistence layer for device read operations.
 * @param providerDevicesFactories all registered [ProviderDevicesFactory] instances; indexed internally
 *        by provider identifier to allow O(1) look-up when assembling domain [Device] objects.
 */
@Service
class DevicesService(
    private val devicesRepository: DevicesRepository,
    providerDevicesFactories: Collection<ProviderDevicesFactory>
) {
    private val logger by LoggerDelegate()
    private val mappedDevicesFactories = providerDevicesFactories.associateBy { it.provider }

    /**
     * Retrieves all persisted devices as [Device] objects.
     *
     * @return [Either.Right] containing the full collection of [Device], or [Either.Left] with a
     *         [PersistenceFailure] if the query fails.
     */
    fun getAllDto(): Either<PersistenceFailure, Collection<Device>> = devicesRepository.getAll()

    /**
     * Retrieves all devices as fully assembled domain objects.
     *
     * Each [Device] is converted to a [Device] using the [ProviderDevicesFactory] registered for
     * the device's provider. Throws if no factory is registered for a given provider.
     *
     * @return [Either.Right] containing a collection of domain [Device] objects, or [Either.Left] with a
     *         [PersistenceFailure] if the underlying query fails.
     */
    fun getAllDevices(): Either<PersistenceFailure, Collection<DeviceDriver>> =
        devicesRepository.getAll().map { records ->
            records.mapNotNull { record ->
                val factory = mappedDevicesFactories[record.provider]
                if (factory == null) {
                    logger.error("No ProviderDevicesFactory registered for provider '${record.provider}', skipping device '${record.uuid}'")
                    null
                } else {
                    factory.createDevice(record)
                }
            }
        }

}
