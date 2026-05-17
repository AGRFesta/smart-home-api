package org.agrfesta.sh.api.core.application.usecases

import arrow.core.Either
import arrow.core.flatMap
import org.agrfesta.sh.api.core.application.ports.inbounds.GetHeatingScheduleUseCase
import org.agrfesta.sh.api.core.application.ports.outbounds.areas.AreasRepository
import org.agrfesta.sh.api.core.application.ports.outbounds.settings.TemperatureSettingsRepository
import org.agrfesta.sh.api.core.domain.areas.HeatingScheduleDto
import org.agrfesta.sh.api.core.domain.areas.IntervalDto
import org.agrfesta.sh.api.core.domain.failures.AreaFetchFailure
import org.agrfesta.sh.api.core.domain.failures.AreaNotFound
import org.agrfesta.sh.api.core.domain.failures.AreaRepositoryError
import org.agrfesta.sh.api.core.domain.failures.HeatingScheduleRepositoryError
import org.agrfesta.sh.api.core.domain.failures.TemperatureSettingRetrievalFailure
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class GetHeatingScheduleService(
    private val areasRepository: AreasRepository,
    private val temperatureSettingsRepository: TemperatureSettingsRepository
) : GetHeatingScheduleUseCase {

    override fun execute(areaId: UUID): Either<TemperatureSettingRetrievalFailure, HeatingScheduleDto?> =
        areasRepository.getAreaById(areaId)
            .mapLeft { it.toRetrievalFailure() }
            .flatMap { _ ->
                temperatureSettingsRepository.findAreaSetting(areaId).map { setting ->
                    setting?.let {
                        HeatingScheduleDto(
                            defaultTemperature = it.defaultTemperature,
                            intervals = it.temperatureSchedule.map { interval ->
                                IntervalDto(
                                    temperature = interval.temperature,
                                    startTime = interval.startTime,
                                    endTime = interval.endTime
                                )
                            }
                        )
                    }
                }
            }

    private fun AreaFetchFailure.toRetrievalFailure(): TemperatureSettingRetrievalFailure = when (this) {
        is AreaNotFound -> this
        AreaRepositoryError -> HeatingScheduleRepositoryError
    }
}
