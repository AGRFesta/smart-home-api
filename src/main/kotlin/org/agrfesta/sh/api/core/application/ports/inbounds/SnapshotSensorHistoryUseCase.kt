package org.agrfesta.sh.api.core.application.ports.inbounds

import arrow.core.Either
import org.agrfesta.sh.api.core.domain.failures.SnapshotSensorHistoryFailure

interface SnapshotSensorHistoryUseCase {

    /**
     * Reads the current cached readings for all sensor devices and persists them as history records.
     *
     * Each sensor is processed independently; a failure for one sensor does not stop
     * processing of the remaining ones.
     *
     * @return [Either.Right] with [Unit] when the operation completes (even if individual
     * sensors failed), or [Either.Left] with [SnapshotSensorHistoryFailure] if the operation
     * could not start (e.g. device list unavailable).
     */
    fun execute(): Either<SnapshotSensorHistoryFailure, Unit>

}
