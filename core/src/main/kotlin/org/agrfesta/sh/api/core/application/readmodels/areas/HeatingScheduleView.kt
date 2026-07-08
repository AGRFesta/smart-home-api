package org.agrfesta.sh.api.core.application.readmodels.areas

import org.agrfesta.sh.api.core.domain.areas.TemperatureInterval
import org.agrfesta.sh.api.core.domain.commons.Temperature
import java.time.LocalTime

data class HeatingScheduleView(
    val defaultTemperature: Temperature,
    val intervals: List<IntervalView>
) {
    companion object {
        /**
         * Builds the view from domain intervals, sorting them by [TemperatureInterval.startTime]
         * ascending — the ordering contract of `GET`/`PUT /areas/{areaId}/heating-schedule`.
         *
         * This factory is the single point applying that contract: build the view through it,
         * not via the primary constructor, whenever intervals come from an unordered source.
         */
        fun from(
            defaultTemperature: Temperature,
            intervals: Collection<TemperatureInterval>
        ) = HeatingScheduleView(
            defaultTemperature = defaultTemperature,
            intervals = intervals.sortedBy { it.startTime }.map {
                IntervalView(
                    temperature = it.temperature,
                    startTime = it.startTime,
                    endTime = it.endTime
                )
            }
        )
    }
}

data class IntervalView(
    val temperature: Temperature,
    val startTime: LocalTime,
    val endTime: LocalTime
)
