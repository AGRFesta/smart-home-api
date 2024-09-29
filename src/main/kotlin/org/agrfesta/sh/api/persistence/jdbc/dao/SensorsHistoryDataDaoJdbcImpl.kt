package org.agrfesta.sh.api.persistence.jdbc.dao

import arrow.core.Either
import arrow.core.right
import org.agrfesta.sh.api.domain.devices.Humidity
import org.agrfesta.sh.api.domain.devices.SensorDataType.HUMIDITY
import org.agrfesta.sh.api.domain.devices.SensorDataType.TEMPERATURE
import org.agrfesta.sh.api.domain.devices.SensorHistoryData
import org.agrfesta.sh.api.domain.devices.Temperature
import org.agrfesta.sh.api.persistence.PersistenceFailure
import org.agrfesta.sh.api.persistence.SensorDataPersistenceSuccess
import org.agrfesta.sh.api.persistence.SensorsHistoryDataDao
import org.agrfesta.sh.api.persistence.jdbc.repositories.SensorsHistoryDataJdbcRepository
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.*

@Service
class SensorsHistoryDataDaoJdbcImpl(
    private val historyDataRepository: SensorsHistoryDataJdbcRepository
): SensorsHistoryDataDao {

    override fun persistTemperature(
        sensorUuid: UUID,
        time: Instant,
        temperature: Temperature
    ): Either<PersistenceFailure, SensorDataPersistenceSuccess> {
        return historyDataRepository.persist(sensorUuid, time, TEMPERATURE, temperature)
    }

    override fun persistHumidity(
        sensorUuid: UUID,
        time: Instant,
        humidity: Humidity
    ): Either<PersistenceFailure, SensorDataPersistenceSuccess> {
        return historyDataRepository.persist(sensorUuid, time, HUMIDITY, humidity.value)
    }

    override fun findBySensor(sensorUuid: UUID): Either<PersistenceFailure, Collection<SensorHistoryData>> {
        return historyDataRepository.findAllBySensorUuid(sensorUuid).map { it.asSensorHistoryData() }.right()
    }

}
