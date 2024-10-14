package org.agrfesta.sh.api.schedulers

import arrow.core.Either
import org.agrfesta.sh.api.domain.devices.Device
import org.agrfesta.sh.api.domain.devices.DeviceFeature
import org.agrfesta.sh.api.domain.devices.Humidity
import org.agrfesta.sh.api.domain.devices.Temperature
import org.agrfesta.sh.api.persistence.DevicesDao
import org.agrfesta.sh.api.persistence.SensorsHistoryDataDao
import org.agrfesta.sh.api.persistence.onLeftLogOn
import org.agrfesta.sh.api.utils.Cache
import org.agrfesta.sh.api.utils.CacheFailure
import org.agrfesta.sh.api.utils.LoggerDelegate
import org.agrfesta.sh.api.utils.TimeService
import org.agrfesta.sh.api.utils.getHumidityOf
import org.agrfesta.sh.api.utils.getTemperatureOf
import org.agrfesta.sh.api.utils.onLeftLogOn
import org.springframework.scheduling.annotation.Async
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.math.BigDecimal

@Component
class DevicesDataHistoryScheduler(
    private val devicesDao: DevicesDao,
    private val historyDataDao: SensorsHistoryDataDao,
    private val cache: Cache,
    private val timeService: TimeService
) {
    private val logger by LoggerDelegate()

    @Scheduled(cron = "0 */15 * * * *")
    @Async
    fun historyDevicesData() {
        logger.info("[SCHEDULED TASK] start devices history data...")
        devicesDao.getAll()
            .filter { it.features.contains(DeviceFeature.SENSOR) }
            .forEach {
                cache.getTemperatureOf(it)
                    .onRightPersistAsTemperatureOf(it)
                    .onLeftLogOn(logger)
                cache.getHumidityOf(it)
                    .onRightPersistAsHumidityOf(it)
                    .onLeftLogOn(logger)
            }
        logger.info("[SCHEDULED TASK] end devices history data")
    }

    private fun Either<CacheFailure, String>.onRightPersistAsTemperatureOf(sensor: Device) = onRight {
        historyDataDao.persistTemperature(
            sensorUuid = sensor.uuid,
            time = timeService.now(),
            temperature = Temperature(it)
        ).onLeftLogOn(logger)
    }

    private fun Either<CacheFailure, String>.onRightPersistAsHumidityOf(sensor: Device) = onRight {
        historyDataDao.persistHumidity(
            sensorUuid = sensor.uuid,
            time = timeService.now(),
            humidity = Humidity(BigDecimal(it))
        ).onLeftLogOn(logger)
    }

}