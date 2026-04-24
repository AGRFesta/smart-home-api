package org.agrfesta.sh.api.core.application.usecases

import arrow.core.Either
import arrow.core.flatMap
import java.util.UUID
import org.agrfesta.sh.api.core.application.ports.inbounds.GetHeatingScheduleUseCase
import org.agrfesta.sh.api.core.application.ports.outbounds.AreasRepository
import org.agrfesta.sh.api.core.application.ports.outbounds.TemperatureSettingsRepository
import org.agrfesta.sh.api.core.domain.areas.HeatingScheduleDto
import org.agrfesta.sh.api.core.domain.areas.IntervalDto
import org.agrfesta.sh.api.core.domain.failures.TemperatureSettingRetrievalFailure
import org.springframework.stereotype.Service

@Service
class GetHeatingScheduleService(
    private val areasRepository: AreasRepository,
    private val temperatureSettingsRepository: TemperatureSettingsRepository
) : GetHeatingScheduleUseCase {

    override fun execute(areaId: UUID): Either<TemperatureSettingRetrievalFailure, HeatingScheduleDto?> {
        val result: Either<TemperatureSettingRetrievalFailure, HeatingScheduleDto?> =
            areasRepository.getAreaById(areaId).flatMap { _ ->
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
        return result
    }

}
