package org.agrfesta.sh.api.persistence.jdbc.dao

import java.time.Instant
import java.util.*
import org.agrfesta.sh.api.domain.commons.RelativeHumidity
import org.agrfesta.sh.api.domain.commons.Temperature
import org.agrfesta.sh.api.domain.devices.SensorDataType.HUMIDITY
import org.agrfesta.sh.api.domain.devices.SensorDataType.TEMPERATURE
import org.agrfesta.sh.api.persistence.SensorsHistoryDataDao
import org.agrfesta.sh.api.persistence.jdbc.repositories.SensorsHistoryDataJdbcRepository
import org.springframework.stereotype.Service

@Service
class SensorsHistoryDataDaoJdbcImpl(
    private val historyDataRepository: SensorsHistoryDataJdbcRepository
): SensorsHistoryDataDao {

    override fun persistTemperature(
        sensorUuid: UUID,
        time: Instant,
        temperature: Temperature
    ) = historyDataRepository.persist(sensorUuid, time, TEMPERATURE, temperature)

    override fun persistHumidity(
        sensorUuid: UUID,
        time: Instant,
        relativeHumidity: RelativeHumidity
    ) = historyDataRepository.persist(sensorUuid, time, HUMIDITY, relativeHumidity.value)

    override fun findBySensor(sensorUuid: UUID) = historyDataRepository.findAllBySensorUuid(sensorUuid)
        .map { it.asSensorHistoryData() }

}
