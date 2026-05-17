package org.agrfesta.sh.api.core.application.ports.inbounds

import arrow.core.Either
import org.agrfesta.sh.api.core.domain.failures.FetchSensorReadingsFailure

interface FetchSensorReadingsUseCase {

    /**
     * Fetches the current readings from all sensor devices and stores them in the cache.
     *
     * Each sensor is queried independently; a failure for one sensor does not stop
     * processing of the remaining ones.
     *
     * @return [Either.Right] with [Unit] when the operation completes (even if individual
     * sensors failed), or [Either.Left] with [FetchSensorReadingsFailure] if the operation
     * could not start (e.g. device list unavailable).
     */
    fun execute(): Either<FetchSensorReadingsFailure, Unit>
}
