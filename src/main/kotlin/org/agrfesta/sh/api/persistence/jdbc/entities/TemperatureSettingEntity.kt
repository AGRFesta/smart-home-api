package org.agrfesta.sh.api.persistence.jdbc.entities

import java.util.*
import org.agrfesta.sh.api.domain.commons.Temperature

class TemperatureSettingEntity(
    val uuid: UUID,
    val areaUuid: UUID,
    val defaultTemperature: Temperature
)
