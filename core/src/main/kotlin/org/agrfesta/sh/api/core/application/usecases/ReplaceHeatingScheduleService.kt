package org.agrfesta.sh.api.core.application.usecases

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import java.util.UUID
import org.agrfesta.sh.api.core.application.ports.inbounds.ReplaceHeatingScheduleUseCase
import org.agrfesta.sh.api.core.application.ports.outbounds.areas.AreasRepository
import org.agrfesta.sh.api.core.application.ports.outbounds.settings.TemperatureSettingsRepository
import org.agrfesta.sh.api.core.domain.areas.AreaTemperatureSetting
import org.agrfesta.sh.api.core.domain.areas.HeatingScheduleDto
import org.agrfesta.sh.api.core.domain.areas.IntervalDto
import org.agrfesta.sh.api.core.domain.areas.TemperatureInterval
import org.agrfesta.sh.api.core.domain.areas.hasOverlap
import org.agrfesta.sh.api.core.domain.commons.Temperature
import org.agrfesta.sh.api.core.domain.failures.AreaFetchFailure
import org.agrfesta.sh.api.core.domain.failures.AreaNotFound
import org.agrfesta.sh.api.core.domain.failures.AreaRepositoryError
import org.agrfesta.sh.api.core.domain.failures.HeatingScheduleRepositoryError
import org.agrfesta.sh.api.core.domain.failures.OverlappingIntervals
import org.agrfesta.sh.api.core.domain.failures.TemperatureSettingCreationFailure
import org.springframework.stereotype.Service

@Service
class ReplaceHeatingScheduleService(
    private val areasRepository: AreasRepository,
    private val temperatureSettingsRepository: TemperatureSettingsRepository
) : ReplaceHeatingScheduleUseCase {

    override fun execute(
        areaId: UUID,
        defaultTemperature: Temperature,
        intervals: Collection<TemperatureInterval>
    ): Either<TemperatureSettingCreationFailure, HeatingScheduleDto> {
        if (intervals.hasOverlap()) return OverlappingIntervals.left()

        return areasRepository.getAreaById(areaId)
            .mapLeft { it.toCreationFailure() }
            .flatMap { _ ->
                temperatureSettingsRepository.createSetting(
                    AreaTemperatureSetting(
                        areaId = areaId,
                        defaultTemperature = defaultTemperature,
                        temperatureSchedule = intervals.toSet()
                    )
                )
            }
            .map {
                HeatingScheduleDto(
                    defaultTemperature = defaultTemperature,
                    intervals = intervals.sortedBy { it.startTime }.map { interval ->
                        IntervalDto(
                            temperature = interval.temperature,
                            startTime = interval.startTime,
                            endTime = interval.endTime
                        )
                    }
                )
            }
    }

    private fun AreaFetchFailure.toCreationFailure(): TemperatureSettingCreationFailure = when (this) {
        is AreaNotFound -> this
        AreaRepositoryError -> HeatingScheduleRepositoryError
    }

}
