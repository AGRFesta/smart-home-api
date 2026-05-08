package org.agrfesta.sh.api.core.application.ports.inbounds

import arrow.core.Either
import java.util.UUID
import org.agrfesta.sh.api.core.domain.failures.ActuatorAssignmentFailure

interface AssignActuatorToAreaUseCase {

    fun execute(areaId: UUID, deviceId: UUID): Either<ActuatorAssignmentFailure, Unit>

}
