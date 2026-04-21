package org.agrfesta.sh.api.services

import org.agrfesta.sh.api.core.domain.devices.Sensor
import org.agrfesta.sh.api.core.domain.devices.ThermoHygroDataValue
import org.agrfesta.sh.api.core.domain.devices.onLeftLogOn
import org.agrfesta.sh.api.utils.LoggerDelegate
import org.agrfesta.sh.api.utils.SmartCache
import org.springframework.stereotype.Service

@Service
class SensorReadingsSyncService(
    private val devicesService: DevicesService,
    private val cache: SmartCache
) {
    private val logger by LoggerDelegate()

    suspend fun fetchAndCacheSensorData() {
        devicesService.getAllDevices()
            .onRight { devices ->
                devices.filterIsInstance<Sensor>()
                    .forEach { sensor ->
                        logger.info("Reading ${sensor.provider} sensor [${sensor.deviceProviderId}] data")
                        sensor.fetchReadings()
                            .onRight { readings ->
                                if (readings is ThermoHygroDataValue) {
                                    cache.setThermoHygroOf(device = sensor, thermoHygro = readings.thermoHygroData)
                                }
                            }.onLeftLogOn(logger)
                    }
            }
            .onLeft { failure -> logger.error("Unable to get devices", failure.exception) }
    }

}
