package org.agrfesta.sh.api.core.application.ports.outbounds.areas

import arrow.core.Either
import org.agrfesta.sh.api.core.domain.areas.Area
import org.agrfesta.sh.api.core.domain.failures.AreaCreationFailure
import org.agrfesta.sh.api.core.domain.failures.AreaDeletionFailure
import org.agrfesta.sh.api.core.domain.failures.AreaFetchFailure
import org.agrfesta.sh.api.core.domain.failures.AreaRepositoryError
import org.agrfesta.sh.api.core.domain.failures.AreaUpdateFailure
import org.agrfesta.sh.api.core.domain.failures.GetAreasFailure
import java.util.UUID

/**
 * Outbound Port for [Area] persistence operations.
 *
 * All operations return an [Either] type: [Either.Right] on success, [Either.Left] on failure.
 * Failures are typed and represent either domain-level errors (e.g. not found, name conflict)
 * or infrastructure-level errors (e.g. database access failure).
 */
interface AreasRepository {

    /**
     * Retrieves an [Area] by its unique identifier.
     *
     * @param areaId the unique identifier of the area to retrieve.
     * @return [Either.Right] with the [Area] if found,
     * or [Either.Left] with [AreaFetchFailure] if the area does not exist or a persistence error occurs.
     */
    fun getAreaById(areaId: UUID): Either<AreaFetchFailure, Area>

    /**
     * Looks up an [Area] by name without failing if it does not exist.
     *
     * @param name the name of the area to search for.
     * @return [Either.Right] with the [Area] if found, or `null` if no area with that name exists,
     * or [Either.Left] with [AreaRepositoryError] if a database error occurs.
     */
    fun findAreaByName(name: String): Either<AreaRepositoryError, Area?>

    /**
     * Persists a new [Area].
     *
     * @param area the area to save.
     * @return [Either.Right] with [Unit] on success,
     * or [Either.Left] with [AreaCreationFailure] if the area could not be saved
     * (e.g. an area with the same name already exists, or a persistence error occurs).
     */
    fun save(area: Area): Either<AreaCreationFailure, Unit>

    /**
     * Retrieves all persisted areas.
     *
     * @return [Either.Right] with a collection of all [Area] instances,
     * or [Either.Left] with [GetAreasFailure] if a database error occurs.
     */
    fun getAll(): Either<GetAreasFailure, Collection<Area>>

    /**
     * Updates an existing [Area].
     *
     * @param area the area with updated values. The [Area.uuid] is used to identify the record to update.
     * @return [Either.Right] with the updated [Area] on success,
     *         or [Either.Left] with an [AreaUpdateFailure] if the area does not exist,
     *         a name conflict occurs, or a persistence error occurs.
     */
    fun update(area: Area): Either<AreaUpdateFailure, Area>

    /**
     * Deletes an [Area] by its unique identifier.
     *
     * @param areaId the unique identifier of the area to delete.
     * @return [Either.Right] with [Unit] on success,
     * or [Either.Left] with [AreaDeletionFailure] if the area does not exist or a persistence error occurs.
     */
    fun deleteAreaById(areaId: UUID): Either<AreaDeletionFailure, Unit>
}
