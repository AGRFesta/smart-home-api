package org.agrfesta.sh.api.schedulers

import kotlinx.coroutines.runBlocking
import org.agrfesta.sh.api.domain.devices.DeviceFeature
import org.agrfesta.sh.api.domain.devices.HumidityValue
import org.agrfesta.sh.api.domain.devices.TemperatureValue
import org.agrfesta.sh.api.domain.devices.onLeftLogOn
import org.agrfesta.sh.api.persistence.DevicesDao
import org.agrfesta.sh.api.providers.switchbot.SwitchBotService
import org.agrfesta.sh.api.utils.Cache
import org.agrfesta.sh.api.utils.LoggerDelegate
import org.agrfesta.sh.api.utils.getRelativeHumidityOf
import org.agrfesta.sh.api.utils.getTemperatureOf
import org.agrfesta.sh.api.utils.onLeftLogOn
import org.agrfesta.sh.api.utils.setHumidityOf
import org.agrfesta.sh.api.utils.setTemperatureOf
import org.springframework.scheduling.annotation.Async
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class DevicesDataFetchScheduler(
    private val devicesDao: DevicesDao,
    private val switchBotService: SwitchBotService,
    private val cache: Cache
) {
    private val logger by LoggerDelegate()

    @Scheduled(cron = "0 * * * * ?")
    @Async
    fun fetchDevicesData() {
        logger.info("[SCHEDULED TASK] start fetching devices data...")
        devicesDao.getAll()
            .onRight { devices ->
                devices.filter { f -> f.features.contains(DeviceFeature.SENSOR) }
                    .forEach {
                        runBlocking { switchBotService.fetchSensorReadings(it.providerId) }
                            .onRight { readings ->
                                if (readings is TemperatureValue) { cache.setTemperatureOf(device = it, readings.temperature) }
                                if (readings is HumidityValue) { cache.setHumidityOf(device = it, readings.relativeHumidity) }
                            }.onLeftLogOn(logger)
                    }
            }
            .onLeft { failure -> logger.error("unable to get devices", failure.exception) }
        logger.info("[SCHEDULED TASK] end fetching devices data")
    }

}
