package org.agrfesta.sh.api.schedulers

import arrow.core.Either
import kotlinx.coroutines.runBlocking
import org.agrfesta.sh.api.domain.devices.DeviceFeature
import org.agrfesta.sh.api.domain.devices.HumidityValue
import org.agrfesta.sh.api.domain.devices.TemperatureValue
import org.agrfesta.sh.api.persistence.repositories.DevicesRepository
import org.agrfesta.sh.api.providers.switchbot.SwitchBotService
import org.agrfesta.sh.api.utils.Cache
import org.springframework.scheduling.annotation.Async
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class DevicesDataFetchScheduler(
    private val devicesRepository: DevicesRepository,
    private val switchBotService: SwitchBotService,
    private val cache: Cache
) {

    @Scheduled(cron = "0 * * * * ?")
    @Async
    fun fetchDevicesData() {
        devicesRepository.findAll()
            .filter { f -> f.features.map { DeviceFeature.valueOf(it) }.contains(DeviceFeature.SENSOR) }
            .forEach {
                val response = runBlocking { switchBotService.fetchSensorReadings(it.providerId) }
                when (response) {
                    is Either.Left -> {
                        //TODO just logs
                    }
                    is Either.Right -> {
                        val readings = response.value
                        if (readings is TemperatureValue) {
                            cache.set(
                                "temp:${it.provider.name.lowercase()}:${it.providerId}",
                                readings.temperature.toString())
                        }
                        if (readings is HumidityValue) {
                            cache.set(
                                "hum:${it.provider.name.lowercase()}:${it.providerId}",
                                readings.humidity.toString())
                        }
                    }
                }

            }
    }

}
