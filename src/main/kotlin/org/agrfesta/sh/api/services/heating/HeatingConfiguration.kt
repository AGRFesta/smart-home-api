package org.agrfesta.sh.api.services.heating

import java.math.BigDecimal
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "heating")
data class HeatingConfiguration(
    val hysteresis: BigDecimal = BigDecimal("0.5"),
    val defaultStrategy: SharedHeatingAreasStrategy = SharedHeatingAreasStrategy.ECONOMY
)
