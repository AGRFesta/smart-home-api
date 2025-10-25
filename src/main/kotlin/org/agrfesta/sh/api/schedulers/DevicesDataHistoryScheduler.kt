package org.agrfesta.sh.api.schedulers

import arrow.core.Either
import org.agrfesta.sh.api.domain.commons.ThermoHygroData
import org.agrfesta.sh.api.domain.devices.DeviceDto
import org.agrfesta.sh.api.domain.devices.DeviceFeature
import org.agrfesta.sh.api.services.onLeftLogOn
import org.agrfesta.sh.api.services.DevicesService
import org.agrfesta.sh.api.services.SensorsHistoryDataService
import org.agrfesta.sh.api.utils.CacheFailure
import org.agrfesta.sh.api.utils.LoggerDelegate
import org.agrfesta.sh.api.utils.SmartCache
import org.agrfesta.sh.api.utils.TimeService
import org.agrfesta.sh.api.utils.onLeftLogOn
import org.springframework.scheduling.annotation.Async
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class DevicesDataHistoryScheduler(
    private val devicesService: DevicesService,
    private val historyDataService: SensorsHistoryDataService,
    private val cache: SmartCache,
    private val timeService: TimeService
) {
    private val logger by LoggerDelegate()

    @Scheduled(cron = "0 */15 * * * *")
    @Async
    fun historyDevicesData() {
        logger.info("[SCHEDULED TASK] start devices history data...")
        devicesService.getAllDto()
            .onRight { devices ->
                devices.filter { it.features.contains(DeviceFeature.SENSOR) }
                .forEach {
                    cache.getThermoHygroOf(it)
                        .onRightPersistAsTemperatureAndHumidityOf(it)
                        .onLeftLogOn(logger)
                }
            }
            .onLeft { failure -> logger.error("unable to get devices", failure.exception) }
        logger.info("[SCHEDULED TASK] end devices history data")
    }

    private fun Either<CacheFailure, ThermoHygroData>.onRightPersistAsTemperatureAndHumidityOf(sensor: DeviceDto) =
        onRight {
            historyDataService.persistTemperature(
                sensorUuid = sensor.uuid,
                time = timeService.now(),
                temperature = it.temperature
            ).onLeftLogOn(logger)
            historyDataService.persistHumidity(
                sensorUuid = sensor.uuid,
                time = timeService.now(),
                relativeHumidity = it.relativeHumidity
            ).onLeftLogOn(logger)
        }

}
