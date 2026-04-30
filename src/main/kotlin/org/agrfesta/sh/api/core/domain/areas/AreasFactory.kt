package org.agrfesta.sh.api.core.domain.areas

import java.util.*
import org.agrfesta.sh.api.core.domain.devices.Actuator
import org.agrfesta.sh.api.core.domain.devices.DeviceDriver
import org.agrfesta.sh.api.core.domain.devices.Heater
import org.agrfesta.sh.api.core.domain.devices.Sensor
import org.agrfesta.sh.api.services.heating.HeatingAreasService
import org.agrfesta.sh.api.utils.LoggerDelegate
import org.agrfesta.sh.api.utils.TimeService
import org.springframework.stereotype.Service

@Service
class AreasFactory(
    private val heatingAreasService: HeatingAreasService,
    private val timeService: TimeService
) {
    private val logger by LoggerDelegate()

    fun createArea(dto: AreaDtoWithDevices, devicesRegistry: Map<UUID, DeviceDriver>): Area {
        val sensors = dto.sensors.mapNotNull {
            devicesRegistry[it.uuid] ?: run {
                logger.error("Data inconsistency: Sensor with UUID ${it.uuid} referenced by area ${dto.name} but not found in persisted records")
                null
            }
        }.filterIsInstance<Sensor>()
        val actuators = dto.actuators.mapNotNull {
            devicesRegistry[it.uuid] ?: run {
                logger.error("Data inconsistency: Actuator with UUID ${it.uuid} referenced by area ${dto.name} but not found in persisted records")
                null
            }
        }.filterIsInstance<Actuator>()
        val heaters = actuators.filterIsInstance<Heater>()
        val area = AreaImpl(dto.uuid, sensors, actuators)
        return if (sensors.isNotEmpty()) {
            val mcArea = MonitoredClimateAreaImpl(area)
            if (heaters.isNotEmpty()) {
                if (heaters.size > 1) {
                    logger.warn("Area '${dto.uuid}' has multiple heaters assigned. Using the first one: '${heaters.first().uuid}'.")
                }
                HeatableAreaImpl(heaters.first(), mcArea, heatingAreasService, timeService)
            } else {
                mcArea
            }
        } else area
    }

}
