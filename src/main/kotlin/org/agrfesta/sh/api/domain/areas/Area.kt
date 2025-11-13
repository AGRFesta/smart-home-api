package org.agrfesta.sh.api.domain.areas

import arrow.core.Either
import java.time.Instant
import java.util.*
import kotlinx.coroutines.runBlocking
import org.agrfesta.sh.api.domain.commons.Temperature
import org.agrfesta.sh.api.domain.devices.Device
import org.agrfesta.sh.api.domain.devices.Sensor
import org.agrfesta.sh.api.domain.devices.Heater
import org.agrfesta.sh.api.domain.devices.ThermoHygroDataValue
import org.agrfesta.sh.api.domain.failures.Failure
import org.agrfesta.sh.api.services.HeatingAreasService

interface Area {
    val uuid: UUID
    val devices: Collection<Device>
}

data class AreaImpl(
    override val uuid: UUID,
    override val devices: Collection<Device>
): Area

/**
 * Represents an [Area] that expose climate data, this means that the area has at least a [Sensor]
 */
interface MonitoredClimateArea: Area {
    fun getActualTemperature(): Either<Failure, Temperature>
    //TODO humidity
}

class MonitoredClimateAreaImpl(
    private val area: Area
): MonitoredClimateArea, Area by area {
    private val sensors = area.devices.filterIsInstance<Sensor>()

    /** considers
     * only the first sensor
     */
    override fun getActualTemperature(): Either<Failure, Temperature> {
        return runBlocking {
            sensors.first().fetchReadings().map { (it as ThermoHygroDataValue).thermoHygroData.temperature }
        }
    }

}

interface HeatableArea: MonitoredClimateArea {
    val heater: Heater
    fun getActualTargetTemperature(): Temperature?
}

class HeatableAreaImpl(
    override val heater: Heater,
    private val mcArea: MonitoredClimateArea,
    private val heatingAreasService: HeatingAreasService
): HeatableArea, MonitoredClimateArea by mcArea {

    override fun getActualTargetTemperature(): Temperature? {
        return heatingAreasService.findAreaSetting(uuid).fold(
            ifLeft = {null},
            ifRight = {
                it?.temperatureSchedule
                    ?.firstNotNullOfOrNull { i -> i.temperatureAt(Instant.now()) }
                    ?: it?.defaultTemperature
            }
        )
    }

}
