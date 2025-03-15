package org.agrfesta.sh.api.domain

import com.fasterxml.jackson.annotation.JsonFormat
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.*
import org.agrfesta.sh.api.domain.commons.Temperature

data class AreaTemperatureSetting(
    val areaId: UUID,
    val defaultTemperature: Temperature,
    val temperatureSchedule: Set<TemperatureInterval>
)

data class TemperatureInterval(
    val temperature: Temperature,
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = INTERVAL_TIME_FORMAT) val startTime: LocalTime,
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = INTERVAL_TIME_FORMAT) val endTime: LocalTime
) {
    companion object {
        const val INTERVAL_TIME_FORMAT = "HH:mm"
        private val formatter = DateTimeFormatter.ofPattern(INTERVAL_TIME_FORMAT)
    }

    fun overlapsWith(other: TemperatureInterval): Boolean =
        this.startTime < other.endTime && this.endTime > other.startTime

    override fun toString() = "[${startTime.format(formatter)} <-> ${endTime.format(formatter)}]"
}
