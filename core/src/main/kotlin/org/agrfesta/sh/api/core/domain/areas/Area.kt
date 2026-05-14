package org.agrfesta.sh.api.core.domain.areas

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import java.time.LocalTime
import java.util.*
import org.agrfesta.sh.api.core.domain.commons.Temperature
import org.agrfesta.sh.api.core.domain.commons.average
import org.agrfesta.sh.api.core.domain.devices.Actuator
import org.agrfesta.sh.api.core.domain.devices.Heater
import org.agrfesta.sh.api.core.domain.devices.Sensor
import org.agrfesta.sh.api.core.domain.devices.ThermoHygroDataValue
import org.agrfesta.sh.api.utils.LoggerDelegate

/**
 * Represents a physical or logical space within the smart home system.
 * An area is identified by a unique identifier and can contain a collection of [Sensor]s and [Actuator]s.
 */
interface Area {
    val uuid: UUID
    val sensors: Collection<Sensor>
    val actuators: Collection<Actuator>
}

/**
 * Default implementation of the [Area] interface.
 *
 * @property uuid The unique identifier of the area.
 * @property sensors The collection of sensors assigned to this area.
 * @property actuators The collection of actuators assigned to this area.
 */
data class AreaImpl(
    override val uuid: UUID,
    override val sensors: Collection<Sensor>,
    override val actuators: Collection<Actuator>
): Area

/**
 * Represents an [Area] that exposes climate data.
 * This implies that the area is equipped with at least one [Sensor] capable of monitoring climate conditions
 * (e.g., temperature, humidity).
 */
data object TemperatureUnavailable

interface MonitoredClimateArea: Area {
    /**
     * Retrieves the current temperature of the area.
     * This is typically an aggregate of readings from the sensors in the area.
     *
     * @return [Either] a [TemperatureUnavailable] if the temperature cannot be retrieved, or the [Temperature].
     */
    fun getCurrentTemperature(): Either<TemperatureUnavailable, Temperature>
    //TODO humidity
}

/**
 * Implementation of [MonitoredClimateArea] that delegates basic area functionality to an underlying [Area] instance.
 * It aggregates readings from available sensors to provide area-level climate data.
 *
 * @param area The underlying [Area] instance.
 * @throws IllegalArgumentException if the area has no sensors.
 */
class MonitoredClimateAreaImpl(
    private val area: Area
): MonitoredClimateArea, Area by area {
    private val logger by LoggerDelegate()

    init {
        require(area.sensors.isNotEmpty()) {
            "MonitoredClimateAreaImpl must have at least one sensor! Area '${area.uuid}'"
        }
    }

    override fun getCurrentTemperature(): Either<TemperatureUnavailable, Temperature> =
        sensors.mapNotNull {
            it.fetchReadings().fold(
                    ifLeft = { _ ->
                        logger.error("Unable to fetch readings by sensor '${it.uuid}'.")
                        null},
                    ifRight = { r -> (r as? ThermoHygroDataValue)?.thermoHygroData?.temperature }
                )
        }.average()?.right()
            ?: TemperatureUnavailable.left()

}

/**
 * Represents a [MonitoredClimateArea] that has heating capabilities.
 * In addition to monitoring climate, it includes a [Heater] and logic to determine the target temperature.
 */
interface HeatableArea: MonitoredClimateArea {
    val heater: Heater

    /**
     * Calculates the current target temperature for the area based on current settings and schedules.
     *
     * @param currentTime The current local time used to evaluate the active schedule slot.
     * @return The target [Temperature], or null if no target is set or available.
     */
    fun getCurrentTargetTemperature(currentTime: LocalTime): Temperature?
}

/**
 * Implementation of [HeatableArea].
 * It combines a [MonitoredClimateArea] with a [Heater] and a pre-resolved [AreaTemperatureSetting].
 *
 * @property heater The heater associated with this area.
 * @param mcArea The underlying monitored climate area.
 * @param setting The temperature setting for this area, or null if none is configured.
 */
class HeatableAreaImpl(
    override val heater: Heater,
    private val mcArea: MonitoredClimateArea,
    private val setting: AreaTemperatureSetting?
): HeatableArea, MonitoredClimateArea by mcArea {

    override fun getCurrentTargetTemperature(currentTime: LocalTime): Temperature? =
        setting?.targetTemperatureAt(currentTime)

}
