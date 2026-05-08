package org.agrfesta.sh.api.core.application.ports.inbounds

import arrow.core.Either
import java.util.UUID
import org.agrfesta.sh.api.core.domain.failures.SensorAssignmentFailure

interface AssignSensorToAreaUseCase {

    fun execute(areaId: UUID, deviceId: UUID): Either<SensorAssignmentFailure, Unit>

}
