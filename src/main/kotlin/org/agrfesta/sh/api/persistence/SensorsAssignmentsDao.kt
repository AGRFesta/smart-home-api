package org.agrfesta.sh.api.persistence

import arrow.core.Either
import java.util.*
import org.agrfesta.sh.api.domain.failures.SensorAssignmentFailure

interface SensorsAssignmentsDao {
    fun assign(areaId: UUID, sensorId: UUID): Either<SensorAssignmentFailure, AssignmentSuccess>
}

object AssignmentSuccess
