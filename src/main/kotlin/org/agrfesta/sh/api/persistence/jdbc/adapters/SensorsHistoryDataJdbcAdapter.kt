package org.agrfesta.sh.api.persistence.jdbc.adapters

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import java.time.Instant
import java.util.*
import org.agrfesta.sh.api.core.domain.commons.RelativeHumidity
import org.agrfesta.sh.api.core.domain.commons.Temperature
import org.agrfesta.sh.api.core.domain.devices.SensorDataType.HUMIDITY
import org.agrfesta.sh.api.core.domain.devices.SensorDataType.TEMPERATURE
import org.agrfesta.sh.api.core.domain.devices.SensorHistoryData
import org.agrfesta.sh.api.core.domain.failures.PersistenceFailure
import org.agrfesta.sh.api.core.application.ports.outbounds.SensorsHistoryDataRepository
import org.agrfesta.sh.api.persistence.jdbc.repositories.SensorsHistoryDataJdbcRepository
import org.springframework.dao.DataAccessException
import org.springframework.stereotype.Service

@Service
class SensorsHistoryDataJdbcAdapter(
    private val historyDataRepository: SensorsHistoryDataJdbcRepository
): SensorsHistoryDataRepository {

    override fun persistTemperature(
        sensorUuid: UUID,
        time: Instant,
        temperature: Temperature
    ): Either<PersistenceFailure, Unit> = try {
        historyDataRepository.persist(sensorUuid, time, TEMPERATURE, temperature.value).right()
    } catch (e: DataAccessException) {
        PersistenceFailure(e).left()
    }

    override fun persistHumidity(
        sensorUuid: UUID,
        time: Instant,
        relativeHumidity: RelativeHumidity
    ): Either<PersistenceFailure, Unit> = try {
        historyDataRepository.persist(sensorUuid, time, HUMIDITY, relativeHumidity.value).right()
    } catch (e: DataAccessException) {
        PersistenceFailure(e).left()
    }

    override fun findBySensor(sensorUuid: UUID): Either<PersistenceFailure, Collection<SensorHistoryData>> = try {
        historyDataRepository.findAllBySensorUuid(sensorUuid)
            .map { it.asSensorHistoryData() }
            .right()
    } catch (e: DataAccessException) {
        PersistenceFailure(e).left()
    }

}
