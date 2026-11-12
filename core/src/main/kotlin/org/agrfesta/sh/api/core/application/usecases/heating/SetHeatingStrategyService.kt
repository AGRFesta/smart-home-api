package org.agrfesta.sh.api.core.application.usecases.heating

import arrow.core.Either
import org.agrfesta.sh.api.core.application.ports.inbounds.heating.SetHeatingStrategyUseCase
import org.agrfesta.sh.api.core.application.ports.outbounds.home.HomeStateRefreshPublisher
import org.agrfesta.sh.api.core.application.ports.outbounds.settings.PropertyRepository
import org.agrfesta.sh.api.core.application.usecases.heating.HeatingStrategySelector.Companion.HEATING_STRATEGY_KEY
import org.agrfesta.sh.api.core.domain.failures.SetHeatingStrategyFailure
import org.agrfesta.sh.api.core.domain.heating.SharedHeatingStrategy
import org.springframework.stereotype.Service

@Service
class SetHeatingStrategyService(
    private val propertyRepository: PropertyRepository,
    private val homeStateRefreshPublisher: HomeStateRefreshPublisher
) : SetHeatingStrategyUseCase {

    override fun execute(strategy: SharedHeatingStrategy): Either<SetHeatingStrategyFailure, Unit> =
        propertyRepository.upsert(HEATING_STRATEGY_KEY, strategy.name)
            .onRight { homeStateRefreshPublisher.publish() }
}
