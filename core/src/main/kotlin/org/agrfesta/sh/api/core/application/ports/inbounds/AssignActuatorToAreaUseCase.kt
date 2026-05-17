package org.agrfesta.sh.api.core.application.ports.inbounds

import arrow.core.Either
import org.agrfesta.sh.api.core.domain.failures.ActuatorAssignmentFailure
import java.util.UUID

interface AssignActuatorToAreaUseCase {

    fun execute(areaId: UUID, deviceId: UUID): Either<ActuatorAssignmentFailure, Unit>
}
