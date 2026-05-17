package org.agrfesta.sh.api.core.application.ports.inbounds

import arrow.core.Either
import org.agrfesta.sh.api.core.domain.areas.HeatingScheduleDto
import org.agrfesta.sh.api.core.domain.areas.TemperatureInterval
import org.agrfesta.sh.api.core.domain.commons.Temperature
import org.agrfesta.sh.api.core.domain.failures.TemperatureSettingCreationFailure
import java.util.UUID

interface ReplaceHeatingScheduleUseCase {

    fun execute(
        areaId: UUID,
        defaultTemperature: Temperature,
        intervals: Collection<TemperatureInterval>
    ): Either<TemperatureSettingCreationFailure, HeatingScheduleDto>
}
