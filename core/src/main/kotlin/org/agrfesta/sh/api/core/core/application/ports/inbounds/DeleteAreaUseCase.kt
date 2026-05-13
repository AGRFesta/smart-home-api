package org.agrfesta.sh.api.core.application.ports.inbounds

import arrow.core.Either
import org.agrfesta.sh.api.core.domain.failures.AreaDeletionFailure
import java.util.UUID

interface DeleteAreaUseCase {

    /**
     * Deletes an area by its unique identifier.
     *
     * @param areaId the unique identifier of the area to delete.
     * @return [Either.Right] with [Unit] on success,
     *         or [Either.Left] with an [AreaDeletionFailure] if the area does not exist or a persistence error occurs.
     */
    fun execute(areaId: UUID): Either<AreaDeletionFailure, Unit>

}
