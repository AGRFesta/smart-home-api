package org.agrfesta.sh.api.core.application.ports.inbounds.devices

import arrow.core.Either
import org.agrfesta.sh.api.core.domain.devices.AcState
import org.agrfesta.sh.api.core.domain.failures.GetAcStateFailure
import java.util.UUID

interface GetAcStateUseCase {

    /**
     * Resolves the persisted device by [deviceId], routes to its provider's air conditioner
     * driver and reads the device's current control state (power, mode, target temperature,
     * fan speed). The read is realtime from the provider — never persisted nor cached.
     *
     * @param deviceId the unique identifier of the device to read.
     * @return [Either.Right] with the driver's [AcState],
     * or [Either.Left] with a [GetAcStateFailure].
     */
    fun execute(deviceId: UUID): Either<GetAcStateFailure, AcState>
}
