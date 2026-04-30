package org.agrfesta.sh.api.services

import arrow.core.Either
import java.util.*
import org.agrfesta.sh.api.core.domain.devices.Device
import org.agrfesta.sh.api.core.domain.devices.ProviderDeviceData
import org.agrfesta.sh.api.core.domain.devices.DeviceStatus
import org.agrfesta.sh.api.core.domain.devices.DeviceDriver
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.ProviderDevicesFactory
import org.agrfesta.sh.api.core.domain.failures.DeviceCreationFailure
import org.agrfesta.sh.api.core.domain.failures.DeviceUpdateFailure
import org.agrfesta.sh.api.core.domain.failures.PersistenceFailure
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.DevicesRepository
import org.agrfesta.sh.api.utils.LoggerDelegate
import org.agrfesta.sh.api.utils.RandomGenerator
import org.springframework.stereotype.Service

/**
 * Service responsible for managing devices within the smart home system.
 *
 * Handles device creation, update, refresh (synchronisation with provider-supplied data),
 * and retrieval in both record and domain-object forms.
 *
 * @property devicesRepository persistence layer for device CRUD operations.
 * @param providerDevicesFactories all registered [ProviderDevicesFactory] instances; indexed internally
 *        by provider identifier to allow O(1) look-up when assembling domain [Device] objects.
 */
@Service
class DevicesService(
    private val devicesRepository: DevicesRepository,
    private val randomGenerator: RandomGenerator,
    providerDevicesFactories: Collection<ProviderDevicesFactory>
) {
    private val logger by LoggerDelegate()
    private val mappedDevicesFactories = providerDevicesFactories.associateBy { it.provider }

    /**
     * Persists a new device derived from [device] provider data.
     *
     * @param device the provider-supplied data describing the device to create.
     * @param initialStatus the initial [DeviceStatus] assigned to the new device; defaults to [DeviceStatus.PAIRED].
     * @return [Either.Right] containing the UUID of the newly created device, or [Either.Left] with a
     *         [DeviceCreationFailure] if the device could not be persisted.
     */
    fun createDevice(
        device: ProviderDeviceData,
        initialStatus: DeviceStatus = DeviceStatus.PAIRED
    ): Either<DeviceCreationFailure, UUID> {
        val uuid = randomGenerator.uuid()
        return devicesRepository.create(uuid, device, initialStatus).map { uuid }
    }

    /**
     * Updates the persisted representation of an existing device.
     *
     * @param device the [Device] carrying the updated field values.
     * @return [Either.Right] with [Unit] on success, or [Either.Left] with a [DeviceUpdateFailure]
     *         if the device does not exist or the update fails.
     */
    fun update(device: Device): Either<DeviceUpdateFailure, Unit> = devicesRepository.update(device)

    /**
     * Computes the difference between the current provider snapshot and the persisted device list.
     *
     * Produces three disjoint sets:
     * - **newDevices** — devices present in [providersDevices] but not yet in [devices].
     * - **updatedDevices** — devices present in both, with their name and status refreshed to [DeviceStatus.PAIRED].
     * - **detachedDevices** — devices present in [devices] but absent from [providersDevices], marked as
     *   [DeviceStatus.DETACHED].
     *
     * @param providersDevices the up-to-date collection of devices reported by the external provider.
     * @param devices the collection of devices currently stored in the system.
     * @return a [DevicesRefreshResult] describing the three change sets.
     */
    fun refresh(providersDevices: Collection<ProviderDeviceData>, devices: Collection<Device>): DevicesRefreshResult {
        return DevicesRefreshResult(
            newDevices = providersDevices
                .filter { devices.find(it.deviceProviderId) == null },
            updatedDevices = providersDevices
                .mapNotNull { devices.find(it.deviceProviderId)?.copy(name = it.name, status = DeviceStatus.PAIRED) },
            detachedDevices = devices
                .filter { providersDevices.find(it.deviceProviderId) == null }
                .map { it.copy(status = DeviceStatus.DETACHED) }
        )
    }

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

    private fun Collection<Device>.find(providerId: String): Device? =
        firstOrNull { it.deviceProviderId == providerId }
    private fun Collection<ProviderDeviceData>.find(providerId: String): ProviderDeviceData? =
        firstOrNull { it.deviceProviderId == providerId }

}

/**
 * Holds the outcome of a device refresh operation, categorising each device into one of three sets.
 *
 * @property newDevices devices reported by the provider that are not yet persisted in the system.
 * @property updatedDevices devices present in both the provider snapshot and the system, with refreshed data.
 * @property detachedDevices devices persisted in the system but no longer reported by the provider.
 */
data class DevicesRefreshResult(
    val newDevices: Collection<ProviderDeviceData> = emptyList(),
    val updatedDevices: Collection<Device> = emptyList(),
    val detachedDevices: Collection<Device> = emptyList()
)
