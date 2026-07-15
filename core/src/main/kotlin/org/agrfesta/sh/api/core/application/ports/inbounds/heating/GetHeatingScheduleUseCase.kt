package org.agrfesta.sh.api.core.application.ports.inbounds.heating

import arrow.core.Either
import org.agrfesta.sh.api.core.application.readmodels.areas.HeatingScheduleView
import org.agrfesta.sh.api.core.domain.failures.TemperatureSettingRetrievalFailure
import java.util.UUID

interface GetHeatingScheduleUseCase {

    fun execute(areaId: UUID): Either<TemperatureSettingRetrievalFailure, HeatingScheduleView?>
}
