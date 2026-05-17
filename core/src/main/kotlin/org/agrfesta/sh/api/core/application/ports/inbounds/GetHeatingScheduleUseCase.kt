package org.agrfesta.sh.api.core.application.ports.inbounds

import arrow.core.Either
import org.agrfesta.sh.api.core.domain.areas.HeatingScheduleDto
import org.agrfesta.sh.api.core.domain.failures.TemperatureSettingRetrievalFailure
import java.util.UUID

interface GetHeatingScheduleUseCase {

    fun execute(areaId: UUID): Either<TemperatureSettingRetrievalFailure, HeatingScheduleDto?>
}
