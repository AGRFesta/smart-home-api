package org.agrfesta.sh.api.domain.areas

import com.fasterxml.jackson.annotation.JsonFormat
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.*
import org.agrfesta.sh.api.domain.commons.Temperature

fun Collection<TemperatureInterval>.hasOverlap(): Boolean {
    val sortedIntervals = flatMap { interval ->
        if (interval.endTime < interval.startTime && interval.endTime != LocalTime.MIN) {
            listOf(
                TemperatureInterval(interval.temperature, interval.startTime, LocalTime.MAX),
                TemperatureInterval(interval.temperature, LocalTime.MIN, interval.endTime)
            )
        } else {
            listOf(interval)
        }
    }.sortedBy { it.startTime }
    for (i in 1 until sortedIntervals.size) {
        if (sortedIntervals[i].startTime < sortedIntervals[i - 1].endTime) {
            return true
        }
    }
    return false
}

/**
 * Represents the temperature settings for a specific area.
 *
 * @property areaId The unique identifier of the area.
 * @property defaultTemperature The default temperature to maintain when no schedule is active.
 * @property temperatureSchedule A set of scheduled temperature intervals.
 */
data class AreaTemperatureSetting(
    val areaId: UUID,
    val defaultTemperature: Temperature,
    val temperatureSchedule: Set<TemperatureInterval>
)

/**
 * Defines a time interval during which a specific temperature should be maintained.
 *
 * @property temperature The target temperature for this interval.
 * @property startTime The start time of the interval (inclusive).
 * @property endTime The end time of the interval (exclusive, unless it crosses midnight).
 */
data class TemperatureInterval(
    val temperature: Temperature,
    @param:JsonFormat(shape = JsonFormat.Shape.STRING, pattern = INTERVAL_TIME_FORMAT) val startTime: LocalTime,
    @param:JsonFormat(shape = JsonFormat.Shape.STRING, pattern = INTERVAL_TIME_FORMAT) val endTime: LocalTime
) {
    companion object {
        const val INTERVAL_TIME_FORMAT = "HH:mm"
        private val formatter = DateTimeFormatter.ofPattern(INTERVAL_TIME_FORMAT)
    }

    /**
     * Checks if the given [localTime] falls within this interval and returns the [temperature] if it does.
     *
     * This method correctly handles intervals that span across midnight (e.g., 22:00 to 06:00).
     *
     * @param localTime The time to check against the interval.
     * @return The [temperature] if [localTime] is within the interval, null otherwise.
     */
    fun temperatureAt(localTime: LocalTime): Temperature? {
        val inInterval = if (startTime <= endTime) {
            // interval does not cross midnight, inclusive start exclusive end
            localTime in startTime..<endTime
        } else {
            // interval crosses midnight (e.g. 22:00 - 06:00)
            localTime !in endTime..<startTime
        }
        return if (inInterval) temperature else null
    }

    override fun toString() = "[${startTime.format(formatter)} <-> ${endTime.format(formatter)}]"
}
