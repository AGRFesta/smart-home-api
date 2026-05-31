package org.agrfesta.sh.api.core.application.usecases

import arrow.core.Either
import arrow.core.left
import org.agrfesta.sh.api.core.application.ports.inbounds.UpsertPropertyBatchUseCase
import org.agrfesta.sh.api.core.application.ports.outbounds.home.HomeStateRefreshPublisher
import org.agrfesta.sh.api.core.application.ports.outbounds.settings.PropertyRepository
import org.agrfesta.sh.api.core.domain.commons.PropertyUpsertEntry
import org.agrfesta.sh.api.core.domain.failures.DuplicatePropertyKeys
import org.agrfesta.sh.api.core.domain.failures.EmptyPropertyBatch
import org.agrfesta.sh.api.core.domain.failures.PropertyBatchTooLarge
import org.agrfesta.sh.api.core.domain.failures.UpsertPropertyBatchFailure
import org.springframework.stereotype.Service

@Service
class UpsertPropertyBatchService(
    private val propertyRepository: PropertyRepository,
    private val homeStateRefreshPublisher: HomeStateRefreshPublisher
) : UpsertPropertyBatchUseCase {

    override fun execute(entries: List<PropertyUpsertEntry>): Either<UpsertPropertyBatchFailure, Unit> = when {
        entries.isEmpty() -> EmptyPropertyBatch.left()
        entries.size > UpsertPropertyBatchUseCase.MAX_BATCH_SIZE ->
            PropertyBatchTooLarge(UpsertPropertyBatchUseCase.MAX_BATCH_SIZE).left()
        entries.map { it.key }.toSet().size < entries.size -> DuplicatePropertyKeys.left()
        else -> propertyRepository.upsertBatch(entries)
            .onRight { homeStateRefreshPublisher.publish() }
    }
}
