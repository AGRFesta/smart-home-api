package org.agrfesta.sh.api.core.application.ports.outbounds.sensors

import arrow.core.Either
import java.time.Instant
import java.util.UUID
import org.agrfesta.sh.api.core.domain.commons.RelativeHumidity
import org.agrfesta.sh.api.core.domain.commons.Temperature
import org.agrfesta.sh.api.core.domain.devices.SensorHistoryData
import org.agrfesta.sh.api.core.domain.failures.SensorHistoryRepositoryError

interface SensorsHistoryDataRepository {

    /**
     * Persists a temperature reading for a sensor at the given time.
     *
     * @param sensorUuid the unique identifier of the sensor.
     * @param time the instant at which the reading was taken.
     * @param temperature the temperature value to persist.
     * @return [Either.Right] with [Unit] on success,
     * or [Either.Left] with [SensorHistoryRepositoryError] if a database error occurs.
     */
    fun persistTemperature(sensorUuid: UUID, time: Instant, temperature: Temperature): Either<SensorHistoryRepositoryError, Unit>

    /**
     * Persists a humidity reading for a sensor at the given time.
     *
     * @param sensorUuid the unique identifier of the sensor.
     * @param time the instant at which the reading was taken.
     * @param relativeHumidity the humidity value to persist.
     * @return [Either.Right] with [Unit] on success,
     * or [Either.Left] with [SensorHistoryRepositoryError] if a database error occurs.
     */
    fun persistHumidity(
        sensorUuid: UUID,
        time: Instant,
        relativeHumidity: RelativeHumidity
    ): Either<SensorHistoryRepositoryError, Unit>

    /**
     * Retrieves all history data recorded for a specific sensor.
     *
     * @param sensorUuid the unique identifier of the sensor.
     * @return [Either.Right] with the collection of [SensorHistoryData] (possibly empty),
     * or [Either.Left] with [SensorHistoryRepositoryError] if a database error occurs.
     */
    fun findBySensor(sensorUuid: UUID): Either<SensorHistoryRepositoryError, Collection<SensorHistoryData>>

}
