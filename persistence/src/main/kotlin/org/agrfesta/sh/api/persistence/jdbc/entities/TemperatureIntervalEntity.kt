package org.agrfesta.sh.api.persistence.jdbc.entities

import java.math.BigDecimal
import java.time.LocalTime
import java.util.*

class TemperatureIntervalEntity(
    val uuid: UUID,
    val areaUuid: UUID,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val temperature: BigDecimal
)
