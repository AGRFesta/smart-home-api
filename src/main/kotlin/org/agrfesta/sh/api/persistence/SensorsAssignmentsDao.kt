package org.agrfesta.sh.api.persistence

import arrow.core.Either
import java.util.UUID
import org.agrfesta.sh.api.domain.failures.SensorAssignmentFailure

/**
 * DAO for managing sensor-to-area assignments.
 */
interface SensorsAssignmentsDao {

    /**
     * Assigns the sensor identified by [sensorId] to the area identified by [areaId].
     *
     * Assumes the area and device have already been validated by the caller.
     * Only handles assignment-specific persistence logic.
     *
     * @param areaId The unique identifier of the target area.
     * @param sensorId The unique identifier of the sensor to assign.
     * @return [Either.Right] with [Unit] on success, or [Either.Left] containing a [SensorAssignmentFailure]:
     * - [SameAreaAssignment] if the sensor is already actively assigned to [areaId].
     * - [SensorAlreadyAssigned] if the sensor is already actively assigned to a different area.
     * - [PersistenceFailure] if a database error occurs.
     */
    fun assign(areaId: UUID, sensorId: UUID): Either<SensorAssignmentFailure, Unit>

}
