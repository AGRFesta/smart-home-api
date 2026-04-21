package org.agrfesta.sh.api.services

import arrow.core.Either
import java.time.Instant
import java.util.*
import org.agrfesta.sh.api.core.domain.commons.RelativeHumidity
import org.agrfesta.sh.api.core.domain.commons.Temperature
import org.agrfesta.sh.api.core.domain.devices.SensorHistoryData
import org.agrfesta.sh.api.core.domain.failures.PersistenceFailure
import org.agrfesta.sh.api.core.application.ports.outbounds.SensorsHistoryDataRepository
import org.springframework.stereotype.Service

@Service
class SensorsHistoryDataService(
    private val sensorsHistoryDataRepository: SensorsHistoryDataRepository
) {

    fun persistTemperature(
        sensorUuid: UUID,
        time: Instant,
        temperature: Temperature
    ): Either<PersistenceFailure, Unit> = sensorsHistoryDataRepository.persistTemperature(sensorUuid, time, temperature)

    fun persistHumidity(
        sensorUuid: UUID,
        time: Instant,
        relativeHumidity: RelativeHumidity
    ): Either<PersistenceFailure, Unit> = sensorsHistoryDataRepository.persistHumidity(sensorUuid, time, relativeHumidity)

    fun findBySensor(sensorUuid: UUID): Either<PersistenceFailure, Collection<SensorHistoryData>> =
        sensorsHistoryDataRepository.findBySensor(sensorUuid)

}
