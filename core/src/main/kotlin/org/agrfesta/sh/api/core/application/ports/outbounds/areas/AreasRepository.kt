package org.agrfesta.sh.api.core.application.ports.outbounds.areas

import arrow.core.Either
import java.util.UUID
import org.agrfesta.sh.api.core.domain.areas.AreaDto
import org.agrfesta.sh.api.core.domain.failures.AreaCreationFailure
import org.agrfesta.sh.api.core.domain.failures.AreaDeletionFailure
import org.agrfesta.sh.api.core.domain.failures.AreaFetchFailure
import org.agrfesta.sh.api.core.domain.failures.AreaRepositoryError
import org.agrfesta.sh.api.core.domain.failures.AreaUpdateFailure
import org.agrfesta.sh.api.core.domain.failures.GetAreasFailure

/**
 * Outbound Port for [AreaDto] persistence operations.
 *
 * All operations return an [Either] type: [Either.Right] on success, [Either.Left] on failure.
 * Failures are typed and represent either domain-level errors (e.g. not found, name conflict)
 * or infrastructure-level errors (e.g. database access failure).
 */
interface AreasRepository {

    /**
     * Retrieves an [AreaDto] by its unique identifier.
     *
     * @param areaId the unique identifier of the area to retrieve.
     * @return [Either.Right] with the [AreaDto] if found,
     * or [Either.Left] with [.AreaFetchFailure] if the area does not exist or a persistence error occurs.
     */
    fun getAreaById(areaId: UUID): Either<AreaFetchFailure, AreaDto>

    /**
     * Looks up an [AreaDto] by name without failing if it does not exist.
     *
     * @param name the name of the area to search for.
     * @return [Either.Right] with the [AreaDto] if found, or `null` if no area with that name exists,
     * or [Either.Left] with [AreaRepositoryError] if a database error occurs.
     */
    fun findAreaByName(name: String): Either<AreaRepositoryError, AreaDto?>

    /**
     * Persists a new [AreaDto].
     *
     * @param area the area to save.
     * @return [Either.Right] with [Unit] on success,
     * or [Either.Left] with [AreaCreationFailure] if the area could not be saved
     * (e.g. an area with the same name already exists, or a persistence error occurs).
     */
    fun save(area: AreaDto): Either<AreaCreationFailure, Unit>

    /**
     * Retrieves all persisted areas.
     *
     * @return [Either.Right] with a collection of all [AreaDto] instances,
     * or [Either.Left] with [GetAreasFailure] if a database error occurs.
     */
    fun getAll(): Either<GetAreasFailure, Collection<AreaDto>>

    /**
     * Updates an existing [AreaDto].
     *
     * @param area the area with updated values. The [AreaDto.uuid] is used to identify the record to update.
     * @return [Either.Right] with the updated [AreaDto] on success,
     *         or [Either.Left] with an [AreaUpdateFailure] if the area does not exist,
     *         a name conflict occurs, or a persistence error occurs.
     */
    fun update(area: AreaDto): Either<AreaUpdateFailure, AreaDto>

    /**
     * Deletes an [AreaDto] by its unique identifier.
     *
     * @param areaId the unique identifier of the area to delete.
     * @return [Either.Right] with [Unit] on success,
     * or [Either.Left] with [AreaDeletionFailure] if the area does not exist or a persistence error occurs.
     */
    fun deleteAreaById(areaId: UUID): Either<AreaDeletionFailure, Unit>

}
