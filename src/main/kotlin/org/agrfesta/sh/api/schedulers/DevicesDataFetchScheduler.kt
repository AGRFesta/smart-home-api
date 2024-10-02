package org.agrfesta.sh.api.schedulers

import arrow.core.Either
import kotlinx.coroutines.runBlocking
import org.agrfesta.sh.api.domain.devices.DeviceFeature
import org.agrfesta.sh.api.domain.devices.HumidityValue
import org.agrfesta.sh.api.domain.devices.TemperatureValue
import org.agrfesta.sh.api.persistence.DevicesDao
import org.agrfesta.sh.api.providers.switchbot.SwitchBotService
import org.agrfesta.sh.api.utils.Cache
import org.agrfesta.sh.api.utils.LoggerDelegate
import org.springframework.scheduling.annotation.Async
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class DevicesDataFetchScheduler(
    private val devicesRepository: DevicesDao,
    private val switchBotService: SwitchBotService,
    private val cache: Cache
) {
    private val logger by LoggerDelegate()

    @Scheduled(cron = "0 * * * * ?")
    @Async
    fun fetchDevicesData() {
        logger.info("[SCHEDULED TASK] start fetching devices data...")
        devicesRepository.getAll()
            .filter { f -> f.features.contains(DeviceFeature.SENSOR) }
            .forEach {
                val response = runBlocking { switchBotService.fetchSensorReadings(it.providerId) }
                when (response) {
                    is Either.Left -> {
                        logger.error("Failure fetching data from ${it.name}")
                    }
                    is Either.Right -> {
                        val readings = response.value
                        if (readings is TemperatureValue) {
                            cache.set(
                                "sensors:${it.provider.name.lowercase()}:${it.providerId}:temperature",
                                readings.temperature.toString())
                        }
                        if (readings is HumidityValue) {
                            cache.set(
                                "sensors:${it.provider.name.lowercase()}:${it.providerId}:humidity",
                                readings.humidity.toString())
                        }
                    }
                }

            }
        logger.info("[SCHEDULED TASK] end fetching devices data")
    }

}
