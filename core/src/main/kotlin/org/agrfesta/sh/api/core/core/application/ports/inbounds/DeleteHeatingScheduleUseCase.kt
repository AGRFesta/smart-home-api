package org.agrfesta.sh.api.core.application.ports.inbounds

import arrow.core.Either
import java.util.UUID
import org.agrfesta.sh.api.core.domain.failures.TemperatureSettingDeletionFailure

interface DeleteHeatingScheduleUseCase {

    fun execute(areaId: UUID): Either<TemperatureSettingDeletionFailure, Unit>

}
