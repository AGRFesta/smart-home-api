package org.agrfesta.sh.api.core.application.ports.inbounds

import arrow.core.Either
import org.agrfesta.sh.api.core.domain.failures.InspectDeviceFailure
import java.util.UUID

interface InspectDeviceUseCase {

    /**
     * Resolves the persisted device by [deviceId], routes to its provider's diagnostics
     * implementation, and returns the provider's realtime raw response body verbatim.
     *
     * This is the *provider's truth* sibling of the persisted aggregate read: it is a passthrough,
     * never persisted nor cached, and intentionally surfaces provider failures instead of masking
     * them.
     *
     * @param deviceId the unique identifier of the device to inspect.
     * @return [Either.Right] with the provider's raw JSON body,
     * or [Either.Left] with an [InspectDeviceFailure].
     */
    fun execute(deviceId: UUID): Either<InspectDeviceFailure, String>
}
