package org.agrfesta.sh.api.core.application.ports.inbounds

import arrow.core.Either
import org.agrfesta.sh.api.core.domain.devices.RefreshDevicesResult
import org.agrfesta.sh.api.core.domain.failures.RefreshDevicesFailure

interface RefreshDevicesUseCase {

    /**
     * Synchronises the persisted device list with the current provider snapshot.
     *
     * @return [Either.Right] with a [RefreshDevicesResult] describing new, updated, and detached
     *         devices, or [Either.Left] with a [RefreshDevicesFailure] if the operation could not
     *         be completed.
     */
    fun execute(): Either<RefreshDevicesFailure, RefreshDevicesResult>

}
