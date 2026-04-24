package org.agrfesta.sh.api.core.application.usecases

import arrow.core.Either
import arrow.core.flatMap
import java.util.UUID
import org.agrfesta.sh.api.core.application.ports.inbounds.DeleteHeatingScheduleUseCase
import org.agrfesta.sh.api.core.application.ports.outbounds.AreasRepository
import org.agrfesta.sh.api.core.application.ports.outbounds.TemperatureSettingsRepository
import org.agrfesta.sh.api.core.domain.failures.TemperatureSettingDeletionFailure
import org.springframework.stereotype.Service

@Service
class DeleteHeatingScheduleService(
    private val areasRepository: AreasRepository,
    private val temperatureSettingsRepository: TemperatureSettingsRepository
) : DeleteHeatingScheduleUseCase {

    override fun execute(areaId: UUID): Either<TemperatureSettingDeletionFailure, Unit> {
        val result: Either<TemperatureSettingDeletionFailure, Unit> =
            areasRepository.getAreaById(areaId).flatMap { _ ->
                temperatureSettingsRepository.deleteAreaSetting(areaId)
            }
        return result
    }

}
