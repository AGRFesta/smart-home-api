package org.agrfesta.sh.api.core.application.ports.inbounds

import arrow.core.Either
import org.agrfesta.sh.api.core.domain.areas.AreaDto
import org.agrfesta.sh.api.core.domain.failures.AreaUpdateFailure
import java.util.UUID

interface UpdateAreaUseCase {

    /**
     * Updates an existing area identified by [areaId] with the given [name] and [isIndoor] flag.
     *
     * @param areaId the unique identifier of the area to update.
     * @param name the new display name for the area.
     * @param isIndoor whether the area is indoors.
     * @return [Either.Right] containing the updated [AreaDto] on success,
     *         or [Either.Left] with an [AreaUpdateFailure] if the area does not exist,
     *         a name conflict occurs, or a persistence error occurs.
     */
    fun execute(areaId: UUID, name: String, isIndoor: Boolean): Either<AreaUpdateFailure, AreaDto>
}
