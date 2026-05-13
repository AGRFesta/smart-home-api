package org.agrfesta.sh.api.core.application.ports.inbounds

import arrow.core.Either
import org.agrfesta.sh.api.core.domain.areas.AreaDto
import org.agrfesta.sh.api.core.domain.failures.PersistenceFailure

interface GetAreasUseCase {

    /**
     * Retrieves all persisted areas.
     *
     * @return [Either.Right] containing a collection of all [AreaDto] instances,
     *         or [Either.Left] with a [PersistenceFailure] if a database error occurs.
     */
    fun execute(): Either<PersistenceFailure, Collection<AreaDto>>

}
