package org.agrfesta.sh.api.persistence.jdbc.entities

import java.math.BigDecimal
import java.util.UUID

class TemperatureSettingEntity(
    val areaUuid: UUID,
    val defaultTemperature: BigDecimal
)
