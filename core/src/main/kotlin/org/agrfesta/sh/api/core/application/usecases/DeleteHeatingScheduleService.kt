package org.agrfesta.sh.api.core.application.usecases

import arrow.core.Either
import arrow.core.left
import java.util.UUID
import org.agrfesta.sh.api.core.application.ports.inbounds.DeleteHeatingScheduleUseCase
import org.agrfesta.sh.api.core.application.ports.outbounds.areas.AreasRepository
import org.agrfesta.sh.api.core.application.ports.outbounds.settings.TemperatureSettingsRepository
import org.agrfesta.sh.api.core.domain.failures.TemperatureSettingDeletionFailure
import org.springframework.stereotype.Service

@Service
class DeleteHeatingScheduleService(
    private val areasRepository: AreasRepository,
    private val temperatureSettingsRepository: TemperatureSettingsRepository
) : DeleteHeatingScheduleUseCase {

    override fun execute(areaId: UUID): Either<TemperatureSettingDeletionFailure, Unit> =
        areasRepository.getAreaById(areaId).fold(
            { it.left() },
            { _ -> temperatureSettingsRepository.deleteAreaSetting(areaId) }
        )

}
