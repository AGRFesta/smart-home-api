package org.agrfesta.sh.api.core.application.usecases.heating

import arrow.core.Either
import org.agrfesta.sh.api.core.application.ports.inbounds.heating.SetHeatingEnabledUseCase
import org.agrfesta.sh.api.core.application.ports.outbounds.home.HomeStateRefreshPublisher
import org.agrfesta.sh.api.core.application.ports.outbounds.settings.PropertyRepository
import org.agrfesta.sh.api.core.application.usecases.heating.EvaluateHeatingStateService.Companion.HEATING_ENABLED_KEY
import org.agrfesta.sh.api.core.domain.failures.SetHeatingEnabledFailure
import org.springframework.stereotype.Service

@Service
class SetHeatingEnabledService(
    private val propertyRepository: PropertyRepository,
    private val homeStateRefreshPublisher: HomeStateRefreshPublisher
) : SetHeatingEnabledUseCase {

    override fun execute(enabled: Boolean): Either<SetHeatingEnabledFailure, Unit> =
        propertyRepository.upsert(HEATING_ENABLED_KEY, enabled.toString())
            .onRight { homeStateRefreshPublisher.publish() }
}
