package org.agrfesta.sh.api.core.domain.areas

import org.agrfesta.sh.api.core.domain.commons.Temperature
import java.time.LocalTime

data class HeatingScheduleDto(
    val defaultTemperature: Temperature,
    val intervals: List<IntervalDto>
)

data class IntervalDto(
    val temperature: Temperature,
    val startTime: LocalTime,
    val endTime: LocalTime
)
