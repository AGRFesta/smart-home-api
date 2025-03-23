package org.agrfesta.sh.api.services

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import java.time.Instant
import java.util.*
import org.agrfesta.sh.api.domain.commons.RelativeHumidity
import org.agrfesta.sh.api.domain.commons.Temperature
import org.agrfesta.sh.api.domain.devices.SensorHistoryData
import org.agrfesta.sh.api.domain.failures.PersistenceFailure
import org.agrfesta.sh.api.persistence.SensorsHistoryDataDao
import org.springframework.stereotype.Service

@Service
class SensorsHistoryDataService(
    private val sensorsHistoryDataDao: SensorsHistoryDataDao
) {

    fun persistTemperature(
        sensorUuid: UUID,
        time: Instant,
        temperature: Temperature
    ): Either<PersistenceFailure, Unit> = try {
        sensorsHistoryDataDao.persistTemperature(sensorUuid, time, temperature).right()
    } catch (e: Exception) {
        PersistenceFailure(e).left()
    }

    fun persistHumidity(
        sensorUuid: UUID,
        time: Instant,
        relativeHumidity: RelativeHumidity
    ): Either<PersistenceFailure, Unit> = try {
        sensorsHistoryDataDao.persistHumidity(sensorUuid, time, relativeHumidity).right()
    } catch (e: Exception) {
        PersistenceFailure(e).left()
    }

    fun findBySensor(sensorUuid: UUID): Either<PersistenceFailure, Collection<SensorHistoryData>> = try {
        sensorsHistoryDataDao.findBySensor(sensorUuid).right()
    } catch (e: Exception) {
        PersistenceFailure(e).left()
    }

}
