package org.agrfesta.sh.api.core.application.ports.inbounds

import arrow.core.Either
import org.agrfesta.sh.api.core.application.readmodels.areas.AreaView
import org.agrfesta.sh.api.core.domain.failures.GetAreasFailure

interface GetAreasUseCase {

    /**
     * Retrieves all persisted areas.
     *
     * @return [Either.Right] containing a collection of all [AreaView] instances,
     *         or [Either.Left] with a [GetAreasFailure] if a database error occurs.
     */
    fun execute(): Either<GetAreasFailure, Collection<AreaView>>
}
