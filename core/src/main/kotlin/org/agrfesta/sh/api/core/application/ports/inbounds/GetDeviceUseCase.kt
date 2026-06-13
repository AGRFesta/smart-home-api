package org.agrfesta.sh.api.core.application.ports.inbounds

import arrow.core.Either
import org.agrfesta.sh.api.core.domain.devices.DeviceAggregate
import org.agrfesta.sh.api.core.domain.failures.GetDeviceFailure
import java.util.UUID

interface GetDeviceUseCase {

    /**
     * Retrieves the persisted [DeviceAggregate] for a single device: its base fields plus the
     * relationships our model holds (current area assignments).
     *
     * @param deviceId the unique identifier of the device to retrieve.
     * @return [Either.Right] with the [DeviceAggregate] if the device exists,
     *         or [Either.Left] with a [GetDeviceFailure] if the device is unknown or a database
     *         error occurs.
     */
    fun execute(deviceId: UUID): Either<GetDeviceFailure, DeviceAggregate>
}
