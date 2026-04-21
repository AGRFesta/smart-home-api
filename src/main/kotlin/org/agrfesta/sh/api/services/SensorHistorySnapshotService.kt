package org.agrfesta.sh.api.services

import arrow.core.Either
import org.agrfesta.sh.api.core.domain.commons.ThermoHygroData
import org.agrfesta.sh.api.core.domain.devices.DeviceDto
import org.agrfesta.sh.api.core.domain.devices.DeviceFeature
import org.agrfesta.sh.api.utils.CacheFailure
import org.agrfesta.sh.api.utils.LoggerDelegate
import org.agrfesta.sh.api.utils.SmartCache
import org.agrfesta.sh.api.utils.TimeService
import org.agrfesta.sh.api.utils.onLeftLogOn
import org.springframework.stereotype.Service

@Service
class SensorHistorySnapshotService(
    private val devicesService: DevicesService,
    private val historyDataService: SensorsHistoryDataService,
    private val cache: SmartCache,
    private val timeService: TimeService
) {
    private val logger by LoggerDelegate()

    fun snapshotDevicesData() {
        devicesService.getAllDto()
            .onRight { devices ->
                devices.filter { it.features.contains(DeviceFeature.SENSOR) }
                    .forEach { sensor ->
                        cache.getThermoHygroOf(sensor)
                            .onRightPersistAsTemperatureAndHumidityOf(sensor)
                            .onLeftLogOn(logger)
                    }
            }
            .onLeft { failure -> logger.error("unable to get devices", failure.exception) }
    }

    private fun Either<CacheFailure, ThermoHygroData>.onRightPersistAsTemperatureAndHumidityOf(sensor: DeviceDto) =
        onRight {
            val now = timeService.now()
            historyDataService.persistTemperature(
                sensorUuid = sensor.uuid,
                time = now,
                temperature = it.temperature
            ).onLeftLogOn(logger)
            historyDataService.persistHumidity(
                sensorUuid = sensor.uuid,
                time = now,
                relativeHumidity = it.relativeHumidity
            ).onLeftLogOn(logger)
        }

}
