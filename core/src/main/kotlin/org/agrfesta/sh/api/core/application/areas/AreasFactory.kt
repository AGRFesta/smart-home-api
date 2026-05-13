package org.agrfesta.sh.api.core.application.areas

import java.util.*
import org.agrfesta.sh.api.core.domain.devices.Actuator
import org.agrfesta.sh.api.core.domain.devices.DeviceDriver
import org.agrfesta.sh.api.core.domain.devices.Heater
import org.agrfesta.sh.api.core.domain.devices.Sensor
import org.agrfesta.sh.api.core.application.ports.outbounds.settings.TemperatureSettingsRepository
import org.agrfesta.sh.api.core.domain.areas.Area
import org.agrfesta.sh.api.core.domain.areas.AreaDtoWithDevices
import org.agrfesta.sh.api.core.domain.areas.AreaImpl
import org.agrfesta.sh.api.core.domain.areas.HeatableAreaImpl
import org.agrfesta.sh.api.core.domain.areas.MonitoredClimateAreaImpl
import org.agrfesta.sh.api.utils.LoggerDelegate
import org.springframework.stereotype.Service

@Service
class AreasFactory(
    private val temperatureSettingsRepository: TemperatureSettingsRepository
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
                val setting = temperatureSettingsRepository.findAreaSetting(dto.uuid)
                    .onLeft { logger.error("Failed to retrieve temperature settings for area '${dto.uuid}': $it") }
                    .getOrNull()
                HeatableAreaImpl(heaters.first(), mcArea, setting)
            } else {
                mcArea
            }
        } else area
    }

}
