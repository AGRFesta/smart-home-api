package org.agrfesta.sh.api.core.application.ports.outbounds.devices

import arrow.core.Either
import org.agrfesta.sh.api.core.domain.devices.Device
import org.agrfesta.sh.api.core.domain.devices.DeviceFeature
import org.agrfesta.sh.api.core.domain.devices.DeviceStatus
import org.agrfesta.sh.api.core.domain.devices.Provider
import org.agrfesta.sh.api.core.domain.devices.ProviderDeviceData
import org.agrfesta.sh.api.core.domain.failures.DeviceCreationFailure
import org.agrfesta.sh.api.core.domain.failures.DeviceFetchFailure
import org.agrfesta.sh.api.core.domain.failures.DeviceRepositoryError
import org.agrfesta.sh.api.core.domain.failures.DeviceUpdateFailure
import org.agrfesta.sh.api.core.domain.failures.GetDevicesFailure
import java.util.UUID

/**
 * Outbound Port for [Device] persistence operations.
 *
 * All operations return an [Either] type: [Either.Right] on success, [Either.Left] on failure.
 * Failures are typed and represent either domain-level errors (e.g. not found)
 * or infrastructure-level errors (e.g. database access failure).
 */
interface DevicesRepository {

    /**
     * Retrieves a [Device] by its unique identifier.
     *
     * @param deviceId the unique identifier of the device to retrieve.
     * @return [Either.Right] with the [Device] if found,
     * or [Either.Left] with [DeviceFetchFailure] if the device does not exist or a persistence error occurs.
     */
    fun getDeviceById(deviceId: UUID): Either<DeviceFetchFailure, Device>

    /**
     * Retrieves all persisted devices.
     *
     * @return [Either.Right] with a collection of all [Device] instances,
     * or [Either.Left] with [DeviceRepositoryError] if a database error occurs.
     */
    fun getAll(): Either<DeviceRepositoryError, Collection<Device>>

    /**
     * Retrieves the persisted devices, optionally filtered.
     *
     * All filters are combinable with AND semantics; a `null` filter is not applied.
     *
     * @param provider when set, restricts the result to devices of this [Provider].
     * @param status when set, restricts the result to devices with this [DeviceStatus].
     * @param feature when set, restricts the result to devices exposing this [DeviceFeature].
     * @return [Either.Right] with the matching [Device] collection (possibly empty),
     * or [Either.Left] with [GetDevicesFailure] if a database error occurs.
     */
    fun getDevices(
        provider: Provider? = null,
        status: DeviceStatus? = null,
        feature: DeviceFeature? = null
    ): Either<GetDevicesFailure, Collection<Device>>

    /**
     * Persists a new device with the given [id].
     *
     * @param id the [UUID] to assign to the new device.
     * @param device the provider data to persist.
     * @param initialStatus the initial [DeviceStatus] to assign; defaults to [DeviceStatus.PAIRED].
     * @return [Either.Right] with [Unit] on success,
     * or [Either.Left] with [.DeviceCreationFailure] if the device could not be created.
     */
    fun create(
        id: UUID,
        device: ProviderDeviceData,
        initialStatus: DeviceStatus = DeviceStatus.PAIRED
    ): Either<DeviceCreationFailure, Unit>

    /**
     * Updates an existing [Device] with new data.
     *
     * @param device the device record to persist.
     * @return [Either.Right] with [Unit] on success,
     * or [Either.Left] with [DeviceUpdateFailure] if the device does not exist or the update fails.
     */
    fun update(device: Device): Either<DeviceUpdateFailure, Unit>
}
