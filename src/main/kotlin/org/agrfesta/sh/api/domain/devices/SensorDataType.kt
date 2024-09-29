package org.agrfesta.sh.api.domain.devices

import org.agrfesta.sh.api.domain.commons.Percentage
import java.math.BigDecimal

typealias Temperature = BigDecimal
typealias Humidity = Percentage

enum class SensorDataType { TEMPERATURE, HUMIDITY }
