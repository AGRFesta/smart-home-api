package org.agrfesta.sh.api.core.application.readmodels.areas

import org.agrfesta.sh.api.core.domain.commons.Temperature
import java.time.LocalTime

data class HeatingScheduleView(
    val defaultTemperature: Temperature,
    val intervals: List<IntervalView>
)

data class IntervalView(
    val temperature: Temperature,
    val startTime: LocalTime,
    val endTime: LocalTime
)
