package org.agrfesta.sh.api.core.application.ports.inbounds

import arrow.core.Either
import org.agrfesta.sh.api.core.domain.failures.SensorAssignmentFailure
import java.util.UUID

interface AssignSensorToAreaUseCase {

    fun execute(areaId: UUID, deviceId: UUID): Either<SensorAssignmentFailure, Unit>
}
