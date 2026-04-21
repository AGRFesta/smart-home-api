package org.agrfesta.sh.api.core.domain.devices

import java.math.BigDecimal
import java.time.Instant

data class SensorHistoryData(
    val time: Instant,
    val type: SensorDataType,
    val value: BigDecimal
)
