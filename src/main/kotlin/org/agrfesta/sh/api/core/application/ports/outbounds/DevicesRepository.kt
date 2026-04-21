package org.agrfesta.sh.api.core.application.ports.outbounds

import arrow.core.Either
import java.util.UUID
import org.agrfesta.sh.api.core.domain.devices.DeviceDataValue
import org.agrfesta.sh.api.core.domain.devices.DeviceDto
import org.agrfesta.sh.api.core.domain.devices.DeviceStatus
import org.agrfesta.sh.api.core.domain.failures.DeviceCreationFailure
import org.agrfesta.sh.api.core.domain.failures.DeviceFetchFailure
import org.agrfesta.sh.api.core.domain.failures.DeviceUpdateFailure
import org.agrfesta.sh.api.core.domain.failures.PersistenceFailure

/**
 * Outbound Port for [DeviceDto] persistence operations.
 *
 * All operations return an [Either] type: [Either.Right] on success, [Either.Left] on failure.
 * Failures are typed and represent either domain-level errors (e.g. not found)
 * or infrastructure-level errors (e.g. database access failure).
 */
interface DevicesRepository {

    /**
     * Retrieves a [DeviceDto] by its unique identifier.
     *
     * @param deviceId the unique identifier of the device to retrieve.
     * @return [Either.Right] with the [DeviceDto] if found,
     * or [Either.Left] with [DeviceFetchFailure] if the device does not exist or a persistence error occurs.
     */
    fun getDeviceById(deviceId: UUID): Either<DeviceFetchFailure, DeviceDto>

    /**
     * Retrieves all persisted devices.
     *
     * @return [Either.Right] with a collection of all [DeviceDto] instances,
     * or [Either.Left] with [PersistenceFailure] if a database error occurs.
     */
    fun getAll(): Either<PersistenceFailure, Collection<DeviceDto>>

    /**
     * Persists a new device with the given [id].
     *
     * @param id the [UUID] to assign to the new device.
     * @param device the device data to persist.
     * @param initialStatus the initial [DeviceStatus] to assign; defaults to [DeviceStatus.PAIRED].
     * @return [Either.Right] with [Unit] on success,
     * or [Either.Left] with [.DeviceCreationFailure] if the device could not be created.
     */
    fun create(
        id: UUID,
        device: DeviceDataValue,
        initialStatus: DeviceStatus = DeviceStatus.PAIRED
    ): Either<DeviceCreationFailure, Unit>

    /**
     * Updates an existing [DeviceDto] with new data.
     *
     * @param device the device data to persist.
     * @return [Either.Right] with [Unit] on success,
     * or [Either.Left] with [DeviceUpdateFailure] if the device does not exist or a persistence error occurs.
     */
    fun update(device: DeviceDto): Either<DeviceUpdateFailure, Unit>

}
