package org.agrfesta.sh.api.core.application.ports.inbounds

import arrow.core.Either
import org.agrfesta.sh.api.core.domain.failures.SensorUnassignFailure
import java.util.UUID

interface UnassignSensorFromAreaUseCase {

    fun execute(areaId: UUID, deviceId: UUID): Either<SensorUnassignFailure, Unit>
}
