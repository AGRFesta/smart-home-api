package org.agrfesta.sh.api.core.application.usecases

import arrow.core.Either
import org.agrfesta.sh.api.core.application.ports.inbounds.GetPropertyUseCase
import org.agrfesta.sh.api.core.application.ports.outbounds.settings.PropertyRepository
import org.agrfesta.sh.api.core.domain.commons.PropertyEntry
import org.agrfesta.sh.api.core.domain.failures.GetPropertyFailure
import org.springframework.stereotype.Service

@Service
class GetPropertyService(
    private val propertyRepository: PropertyRepository
) : GetPropertyUseCase {

    override fun execute(key: String): Either<GetPropertyFailure, PropertyEntry> =
        propertyRepository.getEntry(key)
}
