package org.agrfesta.sh.api.persistence.jdbc.adapters

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import org.agrfesta.sh.api.core.application.ports.outbounds.sensors.SensorsHistoryDataRepository
import org.agrfesta.sh.api.core.domain.commons.RelativeHumidity
import org.agrfesta.sh.api.core.domain.commons.Temperature
import org.agrfesta.sh.api.core.domain.devices.SensorDataType.HUMIDITY
import org.agrfesta.sh.api.core.domain.devices.SensorDataType.TEMPERATURE
import org.agrfesta.sh.api.core.domain.devices.SensorHistoryData
import org.agrfesta.sh.api.core.domain.failures.SensorHistoryRepositoryError
import org.agrfesta.sh.api.persistence.jdbc.repositories.SensorsHistoryDataJdbcRepository
import org.agrfesta.sh.api.utils.LoggerDelegate
import org.springframework.dao.DataAccessException
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.*

@Service
class SensorsHistoryDataJdbcAdapter(
    private val historyDataRepository: SensorsHistoryDataJdbcRepository
) : SensorsHistoryDataRepository {

    private val logger by LoggerDelegate()

    override fun persistTemperature(
        sensorUuid: UUID,
        time: Instant,
        temperature: Temperature
    ): Either<SensorHistoryRepositoryError, Unit> = try {
        historyDataRepository.persist(sensorUuid, time, TEMPERATURE, temperature.value).right()
    } catch (e: DataAccessException) {
        logger.error("Unexpected persistence error persisting temperature for sensor '$sensorUuid'", e)
        SensorHistoryRepositoryError.left()
    }

    override fun persistHumidity(
        sensorUuid: UUID,
        time: Instant,
        relativeHumidity: RelativeHumidity
    ): Either<SensorHistoryRepositoryError, Unit> = try {
        historyDataRepository.persist(sensorUuid, time, HUMIDITY, relativeHumidity.value).right()
    } catch (e: DataAccessException) {
        logger.error("Unexpected persistence error persisting humidity for sensor '$sensorUuid'", e)
        SensorHistoryRepositoryError.left()
    }

    override fun findBySensor(sensorUuid: UUID): Either<SensorHistoryRepositoryError, Collection<SensorHistoryData>> =
        try {
            historyDataRepository.findAllBySensorUuid(sensorUuid)
                .map { it.asSensorHistoryData() }
                .right()
        } catch (e: DataAccessException) {
            logger.error("Unexpected persistence error fetching history for sensor '$sensorUuid'", e)
            SensorHistoryRepositoryError.left()
        }
}
