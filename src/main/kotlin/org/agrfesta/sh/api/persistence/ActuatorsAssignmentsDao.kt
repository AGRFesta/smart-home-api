package org.agrfesta.sh.api.persistence

import arrow.core.Either
import java.util.UUID
import org.agrfesta.sh.api.domain.failures.ActuatorAssignmentFailure

/**
 * DAO for managing actuator-to-area assignments.
 */
interface ActuatorsAssignmentsDao {

    /**
     * Assigns the actuator identified by [actuatorId] to the area identified by [areaId].
     *
     * Assumes the area and device have already been validated by the caller.
     * Only handles assignment-specific persistence logic.
     *
     * @param areaId The unique identifier of the target area.
     * @param actuatorId The unique identifier of the actuator to assign.
     * @return [Either.Right] with [Unit] on success, or [Either.Left] containing an [ActuatorAssignmentFailure]:
     * - [SameAreaAssignment] if the actuator is already assigned to [areaId].
     * - [PersistenceFailure] if a database error occurs.
     */
    fun assign(areaId: UUID, actuatorId: UUID): Either<ActuatorAssignmentFailure, Unit>

}
