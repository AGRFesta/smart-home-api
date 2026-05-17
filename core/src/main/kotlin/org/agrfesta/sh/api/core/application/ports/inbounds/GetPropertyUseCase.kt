package org.agrfesta.sh.api.core.application.ports.inbounds

import arrow.core.Either
import org.agrfesta.sh.api.core.domain.commons.PropertyEntry
import org.agrfesta.sh.api.core.domain.failures.GetPropertyFailure

interface GetPropertyUseCase {

    /**
     * Retrieves a property entry by [key], failing if it does not exist.
     *
     * @param key the unique key to retrieve.
     * @return [Either.Right] with the [PropertyEntry] if found,
     * or [Either.Left] with [GetPropertyFailure] if the entry does not exist or a database error occurs.
     */
    fun execute(key: String): Either<GetPropertyFailure, PropertyEntry>
}
