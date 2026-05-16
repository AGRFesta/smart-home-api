package org.agrfesta.sh.api.core.application.usecases

import arrow.core.Either
import org.agrfesta.sh.api.core.application.ports.inbounds.UpsertPropertyUseCase
import org.agrfesta.sh.api.core.application.ports.outbounds.settings.PropertyRepository
import org.agrfesta.sh.api.core.domain.failures.UpsertPropertyFailure
import org.springframework.stereotype.Service

@Service
class UpsertPropertyService(
    private val propertyRepository: PropertyRepository
) : UpsertPropertyUseCase {

    override fun execute(key: String, value: String, ttl: Long?): Either<UpsertPropertyFailure, Unit> =
        propertyRepository.upsert(key, value, ttl)

}
