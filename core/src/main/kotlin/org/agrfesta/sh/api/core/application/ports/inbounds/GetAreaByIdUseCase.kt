package org.agrfesta.sh.api.core.application.ports.inbounds

import arrow.core.Either
import org.agrfesta.sh.api.core.domain.areas.AreaDto
import org.agrfesta.sh.api.core.domain.failures.AreaFetchFailure
import java.util.UUID

interface GetAreaByIdUseCase {

    /**
     * Retrieves an area by its unique identifier.
     *
     * @param areaId the unique identifier of the area to retrieve.
     * @return [Either.Right] containing the [AreaDto] if found,
     *         or [Either.Left] with an [AreaFetchFailure] if the area does not exist or a persistence error occurs.
     */
    fun execute(areaId: UUID): Either<AreaFetchFailure, AreaDto>

}
