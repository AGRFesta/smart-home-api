package org.agrfesta.sh.api.controllers

import com.fasterxml.jackson.annotation.JsonFormat
import java.time.LocalTime
import org.agrfesta.sh.api.core.domain.areas.HeatingScheduleDto
import org.agrfesta.sh.api.core.domain.areas.TemperatureInterval.Companion.INTERVAL_TIME_FORMAT
import org.agrfesta.sh.api.core.domain.commons.Temperature

data class HeatingScheduleResponse(
    val defaultTemperature: Temperature,
    val intervals: List<IntervalResponse>
)

data class IntervalResponse(
    val temperature: Temperature,
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = INTERVAL_TIME_FORMAT)
    val startTime: LocalTime,
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = INTERVAL_TIME_FORMAT)
    val endTime: LocalTime
)

fun HeatingScheduleDto.toResponse() = HeatingScheduleResponse(
    defaultTemperature = defaultTemperature,
    intervals = intervals.map {
        IntervalResponse(temperature = it.temperature, startTime = it.startTime, endTime = it.endTime)
    }
)
