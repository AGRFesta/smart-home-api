package org.agrfesta.sh.api.configuration

import org.agrfesta.sh.api.core.application.ports.outbounds.settings.PropertyRepository
import org.agrfesta.sh.api.core.application.usecases.heating.DynamicSharedHeatingStrategyService
import org.agrfesta.sh.api.core.application.usecases.heating.EconomyAreasSharedHeatingStrategyService
import org.agrfesta.sh.api.core.application.usecases.heating.NamedSharedHeatingAreasStrategyService
import org.agrfesta.sh.api.core.domain.heating.SharedHeatingStrategy
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import java.math.BigDecimal

@Configuration
class HeatingConfiguration(
    @param:Value("\${heating.default-strategy:ECONOMY}") private val defaultStrategy: SharedHeatingStrategy,
    @param:Value("\${heating.params.economy-areas-percentage:0.5}") private val economyPercentage: BigDecimal
) {

    @Bean
    fun economyAreasSharedHeatingStrategyService(): EconomyAreasSharedHeatingStrategyService =
        EconomyAreasSharedHeatingStrategyService(economyPercentage)

    @Bean
    @Primary
    fun dynamicSharedHeatingStrategyService(
        strategyServices: Collection<NamedSharedHeatingAreasStrategyService>,
        propertyRepository: PropertyRepository
    ): DynamicSharedHeatingStrategyService =
        DynamicSharedHeatingStrategyService(defaultStrategy, strategyServices, propertyRepository)
}
