package org.agrfesta.sh.api.persistence

import java.time.Instant
import java.util.*
import org.agrfesta.sh.api.domain.commons.RelativeHumidity
import org.agrfesta.sh.api.domain.commons.Temperature
import org.agrfesta.sh.api.domain.devices.SensorHistoryData

interface SensorsHistoryDataDao {

    fun persistTemperature(sensorUuid: UUID, time: Instant, temperature: Temperature)

    fun persistHumidity(sensorUuid: UUID, time: Instant, relativeHumidity: RelativeHumidity)

    fun findBySensor(sensorUuid: UUID): Collection<SensorHistoryData>

}
