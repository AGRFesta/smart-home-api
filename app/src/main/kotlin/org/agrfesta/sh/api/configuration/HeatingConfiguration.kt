package org.agrfesta.sh.api.configuration

import org.agrfesta.sh.api.core.application.ports.outbounds.settings.PropertyRepository
import org.agrfesta.sh.api.core.application.usecases.heating.HeatingStrategySelector
import org.agrfesta.sh.api.core.domain.commons.Percentage
import org.agrfesta.sh.api.core.domain.heating.SharedHeatingStrategy
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.math.BigDecimal

@Configuration
class HeatingConfiguration(
    @param:Value("\${heating.default-strategy:ECONOMY}") private val defaultStrategy: SharedHeatingStrategy,
    @param:Value("\${heating.params.economy-areas-percentage:0.5}") private val economyPercentage: BigDecimal
) {

    @Bean
    fun heatingStrategySelector(propertyRepository: PropertyRepository): HeatingStrategySelector =
        HeatingStrategySelector(defaultStrategy, Percentage(economyPercentage), propertyRepository)
}
