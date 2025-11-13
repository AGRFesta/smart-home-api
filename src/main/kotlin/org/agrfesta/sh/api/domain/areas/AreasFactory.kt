package org.agrfesta.sh.api.domain.areas

import java.util.*
import org.agrfesta.sh.api.domain.devices.Device
import org.agrfesta.sh.api.domain.devices.Heater
import org.agrfesta.sh.api.domain.devices.Sensor
import org.agrfesta.sh.api.services.HeatingAreasService
import org.springframework.stereotype.Service

@Service
class AreasFactory(
    private val heatingAreasService: HeatingAreasService
) {

    fun createArea(dto: AreaDtoWithDevices, devicesRegistry: Map<UUID, Device>): Area {
        val areaDevices = dto.devices.map { devicesRegistry[it.uuid] ?: error("unexpected missing device") }
        val sensors = areaDevices.filterIsInstance<Sensor>()
        val heaters = areaDevices.filterIsInstance<Heater>()
        val area = AreaImpl(dto.uuid, areaDevices)
        return if (sensors.isNotEmpty()) {
            val mcArea = MonitoredClimateAreaImpl(area)
            if (heaters.isNotEmpty()) {
                HeatableAreaImpl(heaters.first(), mcArea, heatingAreasService)
            } else {
                mcArea
            }
        } else area
    }

}
