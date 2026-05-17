package org.agrfesta.sh.api.core.application.ports.outbounds.areas

import arrow.core.Either
import org.agrfesta.sh.api.core.domain.failures.SensorAssignmentFailure
import org.agrfesta.sh.api.core.domain.failures.SensorUnassignFailure
import java.util.UUID

/**
 * Outbound Port for managing sensor-to-area assignments.
 */
interface SensorsAssignmentsRepository {

    /**
     * Assigns the sensor identified by [sensorId] to the area identified by [areaId].
     *
     * Assumes the area and device have already been validated by the caller.
     * Only handles assignment-specific persistence logic.
     *
     * @param areaId The unique identifier of the target area.
     * @param sensorId The unique identifier of the sensor to assign.
     * @return [Either.Right] with [Unit] on success, or [Either.Left] containing
     * a [SensorAssignmentFailure]:
     * - [org.agrfesta.sh.api.core.domain.failures.SameAreaAssignment] if the sensor is already actively assigned
     *  to [areaId].
     * - [org.agrfesta.sh.api.core.domain.failures.SensorAlreadyAssigned] if the sensor is already actively assigned to a
     * different area.
     * - [org.agrfesta.sh.api.core.domain.failures.AssignmentRepositoryError] if a database error occurs.
     */
    fun assign(areaId: UUID, sensorId: UUID): Either<SensorAssignmentFailure, Unit>

    fun unassign(areaId: UUID, sensorId: UUID): Either<SensorUnassignFailure, Unit>
}
