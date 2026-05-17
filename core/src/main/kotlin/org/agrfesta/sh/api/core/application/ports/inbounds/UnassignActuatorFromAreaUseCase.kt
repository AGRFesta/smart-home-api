package org.agrfesta.sh.api.core.application.ports.inbounds

import arrow.core.Either
import org.agrfesta.sh.api.core.domain.failures.ActuatorUnassignFailure
import java.util.UUID

interface UnassignActuatorFromAreaUseCase {

    fun execute(areaId: UUID, deviceId: UUID): Either<ActuatorUnassignFailure, Unit>
}
