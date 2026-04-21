package org.agrfesta.sh.api.core.application.ports.outbounds

import arrow.core.Either
import java.util.UUID
import org.agrfesta.sh.api.core.domain.failures.ActuatorAssignmentFailure

/**
 * Outbound Port for managing actuator-to-area assignments.
 */
interface ActuatorsAssignmentsRepository {

    /**
     * Assigns the actuator identified by [actuatorId] to the area identified by [areaId].
     *
     * Assumes the area and device have already been validated by the caller.
     * Only handles assignment-specific persistence logic.
     *
     * @param areaId The unique identifier of the target area.
     * @param actuatorId The unique identifier of the actuator to assign.
     * @return [arrow.core.Either.Right] with [Unit] on success, or [arrow.core.Either.Left]
     * containing an [ActuatorAssignmentFailure]:
     * - [org.agrfesta.sh.api.core.domain.failures.SameAreaAssignment] if the actuator is already assigned to [areaId].
     * - [org.agrfesta.sh.api.core.domain.failures.PersistenceFailure] if a database error occurs.
     */
    fun assign(areaId: UUID, actuatorId: UUID): Either<ActuatorAssignmentFailure, Unit>

}