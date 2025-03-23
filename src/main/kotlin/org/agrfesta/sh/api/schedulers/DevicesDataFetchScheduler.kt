package org.agrfesta.sh.api.schedulers

import kotlinx.coroutines.runBlocking
import org.agrfesta.sh.api.domain.devices.DeviceFeature
import org.agrfesta.sh.api.services.DevicesService
import org.agrfesta.sh.api.domain.devices.ThermoHygroDataValue
import org.agrfesta.sh.api.domain.devices.onLeftLogOn
import org.agrfesta.sh.api.providers.switchbot.SwitchBotService
import org.agrfesta.sh.api.utils.LoggerDelegate
import org.agrfesta.sh.api.utils.SmartCache
import org.springframework.scheduling.annotation.Async
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class DevicesDataFetchScheduler(
    private val devicesService: DevicesService,
    private val switchBotService: SwitchBotService,
    private val cache: SmartCache
) {
    private val logger by LoggerDelegate()

    @Scheduled(cron = "0 * * * * ?")
    @Async
    fun fetchDevicesData() {
        logger.info("[SCHEDULED TASK] start fetching devices data...")
        devicesService.getAll()
            .onRight { devices ->
                devices.filter { f -> f.features.contains(DeviceFeature.SENSOR) }
                    .forEach {
                        runBlocking { switchBotService.fetchSensorReadings(it.providerId) }
                            .onRight { readings ->
                                if (readings is ThermoHygroDataValue) {
                                    cache.setThermoHygroOf(device = it, thermoHygro = readings.thermoHygroData)
                                }
                            }.onLeftLogOn(logger)
                    }
            }
            .onLeft { failure -> logger.error("unable to get devices", failure.exception) }
        logger.info("[SCHEDULED TASK] end fetching devices data")
    }

}
