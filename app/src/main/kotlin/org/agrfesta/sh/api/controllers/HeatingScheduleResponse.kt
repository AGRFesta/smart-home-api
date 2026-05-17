package org.agrfesta.sh.api.controllers

import org.agrfesta.sh.api.core.domain.areas.HeatingScheduleDto
import org.agrfesta.sh.api.core.domain.areas.TemperatureInterval.Companion.INTERVAL_TIME_FORMAT
import java.math.BigDecimal
import java.time.format.DateTimeFormatter

private val timeFormatter = DateTimeFormatter.ofPattern(INTERVAL_TIME_FORMAT)

data class HeatingScheduleResponse(
    val defaultTemperature: BigDecimal,
    val intervals: List<IntervalResponse>
)

data class IntervalResponse(
    val temperature: BigDecimal,
    val startTime: String,
    val endTime: String
)

fun HeatingScheduleDto.toResponse() = HeatingScheduleResponse(
    defaultTemperature = defaultTemperature.value,
    intervals = intervals.map {
        IntervalResponse(
            temperature = it.temperature.value,
            startTime = it.startTime.format(timeFormatter),
            endTime = it.endTime.format(timeFormatter)
        )
    }
)
