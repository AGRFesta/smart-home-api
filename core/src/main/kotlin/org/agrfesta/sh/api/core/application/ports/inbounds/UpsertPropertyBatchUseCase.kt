package org.agrfesta.sh.api.core.application.ports.inbounds

import arrow.core.Either
import org.agrfesta.sh.api.core.domain.commons.PropertyUpsertEntry
import org.agrfesta.sh.api.core.domain.failures.UpsertPropertyBatchFailure

interface UpsertPropertyBatchUseCase {

    companion object {
        const val MAX_BATCH_SIZE = 1000
    }

    /**
     * Inserts or updates a batch of property entries.
     *
     * Validates that the batch is non-empty, does not exceed the maximum allowed size,
     * and contains no duplicate keys before persisting.
     *
     * @param entries the list of [PropertyUpsertEntry] to persist.
     * @return [Either.Right] with [Unit] on success,
     * or [Either.Left] with [UpsertPropertyBatchFailure] if validation fails or a persistence error occurs.
     */
    fun execute(entries: List<PropertyUpsertEntry>): Either<UpsertPropertyBatchFailure, Unit>

}
