package org.agrfesta.sh.api.domain.areas

import com.fasterxml.jackson.annotation.JsonFormat
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
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
        val ROME_ZONE: ZoneId = ZoneId.of("Europe/Rome")
    }

    /**
     * Returns [temperature] if the given [instant] falls within the interval
     * in the given [zone] (defaults to Europe/Rome), otherwise returns null.
     *
     * This method correctly handles intervals that span midnight and
     * relies on the ZoneId rules (including DST).
     */
    fun temperatureAt(instant: Instant, zone: ZoneId = ROME_ZONE): Temperature? {
        // Convert the instant to a local time in the specified zone
        val localTime = instant.atZone(zone).toLocalTime()

        val inInterval = if (startTime <= endTime) {
            // interval does not cross midnight, inclusive start exclusive end
            localTime >= startTime && localTime < endTime
        } else {
            // interval crosses midnight (e.g. 22:00 - 06:00)
            localTime >= startTime || localTime < endTime
        }

        return if (inInterval) temperature else null
    }

    fun overlapsWith(other: TemperatureInterval): Boolean =
        this.startTime < other.endTime && this.endTime > other.startTime

    override fun toString() = "[${startTime.format(formatter)} <-> ${endTime.format(formatter)}]"
}
