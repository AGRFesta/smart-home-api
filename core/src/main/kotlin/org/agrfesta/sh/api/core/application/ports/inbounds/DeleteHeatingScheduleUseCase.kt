package org.agrfesta.sh.api.core.application.ports.inbounds

import arrow.core.Either
import org.agrfesta.sh.api.core.domain.failures.TemperatureSettingDeletionFailure
import java.util.UUID

interface DeleteHeatingScheduleUseCase {

    fun execute(areaId: UUID): Either<TemperatureSettingDeletionFailure, Unit>
}
