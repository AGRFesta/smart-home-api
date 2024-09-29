package org.agrfesta.sh.api.persistence

import arrow.core.Either
import org.agrfesta.sh.api.domain.devices.Humidity
import org.agrfesta.sh.api.domain.devices.SensorHistoryData
import org.agrfesta.sh.api.domain.devices.Temperature
import java.time.Instant
import java.util.*

interface SensorsHistoryDataDao {

    fun persistTemperature(
        sensorUuid: UUID,
        time: Instant,
        temperature: Temperature
    ): Either<PersistenceFailure, SensorDataPersistenceSuccess>

    fun persistHumidity(
        sensorUuid: UUID,
        time: Instant,
        humidity: Humidity
    ): Either<PersistenceFailure, SensorDataPersistenceSuccess>

    fun findBySensor(sensorUuid: UUID): Either<PersistenceFailure, Collection<SensorHistoryData>>

}

object SensorDataPersistenceSuccess
