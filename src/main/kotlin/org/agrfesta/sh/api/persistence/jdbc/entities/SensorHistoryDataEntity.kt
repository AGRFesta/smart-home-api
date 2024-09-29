package org.agrfesta.sh.api.persistence.jdbc.entities

import org.agrfesta.sh.api.domain.devices.SensorDataType
import org.agrfesta.sh.api.domain.devices.SensorHistoryData
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class SensorHistoryDataEntity(
    val sensorUuid: UUID,
    val time: Instant,
    val type: SensorDataType,
    val value: BigDecimal
) {
    fun asSensorHistoryData() = SensorHistoryData(time, type, value)
}
