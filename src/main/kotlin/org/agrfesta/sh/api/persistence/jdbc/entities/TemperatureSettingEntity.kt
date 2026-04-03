package org.agrfesta.sh.api.persistence.jdbc.entities

import java.math.BigDecimal
import java.util.UUID
import org.agrfesta.sh.api.domain.commons.Temperature

class TemperatureSettingEntity(
    val areaUuid: UUID,
    val defaultTemperature: BigDecimal
)
