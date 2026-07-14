package org.agrfesta.sh.api.core.application.ports.outbounds.devices

import arrow.core.Either
import org.agrfesta.sh.api.core.application.readmodels.devices.DeviceView
import org.agrfesta.sh.api.core.domain.failures.GetDeviceFailure
import java.util.UUID

/**
 * Outbound Port for retrieving the persisted [DeviceView] of a single device.
 */
interface DeviceViewRepository {

    /**
     * Retrieves the [DeviceView] for the device identified by [deviceId], including its current
     * area assignments.
     *
     * @param deviceId the unique identifier of the device to retrieve.
     * @return [Either.Right] with the [DeviceView] if the device exists,
     * or [Either.Left] with a [GetDeviceFailure]:
     * - [org.agrfesta.sh.api.core.domain.failures.DeviceNotFound] if no device matches [deviceId].
     * - [org.agrfesta.sh.api.core.domain.failures.DeviceRepositoryError] if a database error occurs.
     */
    fun findById(deviceId: UUID): Either<GetDeviceFailure, DeviceView>
}
