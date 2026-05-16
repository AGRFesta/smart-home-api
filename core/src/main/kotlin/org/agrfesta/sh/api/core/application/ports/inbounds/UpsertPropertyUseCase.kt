package org.agrfesta.sh.api.core.application.ports.inbounds

import arrow.core.Either
import org.agrfesta.sh.api.core.domain.failures.UpsertPropertyFailure

interface UpsertPropertyUseCase {

    /**
     * Inserts or updates a single property entry identified by [key].
     *
     * @param key the unique key for the property entry.
     * @param value the value to store.
     * @param ttl optional time-to-live in seconds; `null` means no expiry.
     * @return [Either.Right] with [Unit] on success,
     * or [Either.Left] with [UpsertPropertyFailure] if a persistence error occurs.
     */
    fun execute(key: String, value: String, ttl: Long? = null): Either<UpsertPropertyFailure, Unit>

}
