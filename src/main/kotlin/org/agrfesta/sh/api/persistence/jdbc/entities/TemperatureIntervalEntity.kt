package org.agrfesta.sh.api.persistence.jdbc.entities

import java.time.LocalTime
import java.util.*
import org.agrfesta.sh.api.domain.commons.Temperature

class TemperatureIntervalEntity (
    val uuid: UUID,
    val settingUuid: UUID,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val temperature: Temperature
)
